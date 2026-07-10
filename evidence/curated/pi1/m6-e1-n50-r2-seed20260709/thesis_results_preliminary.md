# Resultados preliminares del harness de tesis

Generado en UTC: `2026-07-10T03:59:04.452733984Z`

## Ejecucion

```bash
mvn org.codehaus.mojo:exec-maven-plugin:3.5.0:java -Dexec.mainClass=com.mycompany.pepitoapp.security.metrics.ThesisMetricsRunner -Dexec.args="--records=50 --runs=2 --tamper-index=10 --output=build/thesis-metrics-final-pi1 --seed=20260709 --argon-iterations=1 --argon-memory-kb=8192"
```

- Mecanismo evaluado: `M6_HASH_CHAIN_ED25519`
- Registros por corrida: `50`
- Corridas: `2`
- Indice alterado: `10`
- Directorio de salida: `build/thesis-metrics-final-pi1`

## Resumen preliminar

| Metrica | Valor |
| --- | ---: |
| Tasa de deteccion | 100.00% |
| P95 promedio de insercion | 17.061653 ms |
| Tiempo medio de verificacion | 264.944621 ms |
| Corridas alteradas detectadas | 2 / 2 |

## Escenarios ejecutados

| Escenario | Operacion | Ejecuciones | Detecciones | Duracion media (ms) | P95 promedio insercion (ms) | Bytes medios BD |
| --- | --- | ---: | ---: | ---: | ---: | ---: |
| `CONTROL_CLEAN` | `append_batch` | 2 | 0 | 0.000000 | 17.061653 | 32768.000000 |
| `CONTROL_CLEAN` | `verify_chain` | 2 | 0 | 271.366042 | 0.000000 | 32768.000000 |
| `E1_EDIT_SALE_JSON` | `verify_chain` | 2 | 2 | 258.523199 | 0.000000 | 32768.000000 |

## Limitaciones academicas

- Esto es evidencia preliminar para Proyecto de Tesis I.
- Solo evalua `M6_HASH_CHAIN_ED25519`.
- Solo cubre `E1_EDIT_SALE_JSON` como escenario controlado de alteracion.
- No reemplaza el benchmark completo requerido para Proyecto de Tesis II.
