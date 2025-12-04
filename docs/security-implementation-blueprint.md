# Blueprint de implementación segura para PepitoApp

## 1. Resumen del problema y objetivos (10–15 líneas)
1. PepitoApp es un POS de escritorio **offline-first** para una bodega en Lima que registra boletas localmente.
2. El riesgo principal es la **manipulación o borrado** de boletas y bitácoras locales para fraude o evasión.
3. Se necesita **integridad, no repudio y confidencialidad en reposo** sin depender de internet.
4. Las boletas se representan como JSON y deben quedar protegidas mediante **Ed25519** y una **hash-chain**.
5. La BD local debe migrar a **SQLCipher** con llaves derivadas usando **Argon2id** a partir de una passphrase de operador.
6. La clave privada Ed25519 se guarda en un **keystore local** con `key_id` para permitir rotación básica.
7. El sistema debe tolerar hardware modesto (i3/Ryzen3, 6GB RAM) y mantener latencias aceptables de caja.
8. Se busca **forward integrity**: cualquier tampering o rollback en la bitácora debe detectarse en el cierre de caja.
9. El modelo de amenazas incluye personal interno deshonesto, soporte técnico descuidado, robo físico y malware local.
10. Se medirán **TAND**, latencia p95/p99 y tiempo percibido con y sin controles para validar el impacto.
11. La demo pública solo expone la capa de seguridad; el POS completo y lógica de negocio permanecen privados.
12. El diseño debe mapear controles a **X.800** (integridad, autenticación de origen, no repudio) y a **NIST SP 800-53 AU-10**.
13. La arquitectura debe ser por capas, desacoplando dominio, servicios criptográficos y almacenamiento cifrado.
14. Se entrega pseudocódigo y fragmentos clave; no se expone toda la lógica de ventas ni reportes.

## 2. Arquitectura por capas (paquetes y clases principales)
- **Dominio (`app.security.domain`)**
  - `Receipt`: boleta en JSON canónico (id, totals, items, timestamps).
  - `LedgerEntry`: entrada de bitácora con `id`, `timestamp`, `saleId`, `saleJson`, `prevHash`, `currentHash`, `signature`, `keyId`, `checkpoint`.
  - `KeyMetadata`: `keyId`, `createdAt`, `status`, `publicKeyRef`.
  - `MetricsSnapshot`: latencias y contadores de verificación.
- **Criptografía (`app.security.crypto`)**
  - `CryptoService`: firma/verificación Ed25519 sobre hashes de boleta; exposición de `keyId` activo.
  - `KeyStoreManager`: generación, carga y rotación de llaves, backed por keystore local protegido.
  - `Hashing`: utilidades SHA-256 o BLAKE2b para la hash-chain.
- **Ledger (`app.security.ledger`)**
  - `LedgerService`: append de entradas, verificación de cadena y checkpoints, detección de rollback/gaps.
  - `LedgerVerifier`: recorrido batch y cálculo de integridad con métricas de fallos.
- **Almacenamiento (`app.security.storage`)**
  - `StorageService`: DAO minimal para ledger y claves, abstracto para SQLite/SQLCipher.
  - `SqlCipherDataSource`: configuración de conexión cifrada y derivación de llave con Argon2id.
- **Aplicación/Infra (`app.security.app`)**
  - `SecurityModule`: fachada para integrarse con eventos de POS (registrar venta, cierre de caja).
  - `MetricsCollector`: registra latencias y resultados de verificación.

## 3. Iteraciones de implementación

### Iteración 1: Modelo de datos y clases de dominio
**Diseño**
- `Receipt` contiene `id`, `issuedAt`, `total`, lista de `items`, `operatorId`; se serializa a JSON canónico ordenado.
- `LedgerEntry` persiste los hashes y la firma; `checkpoint` permite cortes diarios para verificaciones más rápidas.
- `KeyMetadata` guarda `keyId`, `createdAt`, `status` (ACTIVE/RETIRED) y referencia al public key.

**Esquema de tablas (SQLCipher/SQLite)**
```sql
CREATE TABLE ledger_entry (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    sale_id TEXT NOT NULL,
    sale_json TEXT NOT NULL,
    prev_hash BLOB NOT NULL,
    current_hash BLOB NOT NULL,
    signature BLOB NOT NULL,
    key_id TEXT NOT NULL,
    checkpoint INTEGER NOT NULL DEFAULT 0,
    created_at INTEGER NOT NULL,
    verified_at INTEGER,
    verification_status TEXT
);

CREATE TABLE key_metadata (
    key_id TEXT PRIMARY KEY,
    public_key BLOB NOT NULL,
    created_at INTEGER NOT NULL,
    status TEXT NOT NULL
);
```

**Ejemplo de clases (bosquejo Java 21)**
```java
package app.security.domain;

import java.time.Instant;
import java.util.List;

public record Receipt(
        String id,
        Instant issuedAt,
        String operatorId,
        List<ReceiptItem> items,
        long totalCents
) {}

public record LedgerEntry(
        long id,
        String saleId,
        String saleJson,
        byte[] prevHash,
        byte[] currentHash,
        byte[] signature,
        String keyId,
        boolean checkpoint,
        Instant createdAt
) {}
```

### Iteración 2: CryptoService con Ed25519
**Diseño**
- `CryptoService.sign(byte[] hash)` devuelve firma y `keyId` activo.
- `CryptoService.verify(byte[] hash, byte[] signature, String keyId)` usa la llave pública asociada.
- `KeyStoreManager` genera par Ed25519, persiste en keystore local (ej. `PKCS12`) protegido con passphrase de despliegue.

**Snippet de implementación (usando BouncyCastle)**
```java
package app.security.crypto;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;

public class CryptoService {
    private final KeyStoreManager keyStoreManager;

    public CryptoService(KeyStoreManager keyStoreManager) {
        this.keyStoreManager = keyStoreManager;
    }

    public SignatureResult sign(byte[] hash) throws Exception {
        var active = keyStoreManager.loadActiveKeyPair();
        Signature sig = Signature.getInstance("Ed25519");
        sig.initSign(active.privateKey());
        sig.update(hash);
        return new SignatureResult(sig.sign(), active.keyId());
    }

    public boolean verify(byte[] hash, byte[] signature, String keyId) throws Exception {
        var pub = keyStoreManager.loadPublicKey(keyId);
        Signature sig = Signature.getInstance("Ed25519");
        sig.initVerify(pub);
        sig.update(hash);
        return sig.verify(signature);
    }
}
```

**Dependencias Maven sugeridas**
```xml
<dependency>
  <groupId>org.bouncycastle</groupId>
  <artifactId>bcprov-jdk18on</artifactId>
  <version>1.78.1</version>
</dependency>
```

### Iteración 3: LedgerService con hash-chain y verificación
**Diseño**
- `append(Receipt receipt)`:
  1. Serializa a JSON canónico (stable ordering).
  2. Calcula `prevHash` (último `current_hash` o valor génesis 32B cero).
  3. `currentHash = H(prevHash || saleJson)` con SHA-256/BLAKE2b.
  4. Firma `currentHash` con `CryptoService` y persiste en `ledger_entry`.
- `verifyFromCheckpoint(Optional<Long> checkpointId)` recorre entradas, recalcula hashes y verifica firmas; reporta gaps/rollback.

**Snippet de servicio (bosquejo)**
```java
package app.security.ledger;

import app.security.crypto.CryptoService;
import app.security.domain.LedgerEntry;
import app.security.domain.Receipt;
import app.security.storage.StorageService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;

public class LedgerService {
    private static final byte[] GENESIS = new byte[32];
    private final StorageService storage;
    private final CryptoService crypto;

    public LedgerService(StorageService storage, CryptoService crypto) {
        this.storage = storage;
        this.crypto = crypto;
    }

    public LedgerEntry append(Receipt receipt) throws Exception {
        String json = CanonicalJson.serialize(receipt);
        byte[] prev = storage.fetchLastHash().orElse(GENESIS);
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        digest.update(prev);
        digest.update(json.getBytes(StandardCharsets.UTF_8));
        byte[] current = digest.digest();

        var signature = crypto.sign(current);
        var entry = new LedgerEntry(
                0,
                receipt.id(),
                json,
                prev,
                current,
                signature.signature(),
                signature.keyId(),
                false,
                Instant.now()
        );
        storage.insertLedgerEntry(entry);
        return entry;
    }
}
```

### Iteración 4: Integración con SQLCipher y Argon2id
**Diseño**
- `SqlCipherDataSource` deriva llave con Argon2id usando passphrase de operador; almacena `salt` y `keyId` en archivo seguro.
- Conexión JDBC: `jdbc:sqlite:file:pepitoapp.db?cipher=sqlcipher&key_hex=<hexkey>` (driver sqlcipher-jdbc).
- El keystore guarda la passphrase derivada y el par Ed25519.

**Dependencias Maven sugeridas**
```xml
<dependency>
  <groupId>org.xerial</groupId>
  <artifactId>sqlite-jdbc</artifactId>
  <version>3.46.0.0</version>
</dependency>
<dependency>
  <groupId>com.kosprov</groupId>
  <artifactId>sqlcipher-jdbc</artifactId>
  <version>4.6.1</version>
</dependency>
<dependency>
  <groupId>de.mkammerer</groupId>
  <artifactId>argon2-jvm</artifactId>
  <version>2.11</version>
</dependency>
```

**Derivación de llave (bosquejo)**
```java
package app.security.storage;

import de.mkammerer.argon2.Argon2Factory;
import javax.sql.DataSource;

public final class SqlCipherDataSource {
    private static final int MEMORY_KB = 64 * 1024; // ajustar si la PC es más limitada
    private static final int ITERATIONS = 3;
    private static final int PARALLELISM = 1;

    public DataSource build(String dbPath, char[] passphrase, byte[] salt) {
        var argon2 = Argon2Factory.create();
        byte[] key = argon2.hash(ITERATIONS, MEMORY_KB, PARALLELISM, passphrase, salt);
        // Configurar datasource con key_hex y parámetros SQLCipher
        // Nota: almacenar salt y keyId en archivo seguro junto al keystore
        return SqliteDataSourceFactory.fromEncrypted(dbPath, key);
    }
}
```

### Iteración 5: Utilidades de pruebas y métricas
**Diseño**
- `MetricsCollector` mide latencia de `append` y `verifyFromCheckpoint` (p50/p95/p99).
- Scripts de prueba simulan:
  - **Tampering**: modificar `sale_json` o borrar entradas → verificar detección.
  - **Rollback**: truncar tabla → verificar desalineación de `prev_hash`.
  - **Robo de equipo**: validar que la BD cifrada no se abre sin passphrase.
- `MetricsSnapshot` se guarda en tabla o CSV para análisis en la tesis.

**Ejemplo de verificación con métricas**
```java
long start = System.nanoTime();
var result = ledgerService.verifyFromCheckpoint(Optional.empty());
long durationMs = (System.nanoTime() - start) / 1_000_000;
metricsCollector.record("verify_full_ms", durationMs, result.errors());
```

## 4. Alineación con X.800 y NIST SP 800-53
- **Integridad y autenticación del origen**: hash-chain + Ed25519 sobre cada boleta; mapea a servicios de integridad y autenticación.
- **No repudio (AU-10)**: firmas por boleta con `keyId`, bitácora inmutable, checkpoints fechados.
- **Confidencialidad en reposo**: SQLCipher con llaves derivadas Argon2id; protege datos ante robo físico.
- **Auditoría**: bitácora verificable y métricas de verificación almacenadas.

## 5. Cómo usar este blueprint en la demo
- Conectar `SecurityModule.appendSale()` al evento "registrar venta" del POS.
- Exponer un comando "cierre de caja seguro" que ejecuta `verifyFromCheckpoint` y muestra métricas.
- Mantener el **repo privado** del producto y compartir solo esta demo recortada y documentación.
- Etiquetar commits clave y conservar historial para evidenciar autoría.
