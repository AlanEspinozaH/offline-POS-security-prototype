# Resultados explicados del harness de tesis

## Metadatos de ejecución

- generatedAtUtc: `2026-07-10T03:59:04.452733984Z`
- mechanism: `M6_HASH_CHAIN_ED25519`
- records per run: `50`
- runs: `2`
- tamper index: `10`
- seed: `20260709`
- academic scope: `PRELIMINARY_PROJECT_TESIS_I`

## Resumen de métricas principales

| Métrica | Valor | Unidad | Significado técnico | Uso en Capítulo V | Límite de interpretación |
| --- | ---: | --- | --- | --- | --- |
| `tamperDetectionRate` | 100.00% | proporción | Proporción de corridas alteradas detectadas por la verificación. | Evidencia preliminar de detectabilidad para el escenario controlado. | No representa todos los ataques ni todos los mecanismos. |
| `detectedTamperRuns / tamperRuns` | 2 / 2 | corridas | Conteo de alteraciones detectadas frente al total de corridas alteradas. | Permite explicar el numerador y denominador de la tasa de detección. | Depende del número reducido de corridas configuradas. |
| `meanAppendP95Ms` | 17.061653 | ms | Promedio del percentil 95 de inserción protegida. | Aproxima el costo preliminar de escritura con integridad local. | No reemplaza una prueba de rendimiento con carga real. |
| `meanVerificationMs` | 264.944621 | ms | Tiempo medio de verificación de la cadena en los escenarios ejecutados. | Ayuda a discutir el costo de validación local. | Solo aplica al tamaño de datos sintéticos usado. |
| `recordsPerRun` | 50 | registros | Cantidad de ventas sintéticas generadas en cada corrida. | Define la escala del ensayo preliminar. | No equivale a volumen productivo. |
| `runs` | 2 | corridas | Número de repeticiones configuradas para el ensayo. | Sustenta la repetición mínima del smoke test. | No es el número final de repeticiones del benchmark comparativo. |

## Escenarios ejecutados

| scenario | operation | executions | detections | mean duration ms | mean append p95 ms | mean database bytes |
| --- | --- | ---: | ---: | ---: | ---: | ---: |
| `CONTROL_CLEAN` | `append_batch` | 2 | 0 | 0.000000 | 17.061653 | 32768.000000 |
| `CONTROL_CLEAN` | `verify_chain` | 2 | 0 | 271.366042 | 0.000000 | 32768.000000 |
| `E1_EDIT_SALE_JSON` | `verify_chain` | 2 | 2 | 258.523199 | 0.000000 | 32768.000000 |

## Interpretación académica breve

El resultado actual valida instrumentación automatizada preliminar para Proyecto de Tesis I. También sustenta que el harness puede generar evidencia local reproducible con parámetros explícitos. La ejecución actual solo evalúa `M6_HASH_CHAIN_ED25519` y solo cubre `E1_EDIT_SALE_JSON`. Por tanto, no debe presentarse como el benchmark comparativo final de Proyecto de Tesis II.

## Evidencia generada por esta corrida

- `thesis_metrics.csv`
- `thesis_metrics_summary.json`
- `thesis_results_preliminary.md`
- `thesis_experiment_manifest.json`
- `thesis_results_explained.md`
