# Módulo de ciberseguridad para PepitoApp

Este repositorio corresponde a una **demo de investigación** donde se implementan mecanismos de ciberseguridad sobre un sistema POS de escritorio llamado **PepitoApp**, usado en una bodega que opera en modo **offline-first**.

La intención de este repositorio es **mostrar y sustentar la capa de seguridad** (bitácora encadenada, firmas digitales y cifrado en reposo) pues es la parte central o medular del proyecto.

## 1. Contexto del proyecto

- Sistema POS de escritorio para bodega en Lima (San Juan de Miraflores).
- Operación **offline-first** sobre una PC de recursos modestos (Windows 11, Inter Core I3 7ma gen/ryzen 3. 6GB RAM, 250GB SSD, Graficos integrados, pantalla estandar).
- Lenguaje objetivo: **Java JDK 21 o superior**.
- Interfaz gráfica: **JavaFX 23**.
- Base de datos local: **SQLite**, con migración planeada a **SQLCipher** para cifrado en reposo.
- Stack de desarrollo: **NetBeans** + **Maven** (`pom.xml`).

Este módulo se centra en **integridad, no repudio y cifrado en reposo** para las boletas (comprobantes de venta) generadas por el POS.

## 2. Alcance de este repositorio

En este repo se busca **demostrar de forma robusta la parte de ciberseguridad**, mostrando un demo del código de un POS (PepitoApp).

Incluye :

- Bitácora encadenada criptográficamente (*hash-chain*).
- Firmas digitales **Ed25519** por boleta (representada como JSON).
- Cifrado en reposo de la base de datos local con **SQLCipher**, con llaves derivadas mediante **Argon2id**.
- Gestión básica de claves:
  - Almacenamiento de la clave privada en keystore local protegido.
  - Identificación de la clave mediante `key_id`.
  - Rotación básica de claves.
- Métricas de desempeño:
  - Latencia y tiempo de caja con y sin controles criptográficos.

Quedan **fuera de este repositorio**:

- Flujo completo de ventas e inventario de PepitoApp.
- Reportes, UI completa, lógica de negocio detallada.

## 3. Arquitectura y pseudocódigo

1. **Arquitectura y diseño**
   - Diagrama de módulos:
     - `PosCore` (ventas, boletas).
     - `LedgerService` (bitácora encadenada).
     - `CryptoService` (Ed25519, Argon2id).
     - `StorageService` (SQLite/SQLCipher).
     - `KeyStoreManager` (keystore local, `key_id`, rotación).
   - Diagramas de secuencia:
     - Registrar venta → generar boleta JSON → hash(prev + actual) → firmar → guardar en bitácora.
     - Cierre de caja → recorrer bitácora → verificar firmas → detectar *tampering*.

2. **Pseudocódigo en vez de código completo**

   ```pseudo
   function appendEntry(sale_json):
       prev_hash = ledger.last_hash()
       current_hash = H(prev_hash || sale_json)
       signature = Ed25519.sign(private_key, current_hash)
       store_ledger_entry(sale_json, current_hash, prev_hash, signature, key_id, timestamp)
   ```

3. **Resultados experimentales mínimos**

   - Comparar baseline vs. bitácora firmada + cifrado en reposo.
   - Medir latencia p95/p99 para registrar venta y cierre de caja.
   - Registrar tasa de tampering no detectado (TAND) en escenarios controlados.

## 4. Estrategia para demo y protección de PepitoApp

La guía práctica para dividir el proyecto en producto privado y demo pública, qué mostrar en sustentación, cómo responder a solicitudes de código y cómo dejar evidencia de autoría se encuentra en `docs/security-demo-strategy.md`.

## 5. Blueprint de implementación de la capa de seguridad

Un desglose por capas, iteraciones y fragmentos de código de ejemplo para la bitácora firmada, gestión de claves y SQLCipher con Argon2id está documentado en `docs/security-implementation-blueprint.md`.
