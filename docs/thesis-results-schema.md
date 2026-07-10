# Esquema de resultados del harness de tesis

Este documento explica como interpretar los artefactos generados por `ThesisMetricsRunner` para Proyecto de Tesis I. Su objetivo es que las salidas del harness sean trazables, reproducibles y utiles como evidencia preliminar para el Capitulo V, sin presentarlas como benchmark final.

## Proposito del harness

El harness ejecuta un experimento local y controlado sobre una bitacora SQLite aislada. Genera ventas sinteticas, las registra con el mecanismo `M6_HASH_CHAIN_ED25519`, verifica la cadena sin alteraciones, aplica una modificacion controlada sobre `sale_json` y vuelve a verificar si la alteracion es detectada.

La finalidad academica actual es validar que la instrumentacion puede producir archivos legibles y repetibles para analizar integridad de transacciones locales. No busca demostrar que el prototipo sea un POS productivo ni cerrar la evaluacion completa de todos los mecanismos definidos para Proyecto de Tesis II.

## Alcance experimental actual

- Mecanismo evaluado: `M6_HASH_CHAIN_ED25519`.
- Escenario limpio: `CONTROL_CLEAN`.
- Escenario de alteracion controlada: `E1_EDIT_SALE_JSON`.
- Operaciones medidas: insercion por lote (`append_batch`) y verificacion de cadena (`verify_chain`).
- Datos de entrada: ventas sinteticas generadas por el runner, no ventas reales del negocio.
- Salida esperada: archivos locales bajo el directorio indicado con `--output`, normalmente dentro de `build/`.

Este alcance permite evidenciar que una edicion local del JSON de venta rompe la cadena firmada y queda registrada como fallo de verificacion. Todavia no cubre eliminacion, insercion, truncamiento, rollback, multiples tamanos de dataset, comparacion entre mecanismos ni analisis estadistico final.

## `thesis_metrics.csv`

`thesis_metrics.csv` es la salida de detalle. Cada fila representa una medicion de una corrida y una operacion especifica. Es el archivo mas util para revisar datos crudos, construir tablas, recalcular agregados y justificar como se obtuvieron los resultados resumidos.

| Campo | Significado | Uso academico |
| --- | --- | --- |
| `timestamp_utc` | Instante UTC en que se genero la fila. | Trazabilidad temporal de la medicion. |
| `run` | Numero de corrida repetida, comenzando en 1. | Permite separar repeticiones y calcular variabilidad. |
| `mechanism` | Mecanismo de integridad evaluado. | Identifica la tecnica bajo prueba; actualmente `M6_HASH_CHAIN_ED25519`. |
| `scenario` | Escenario experimental ejecutado. | Distingue control limpio y alteracion controlada. |
| `operation` | Operacion medida. | Separa insercion protegida y verificacion de cadena. |
| `records` | Numero de registros sinteticos por corrida. | Tamano del experimento para esa ejecucion. |
| `tamper_index` | Indice del registro alterado; `-1` cuando no aplica. | Ubica el punto de alteracion en escenarios manipulados. |
| `valid` | Resultado booleano de la verificacion de cadena. | En control limpio deberia ser `true`; tras alteracion detectada deberia ser `false`. |
| `detected` | Indica si el escenario alterado fue detectado. | Base para calcular tasa de deteccion en escenarios tipo `E*`. |
| `duration_ms` | Duracion de la operacion medida en milisegundos. | En `verify_chain`, aproxima el costo de verificacion. |
| `mean_append_ms` | Tiempo medio de insercion por registro durante el lote. | Evidencia preliminar del costo de escritura protegida. |
| `p95_append_ms` | Percentil 95 de insercion por registro. | Ayuda a observar latencias altas sin depender solo del promedio. |
| `p99_append_ms` | Percentil 99 de insercion por registro. | Ayuda a observar casos extremos en la insercion. |
| `db_bytes` | Tamano fisico aproximado de la base local usada por la corrida. | Permite estimar crecimiento de almacenamiento bajo el mecanismo. |
| `error_count` | Cantidad de errores reportados por la verificacion. | Resume la evidencia de fallo cuando la cadena no valida. |
| `first_error` | Primer error textual reportado por la verificacion. | Sirve para documentar el tipo inicial de inconsistencia detectada. |

## `thesis_metrics_summary.json`

`thesis_metrics_summary.json` es un resumen agregado para lectura rapida. No reemplaza al CSV porque pierde detalle por corrida, pero facilita reportar resultados preliminares de forma compacta.

| Campo | Significado | Uso academico |
| --- | --- | --- |
| `generatedAtUtc` | Instante UTC de generacion del resumen. | Trazabilidad del conjunto de resultados. |
| `mechanism` | Mecanismo resumido. | Confirma que el agregado corresponde al mecanismo actual. |
| `recordsPerRun` | Registros sinteticos usados en cada corrida. | Describe el tamano del ensayo. |
| `runs` | Numero total de corridas configuradas. | Describe la repeticion experimental. |
| `tamperRuns` | Cantidad de filas correspondientes a escenarios alterados. | Denominador de la tasa de deteccion. |
| `detectedTamperRuns` | Cantidad de escenarios alterados detectados. | Numerador de la tasa de deteccion. |
| `tamperDetectionRate` | Proporcion de alteraciones detectadas. | Indicador preliminar de detectabilidad para el escenario soportado. |
| `meanAppendP95Ms` | Promedio del p95 de insercion entre corridas. | Resumen preliminar de latencia alta de escritura protegida. |
| `meanVerificationMs` | Tiempo medio de verificacion entre filas `verify_chain`. | Resumen preliminar del costo de verificar la cadena. |

Si se genera `thesis_experiment_manifest.json`, ese archivo cumple una funcion complementaria de reproducibilidad. Debe contener solo metadatos seguros: fecha UTC, mecanismo, parametros de ejecucion, directorio de salida indicado por el usuario sin expandirlo a ruta absoluta, version de Java, sistema operativo, arquitectura y `academicScope = "PRELIMINARY_PROJECT_TESIS_I"`.

## `thesis_results_preliminary.md`

`thesis_results_preliminary.md` es un reporte Markdown generado automaticamente para lectura humana. Resume el comando reconstruido, los parametros principales, la tasa de deteccion, las latencias agregadas y las limitaciones academicas del ensayo.

Este archivo puede usarse como insumo narrativo para el Capitulo V porque deja claro que los resultados corresponden a una ejecucion concreta. No debe tratarse como evidencia final por si solo: las tablas academicas deben poder rastrearse al CSV y al JSON generados en la misma ejecucion.

## Uso recomendado en el Capitulo V

Estos artefactos pueden utilizarse para:

- describir el instrumento de medicion implementado;
- documentar parametros de ejecucion reproducibles;
- mostrar evidencia preliminar de detectabilidad para `E1_EDIT_SALE_JSON`;
- justificar que el prototipo ya produce datos crudos y agregados;
- preparar el formato de tablas que luego se ampliara en Proyecto de Tesis II.

Las afirmaciones deben redactarse como evidencia preliminar de instrumentacion. Por ejemplo, es razonable afirmar que el harness actual permite generar mediciones locales reproducibles para un mecanismo y un escenario controlado. No es razonable afirmar que ya se compararon todos los mecanismos ni que el sistema completo esta validado experimentalmente.

## Lo que no debe afirmarse

Con estas salidas no se debe afirmar:

- que el POS esta listo para produccion;
- que todos los mecanismos de integridad fueron evaluados;
- que existe comparacion final contra SQLite plano, SQLCipher, HMAC u otros mecanismos aun no ejecutados;
- que la tasa de deteccion aplica a todos los ataques posibles;
- que el costo de rendimiento observado representa cargas reales de negocio;
- que los resultados sustituyen el benchmark completo de Proyecto de Tesis II;
- que los archivos generados contienen secretos o deben versionarse como evidencia permanente.

## Diferencia con el benchmark final

En Proyecto de Tesis I, estas salidas prueban que la instrumentacion existe, compila, ejecuta y produce evidencia preliminar interpretable. El foco es la viabilidad tecnica del metodo de medicion y la preparacion del repositorio para experimentacion reproducible.

En Proyecto de Tesis II, el benchmark final deberia ejecutar el protocolo completo: mecanismos comparables, escenarios de alteracion adicionales, tamanos de dataset definidos, repeticiones suficientes, control del entorno, analisis estadistico y redaccion de conclusiones con alcance delimitado.

## Reproducibilidad y versionado seguro

Los archivos generados bajo `build/` son artefactos locales de ejecucion. Normalmente no deben ser versionados; solo deberian conservarse temporalmente para inspeccion, curacion manual o inclusion controlada de evidencia derivada.

Tampoco deben versionarse bases de datos generadas, archivos locales de propiedades, almacenes de claves, sales criptograficas, material cifrado de claves privadas ni otros artefactos locales de ejecucion. El repositorio debe contener codigo, documentacion y configuracion reproducible, no secretos ni datos generados por una corrida local.

## Comando smoke-test recomendado

```bash
mvn org.codehaus.mojo:exec-maven-plugin:3.5.0:java \
  -Dexec.mainClass=com.mycompany.pepitoapp.security.metrics.ThesisMetricsRunner \
  -Dexec.args="--records=50 --runs=2 --tamper-index=10 --output=build/thesis-metrics-smoke --seed=20260709 --argon-iterations=1 --argon-memory-kb=8192"
```
