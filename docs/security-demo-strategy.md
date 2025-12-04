# Estrategia para demo de ciberseguridad y protección de PepitoApp

Este documento resume las prácticas para mostrar la capa de seguridad sin exponer el producto completo.

## 1. Separar Producto vs. Demo de investigación

- **PepitoApp real (privado)**: flujo completo de POS, reportes, UX completa, lógica de negocio, integración con inventario/bodega. Mantener en repo privado.
- **Módulo / demo de investigación (público o semi-público)**: solo lo necesario para demostrar:
  - Bitácora encadenada (hash-chain).
  - Firmas Ed25519 por boleta JSON.
  - Cifrado en reposo (SQLCipher + Argon2id).
  - Gestión de claves (keystore local, `key_id`, rotación básica).
  - Métricas de desempeño (latencia, tiempo de caja).
- Lo que se muestra: diseño criptográfico, modelo de amenazas, protocolo experimental y demo funcional limitada.
- Lo que no se muestra: ADN completo del POS (casos de uso completos, reportes, inventario, UI completa).

## 2. Qué enseñar en la sustentación

- **Arquitectura y diseño**: diagramas de módulos (`PosCore`, `LedgerService`, `CryptoService`, `StorageService`, `KeyStoreManager`) y diagramas de secuencia (registrar venta, cierre de caja con verificación).
- **Pseudocódigo** en vez de código completo para operaciones sensibles (ej. `appendEntry`).
- **Fragmentos específicos**: creación de tabla de bitácora, verificación de cadena/firmas, esquema de cifrado con SQLCipher.
- **Resultados experimentales**: comparaciones baseline vs. solución con bitácora + firmas + cifrado; TAND; latencia p95/p99.

## 3. Cómo manejar solicitudes de código

- **Repo privado + pantalla compartida**: se muestra el código sin compartir ZIP ni acceso de lectura.
- **Entrega solo de demo recortada**: preparar repo `pepitoapp-security-demo` con módulos mínimos para encadenar, firmar y verificar boletas.
- **Binario en vez de fuente**: entregar `.jar`/installer (opcionalmente ofuscado) para probar sin revelar código.
- **Licencia y aviso**: agregar nota de autoría y uso restringido en archivos de la demo pública.

## 4. Evidencia de autoría

- Mantener **repo privado con historial de commits** y etiquetas importantes (ej. `v0.1-tesis-idea`) firmadas con GPG.
- Documentación fechada (PDF de problema/objetivos) y envíos por correo a asesor/autor para registro temporal.
- Incluir nombre en esquemas/diagramas: "Prototipo de bitácora encadenada Ed25519 para POS offline – [autor], 2025".

## 5. Mensaje para modelo de código (prompt base)

Usa este prompt como primer mensaje al asistente de programación para guiar la implementación paso a paso:

```
Actúa como un Senior Security Engineer especializado en Java, criptografía aplicada y DevSecOps, y también como mentor de tesis. Eres experto en:

- Criptografía moderna: Ed25519, Argon2id, hash-chains, forward integrity.
- Bases de datos embebidas y cifrado en reposo: SQLite + SQLCipher.
- Diseño de sistemas POS offline-first.
- Modelado de amenazas y controles alineados con X.800 y NIST SP 800-53.
- Buenas prácticas de arquitectura en Java (DDD light, capas) y JavaFX.

CONTEXTO GENERAL DEL PROYECTO

Estoy desarrollando un POS de escritorio llamado “PepitoApp” para una bodega (minimarket) que opera offline, usando:

- Lenguaje: Java 21 o superior.
- GUI: JavaFX (versión 21–23).
- BD local: SQLite (quiero migrar a SQLCipher para cifrado en reposo).
- Sistema: PC modesta con Windows 11, operación offline-first.

Voy a incorporar un módulo de CIBERSEGURIDAD con el siguiente enfoque de investigación:

Tema / problema:

- No repudio e integridad en POS local: firma Ed25519 de boletas JSON y bitácora encadenada.
- Bitácora firmada con Ed25519 y gestionada localmente para garantizar integridad y no repudio de boletas en un POS offline, en una bodega de San Juan de Miraflores (noviembre–diciembre 2025).

Objetivos clave (resumen):

1. Implementar una bitácora de transacciones encadenadas criptográficamente (hash-chain) con firmas Ed25519 por boleta JSON, incluyendo key_id y checkpoints diarios.
2. Incorporar cifrado en reposo de la BD local del POS usando SQLCipher, con llaves derivadas mediante Argon2id y protegidas en un keystore local.
3. Diseñar un modelo de amenazas para el POS (actores internos deshonestos, soporte técnico descuidado, robo físico del equipo, malware local, rollback de bitácoras).
4. Definir un protocolo experimental que compare un baseline sin controles criptográficos vs la solución propuesta, midiendo:
   - Tasa de tampering no detectado (TAND).
   - Evidencia disponible para no repudio.
   - Impacto en desempeño (latencia de registro de venta, cierre de caja, tiempo percibido por el usuario).

REQUERIMIENTOS TÉCNICOS ESPECÍFICOS

Quiero que me ayudes a DISEÑAR y luego IMPLEMENTAR paso a paso:

1. MODELO DE DATOS
   - Esquema mínimo de tablas para:
     - Ventas / boletas (almacenadas como JSON o campos estructurados).
     - Bitácora (ledger) encadenada:
       - id, timestamp, sale_id, sale_json,
       - prev_hash, current_hash,
       - signature (Ed25519),
       - key_id,
       - checkpoint_flag (sí/no),
       - metadata de verificación.
   - Tablas adicionales si hacen falta para gestión de claves o métricas.

2. MÓDULO DE CRIPTOGRAFÍA
   - API limpia en Java para:
     - Generar y almacenar un par de llaves Ed25519 (privada en keystore local protegido).
     - Firmar el hash de cada boleta JSON.
     - Verificar firmas y cadena de integridad (forward integrity).
   - Uso de Argon2id para derivar llaves de cifrado de BD a partir de una passphrase de operador, con parámetros razonables para una PC modesta (por ejemplo: opslimit, memlimit).

3. CIFRADO EN REPOSO (SQLCIPHER)
   - Cómo modificar la conexión a SQLite para usar SQLCipher desde Java.
   - Cómo almacenar y recuperar la llave de cifrado:
     - derivación con Argon2id,
     - almacenamiento seguro (keystore local),
     - uso de key_id para futuras rotaciones.

4. CAPA DE SERVICIO PARA LA BITÁCORA
   - Clase(s) Java que:
     - Reciban una venta (boleta) ya calculada por el POS.
     - Generen el JSON canónico de la boleta.
     - Calcular prev_hash y current_hash (hash-chain).
     - Firmar current_hash con Ed25519.
     - Insertar la entrada en la tabla de bitácora.
   - Operaciones de verificación:
     - Recalcular y verificar toda la cadena desde el génesis o desde un checkpoint diario.
     - Detectar tampering: faltan entradas, hashes rotos, firmas inválidas, rollback.

5. MODELO DE AMENAZAS Y CONTROLES
   - Ayúdame a listar activos, actores y amenazas relevantes para este POS offline local.
   - Mapear controles implementados (bitácora, firmas, cifrado, keystore, rotación de llaves) a:
     - Servicios de seguridad de X.800 (integridad, autenticación del origen, no repudio, auditoría).
     - Controles como AU-10 de NIST SP 800-53 (no repudio).

6. PROTOCOLO EXPERIMENTAL
   - Propuesta detallada (en forma de lista de pasos) para:
     - Configuración baseline sin controles criptográficos.
     - Configuración con bitácora + firmas + cifrado.
     - Escenarios de prueba con “ataques” simulados (tampering, borrado selectivo, rollback, robo de equipo).
     - Cómo medir:
       - TAND (tampering no detectado),
       - latencia p95/p99,
       - impacto en tiempo de caja.
   - Sugerencias de cómo registrar estos datos (logs, tablas, scripts) para usarlos en mi informe y tesis.

MODO DE TRABAJO QUE QUIERO DE TI

1. Primero, resume mi problema, objetivos y enfoque de seguridad en 10–15 líneas para asegurarte de haber entendido.
2. Luego, propón una ARQUITECTURA por capas para la solución (paquetes y clases principales) SIN código aún.
3. Después, ve modulando la implementación en iteraciones:
   - Iteración 1: modelo de datos y clases de dominio (Receipt, LedgerEntry, etc.).
   - Iteración 2: CryptoService con Ed25519 (firmar/verificar).
   - Iteración 3: LedgerService con hash-chain y verificación.
   - Iteración 4: integración con SQLCipher y Argon2id.
   - Iteración 5: utilidades de pruebas y medición de desempeño.
4. Para cada iteración:
   - Dame el diseño (clases, métodos, interfaces).
   - Luego un ejemplo de código Java bien estructurado.
   - Incluye comentarios explicando las decisiones de seguridad.
5. No reescribas todo el POS: asume que ya existe una lógica básica de ventas y boletas y que podemos conectar tus servicios a eventos como “registrar venta” y “cierre de caja”.

Cuando generes código:

- Usa Java 21, evita APIs obsoletas.
- Separa claramente dominio, servicios, e infraestructura (DB, criptografía).
- Sé explícito al indicar qué partes son bosquejo/prototipo y cuáles son más cercanas a producción.
- Indica dependencias externas (por ejemplo, librerías de Ed25519, Argon2id, SQLCipher para Java) y cómo agregarlas (ejemplo de snippet de Maven/Gradle).

Empecemos por tu paso 1: dame el resumen de mi problema y objetivos, y luego la arquitectura por capas.
```

## 6. Siguiente paso sugerido

Usa el prompt anterior con tu asistente de código para obtener el resumen inicial y la propuesta de arquitectura. Luego implementa iterativamente los módulos de seguridad siguiendo el alcance limitado descrito en este documento.
