# Harness de métricas para Tesis I/II

Este repositorio incluye un mecanismo mínimo de automatización para levantar métricas reproducibles sobre la bitácora local firmada. La intención no es reemplazar el experimento completo de Proyecto de Tesis II, sino dejar instalado un instrumento técnico que permita recolectar evidencia de forma incremental.

Para instalación segura, manejo de secretos, ejecución reproducible y checklist DevSecOps, revisar también `docs/INSTALLATION.md`.

## Objetivo

El runner `ThesisMetricsRunner` genera ventas sintéticas, las inserta en una bitácora hash-chain firmada con Ed25519, verifica la cadena limpia, aplica una alteración controlada sobre `sale_json` y vuelve a verificar. Al final exporta:

- `thesis_metrics.csv`: filas por corrida y operación.
- `thesis_metrics_summary.json`: resumen agregado para lectura rápida.

## Métricas exportadas

| Campo | Uso metodológico |
| --- | --- |
| `mean_append_ms` | tiempo medio de inserción protegida |
| `p95_append_ms` | latencia p95 de inserción protegida |
| `p99_append_ms` | latencia p99 de inserción protegida |
| `duration_ms` | tiempo de verificación de cadena |
| `detected` | detectabilidad de alteración controlada |
| `db_bytes` | tamaño físico aproximado de la base SQLite |
| `error_count` / `first_error` | evidencia de localización inicial del fallo |

## Ejecución rápida

Sin modificar el `pom.xml`, puede ejecutarse indicando explícitamente la clase principal:

```bash
mvn org.codehaus.mojo:exec-maven-plugin:3.5.0:java \
  -Dexec.mainClass=com.mycompany.pepitoapp.security.metrics.ThesisMetricsRunner \
  -Dexec.args="--records=200 --runs=5 --tamper-index=50"
```

Salida esperada:

```text
build/thesis-metrics/thesis_metrics.csv
build/thesis-metrics/thesis_metrics_summary.json
```

## Ejecución más cercana a Tesis II

```bash
mvn org.codehaus.mojo:exec-maven-plugin:3.5.0:java \
  -Dexec.mainClass=com.mycompany.pepitoapp.security.metrics.ThesisMetricsRunner \
  -Dexec.args="--records=1000 --runs=30 --tamper-index=500 --output=build/thesis-metrics-1000x30"
```

## Interpretación académica

Para Proyecto de Tesis I, esta automatización debe presentarse como instrumento de recolección de métricas y evidencia preliminar de viabilidad. No debe afirmarse que reemplaza el benchmark completo porque todavía falta comparar todos los mecanismos definidos en la metodología: SQLite plano, SQLCipher, hash por registro, HMAC por registro, hash-chain autenticado y hash-chain con firma digital.

En Proyecto de Tesis II, el mismo formato CSV puede extenderse con nuevas filas para los mecanismos M1 a M5 y escenarios adicionales: eliminación, inserción, reordenamiento, truncamiento y rollback con/sin anclaje externo.

## Decisiones técnicas

- Cada corrida crea un directorio temporal aislado bajo el directorio de salida.
- La base SQLite y el keystore se generan por corrida para evitar contaminación entre repeticiones.
- Los parámetros Argon2 del harness son reducidos por defecto para no dominar el tiempo de medición durante pruebas exploratorias.
- La passphrase usada por el harness es demostrativa; no debe usarse en producción.
