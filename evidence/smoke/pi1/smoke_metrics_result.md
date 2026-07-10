# Smoke test de métricas - Etapa 2A

Fecha UTC: 2026-07-10T01:47:46Z
Rama: stage1-codex-build-db-config

Comando ejecutado:

mvn org.codehaus.mojo:exec-maven-plugin:3.5.0:java \
  -Dexec.mainClass=com.mycompany.pepitoapp.security.metrics.ThesisMetricsRunner \
  -Dexec.args="--records=50 --runs=2 --tamper-index=10 --output=build/thesis-metrics-smoke"

Resultado:
- BUILD SUCCESS.
- Se generó thesis_metrics.csv.
- Se generó thesis_metrics_summary.json.
- Mecanismo evaluado: M6_HASH_CHAIN_ED25519.
- Registros por corrida: 50.
- Corridas: 2.
- Corridas con alteración: 2.
- Alteraciones detectadas: 2.
- Tasa de detección: 1.0.
- p95 promedio de inserción: 18.369339 ms.
- Tiempo medio de verificación: 275.088059 ms.

Interpretación:
Este resultado valida que el repositorio ya cuenta con un mecanismo automatizado mínimo para generar datos experimentales preliminares. No representa todavía el benchmark completo de la tesis, porque solo evalúa M6 y un escenario de alteración E1_EDIT_SALE_JSON.
