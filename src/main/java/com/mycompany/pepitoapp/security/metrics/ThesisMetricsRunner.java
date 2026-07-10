package com.mycompany.pepitoapp.security.metrics;

import com.mycompany.pepitoapp.security.crypto.Argon2KeyDeriver;
import com.mycompany.pepitoapp.security.crypto.CryptoService;
import com.mycompany.pepitoapp.security.crypto.KeyStoreManager;
import com.mycompany.pepitoapp.security.ledger.LedgerService;
import com.mycompany.pepitoapp.security.ledger.VerificationResult;
import com.mycompany.pepitoapp.security.storage.SecureDatabaseProvider;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Command-line harness for Thesis I/II metrics collection.
 *
 * <p>The runner creates isolated SQLite ledgers, inserts synthetic sales, verifies
 * the signed hash-chain, applies a controlled edit attack, and exports CSV/JSON
 * metrics suitable for the methodology chapter.</p>
 */
public final class ThesisMetricsRunner {

    private static final String MECHANISM = "M6_HASH_CHAIN_ED25519";
    private static final String KEY_ID = "thesis-metrics-ed25519";
    private static final String PASSPHRASE = "thesis-metrics-demo-passphrase";

    private ThesisMetricsRunner() {
    }

    public static void main(String[] args) throws Exception {
        Locale.setDefault(Locale.ROOT);
        Config config = Config.parse(args);
        Files.createDirectories(config.outputDir());

        List<MetricRow> rows = new ArrayList<>();
        for (int run = 1; run <= config.runs(); run++) {
            Path runDir = Files.createTempDirectory(config.outputDir(), "run-" + run + "-");
            ExperimentContext context = createContext(runDir, config);

            List<Long> appendDurationsNs = new ArrayList<>(config.records());
            for (int seq = 1; seq <= config.records(); seq++) {
                String saleJson = syntheticSaleJson(seq, config.seed());
                long start = System.nanoTime();
                boolean appended = context.ledger().appendSignedEntry(saleJson, KEY_ID, passphrase(), context.crypto());
                long elapsed = System.nanoTime() - start;
                if (!appended) {
                    throw new IllegalStateException("Could not append synthetic sale at seq=" + seq);
                }
                appendDurationsNs.add(elapsed);
            }

            long dbBytesAfterInsert = fileSize(context.databasePath());
            rows.add(MetricRow.appendBatch(
                    run,
                    config.records(),
                    dbBytesAfterInsert,
                    meanMs(appendDurationsNs),
                    percentileMs(appendDurationsNs, 0.95),
                    percentileMs(appendDurationsNs, 0.99)
            ));

            VerificationMeasurement clean = measureVerification(context, "CONTROL_CLEAN", config.records(), -1);
            rows.add(clean.toMetricRow(run, config.records(), dbBytesAfterInsert));

            int tamperIndex = Math.max(1, Math.min(config.tamperIndex(), config.records()));
            tamperSaleJson(context, tamperIndex);
            long dbBytesAfterTamper = fileSize(context.databasePath());
            VerificationMeasurement tampered = measureVerification(context, "E1_EDIT_SALE_JSON", config.records(), tamperIndex);
            rows.add(tampered.toMetricRow(run, config.records(), dbBytesAfterTamper));
        }

        Path csvPath = config.outputDir().resolve("thesis_metrics.csv");
        Path summaryPath = config.outputDir().resolve("thesis_metrics_summary.json");
        Path markdownPath = config.outputDir().resolve("thesis_results_preliminary.md");
        Path manifestPath = config.outputDir().resolve("thesis_experiment_manifest.json");
        SummaryMetrics summary = summarize(rows, config);
        writeCsv(csvPath, rows);
        writeSummaryJson(summaryPath, summary);
        writePreliminaryMarkdown(markdownPath, rows, config, summary);
        writeExperimentManifest(manifestPath, config, summary);

        System.out.println("Metrics written to: " + csvPath.toAbsolutePath());
        System.out.println("Summary written to: " + summaryPath.toAbsolutePath());
        System.out.println("Preliminary report written to: " + markdownPath.toAbsolutePath());
        System.out.println("Experiment manifest written to: " + manifestPath.toAbsolutePath());
    }

    private static ExperimentContext createContext(Path runDir, Config config) throws IOException {
        Argon2KeyDeriver fastDeriver = new Argon2KeyDeriver(
                config.argonIterations(),
                config.argonMemoryKb(),
                1,
                32
        );
        Path dbDir = runDir.resolve("db");
        Path keyDir = runDir.resolve("keystore");
        SecureDatabaseProvider provider = new SecureDatabaseProvider(fastDeriver, dbDir);
        KeyStoreManager keyStoreManager = new KeyStoreManager(fastDeriver, keyDir);
        CryptoService cryptoService = new CryptoService(keyStoreManager);
        LedgerService ledgerService = new LedgerService(provider);
        return new ExperimentContext(provider, cryptoService, ledgerService, dbDir.resolve("ledger.db"));
    }

    private static VerificationMeasurement measureVerification(
            ExperimentContext context,
            String scenario,
            int records,
            int tamperIndex
    ) {
        long start = System.nanoTime();
        VerificationResult result = context.ledger().verifyChain(passphrase(), context.crypto());
        long elapsed = System.nanoTime() - start;
        boolean expectedDetection = scenario.startsWith("E");
        boolean detected = expectedDetection && !result.valid();
        String firstError = result.errors().isEmpty() ? "" : result.errors().get(0);
        return new VerificationMeasurement(
                scenario,
                records,
                tamperIndex,
                result.valid(),
                detected,
                elapsed,
                result.errors().size(),
                firstError
        );
    }

    private static void tamperSaleJson(ExperimentContext context, int id) throws Exception {
        try (Connection connection = context.provider().getConnection(passphrase())) {
            String currentJson = fetchSaleJson(connection, id)
                    .orElseThrow(() -> new IllegalStateException("No ledger entry found at id=" + id));
            String tamperedJson = currentJson.replaceFirst("\"totalCents\":\\d+", "\"totalCents\":1");
            if (Objects.equals(currentJson, tamperedJson)) {
                tamperedJson = currentJson + " ";
            }
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE ledger SET sale_json = ? WHERE id = ?")) {
                statement.setString(1, tamperedJson);
                statement.setInt(2, id);
                statement.executeUpdate();
            }
        }
    }

    private static Optional<String> fetchSaleJson(Connection connection, int id) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT sale_json FROM ledger WHERE id = ?")) {
            statement.setInt(1, id);
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(rs.getString(1));
                }
            }
        }
        return Optional.empty();
    }

    private static String syntheticSaleJson(int seq, long seed) {
        long issuedAt = 1_700_000_000L + seed + seq;
        int quantity = 1 + (seq % 5);
        long unitPriceCents = 150L + ((seq * 37L + seed) % 2_000L);
        long totalCents = quantity * unitPriceCents;
        String itemId = "SKU-" + String.format(Locale.ROOT, "%05d", seq % 10_000);
        String operatorId = "operator-" + (seq % 3);
        return "{"
                + "\"seq\":" + seq + ","
                + "\"saleId\":\"SALE-" + String.format(Locale.ROOT, "%06d", seq) + "\","
                + "\"issuedAt\":" + issuedAt + ","
                + "\"operatorId\":\"" + operatorId + "\","
                + "\"items\":[{"
                + "\"itemId\":\"" + itemId + "\","
                + "\"quantity\":" + quantity + ","
                + "\"unitPriceCents\":" + unitPriceCents
                + "}],"
                + "\"totalCents\":" + totalCents
                + "}";
    }

    private static char[] passphrase() {
        return PASSPHRASE.toCharArray();
    }

    private static long fileSize(Path path) {
        try {
            return Files.exists(path) ? Files.size(path) : 0L;
        } catch (IOException ex) {
            return -1L;
        }
    }

    private static double meanMs(List<Long> durationsNs) {
        if (durationsNs.isEmpty()) {
            return 0.0;
        }
        long total = 0L;
        for (long duration : durationsNs) {
            total += duration;
        }
        return total / 1_000_000.0 / durationsNs.size();
    }

    private static double percentileMs(List<Long> durationsNs, double percentile) {
        if (durationsNs.isEmpty()) {
            return 0.0;
        }
        List<Long> sorted = new ArrayList<>(durationsNs);
        sorted.sort(Comparator.naturalOrder());
        int index = (int) Math.ceil(percentile * sorted.size()) - 1;
        index = Math.max(0, Math.min(index, sorted.size() - 1));
        return sorted.get(index) / 1_000_000.0;
    }

    private static void writeCsv(Path path, List<MetricRow> rows) throws IOException {
        StringBuilder builder = new StringBuilder();
        builder.append("timestamp_utc,run,mechanism,scenario,operation,records,tamper_index,")
                .append("valid,detected,duration_ms,mean_append_ms,p95_append_ms,p99_append_ms,")
                .append("db_bytes,error_count,first_error\n");
        for (MetricRow row : rows) {
            builder.append(row.toCsvLine()).append('\n');
        }
        Files.writeString(path, builder.toString(), StandardCharsets.UTF_8);
    }

    private static SummaryMetrics summarize(List<MetricRow> rows, Config config) {
        long tamperRuns = rows.stream().filter(row -> row.scenario().startsWith("E")).count();
        long detectedRuns = rows.stream().filter(row -> row.scenario().startsWith("E") && row.detected()).count();
        double detectionRate = tamperRuns == 0 ? 0.0 : detectedRuns / (double) tamperRuns;
        double appendP95Mean = rows.stream()
                .filter(row -> row.operation().equals("append_batch"))
                .mapToDouble(MetricRow::p95AppendMs)
                .average()
                .orElse(0.0);
        double verificationMsMean = rows.stream()
                .filter(row -> row.operation().equals("verify_chain"))
                .mapToDouble(MetricRow::durationMs)
                .average()
                .orElse(0.0);
        return new SummaryMetrics(
                Instant.now(),
                MECHANISM,
                config.records(),
                config.runs(),
                tamperRuns,
                detectedRuns,
                detectionRate,
                appendP95Mean,
                verificationMsMean
        );
    }

    private static void writeSummaryJson(Path path, SummaryMetrics summary) throws IOException {
        String json = "{\n"
                + "  \"generatedAtUtc\": \"" + summary.generatedAtUtc() + "\",\n"
                + "  \"mechanism\": \"" + summary.mechanism() + "\",\n"
                + "  \"recordsPerRun\": " + summary.recordsPerRun() + ",\n"
                + "  \"runs\": " + summary.runs() + ",\n"
                + "  \"tamperRuns\": " + summary.tamperRuns() + ",\n"
                + "  \"detectedTamperRuns\": " + summary.detectedTamperRuns() + ",\n"
                + "  \"tamperDetectionRate\": " + formatDouble(summary.tamperDetectionRate()) + ",\n"
                + "  \"meanAppendP95Ms\": " + formatDouble(summary.meanAppendP95Ms()) + ",\n"
                + "  \"meanVerificationMs\": " + formatDouble(summary.meanVerificationMs()) + "\n"
                + "}\n";
        Files.writeString(path, json, StandardCharsets.UTF_8);
    }

    private static void writeExperimentManifest(
            Path path,
            Config config,
            SummaryMetrics summary
    ) throws IOException {
        String json = "{\n"
                + "  \"generatedAtUtc\": " + jsonString(summary.generatedAtUtc().toString()) + ",\n"
                + "  \"mechanism\": " + jsonString(summary.mechanism()) + ",\n"
                + "  \"records\": " + config.records() + ",\n"
                + "  \"runs\": " + config.runs() + ",\n"
                + "  \"tamperIndex\": " + config.tamperIndex() + ",\n"
                + "  \"outputDirectory\": " + jsonString(config.outputDir().toString()) + ",\n"
                + "  \"seed\": " + config.seed() + ",\n"
                + "  \"argonIterations\": " + config.argonIterations() + ",\n"
                + "  \"argonMemoryKb\": " + config.argonMemoryKb() + ",\n"
                + "  \"javaVersion\": " + jsonString(System.getProperty("java.version", "")) + ",\n"
                + "  \"osName\": " + jsonString(System.getProperty("os.name", "")) + ",\n"
                + "  \"osArch\": " + jsonString(System.getProperty("os.arch", "")) + ",\n"
                + "  \"academicScope\": \"PRELIMINARY_PROJECT_TESIS_I\"\n"
                + "}\n";
        Files.writeString(path, json, StandardCharsets.UTF_8);
    }

    private static void writePreliminaryMarkdown(
            Path path,
            List<MetricRow> rows,
            Config config,
            SummaryMetrics summary
    ) throws IOException {
        StringBuilder builder = new StringBuilder();
        builder.append("# Resultados preliminares del harness de tesis\n\n");
        builder.append("Generado en UTC: `").append(summary.generatedAtUtc()).append("`\n\n");

        builder.append("## Ejecucion\n\n");
        builder.append("```bash\n");
        builder.append(reconstructedCommand(config)).append("\n");
        builder.append("```\n\n");
        builder.append("- Mecanismo evaluado: `").append(summary.mechanism()).append("`\n");
        builder.append("- Registros por corrida: `").append(summary.recordsPerRun()).append("`\n");
        builder.append("- Corridas: `").append(summary.runs()).append("`\n");
        builder.append("- Indice alterado: `").append(config.tamperIndex()).append("`\n");
        builder.append("- Directorio de salida: `").append(config.outputDir()).append("`\n\n");

        builder.append("## Resumen preliminar\n\n");
        builder.append("| Metrica | Valor |\n");
        builder.append("| --- | ---: |\n");
        builder.append("| Tasa de deteccion | ").append(formatPercent(summary.tamperDetectionRate())).append(" |\n");
        builder.append("| P95 promedio de insercion | ").append(formatDouble(summary.meanAppendP95Ms())).append(" ms |\n");
        builder.append("| Tiempo medio de verificacion | ").append(formatDouble(summary.meanVerificationMs())).append(" ms |\n");
        builder.append("| Corridas alteradas detectadas | ").append(summary.detectedTamperRuns())
                .append(" / ").append(summary.tamperRuns()).append(" |\n\n");

        builder.append("## Escenarios ejecutados\n\n");
        builder.append("| Escenario | Operacion | Ejecuciones | Detecciones | Duracion media (ms) | P95 promedio insercion (ms) | Bytes medios BD |\n");
        builder.append("| --- | --- | ---: | ---: | ---: | ---: | ---: |\n");
        for (ScenarioSummary scenario : summarizeScenarios(rows)) {
            builder.append("| `").append(scenario.scenario()).append("` | `")
                    .append(scenario.operation()).append("` | ")
                    .append(scenario.executions()).append(" | ")
                    .append(scenario.detections()).append(" | ")
                    .append(formatDouble(scenario.meanDurationMs())).append(" | ")
                    .append(formatDouble(scenario.meanP95AppendMs())).append(" | ")
                    .append(formatDouble(scenario.meanDbBytes())).append(" |\n");
        }
        builder.append("\n");

        builder.append("## Limitaciones academicas\n\n");
        builder.append("- Esto es evidencia preliminar para Proyecto de Tesis I.\n");
        builder.append("- Solo evalua `M6_HASH_CHAIN_ED25519`.\n");
        builder.append("- Solo cubre `E1_EDIT_SALE_JSON` como escenario controlado de alteracion.\n");
        builder.append("- No reemplaza el benchmark completo requerido para Proyecto de Tesis II.\n");

        Files.writeString(path, builder.toString(), StandardCharsets.UTF_8);
    }

    private static List<ScenarioSummary> summarizeScenarios(List<MetricRow> rows) {
        List<ScenarioSummary> summaries = new ArrayList<>();
        for (MetricRow row : rows) {
            boolean alreadySummarized = summaries.stream()
                    .anyMatch(summary -> summary.scenario().equals(row.scenario())
                            && summary.operation().equals(row.operation()));
            if (alreadySummarized) {
                continue;
            }
            List<MetricRow> matchingRows = rows.stream()
                    .filter(candidate -> candidate.scenario().equals(row.scenario())
                            && candidate.operation().equals(row.operation()))
                    .toList();
            long detections = matchingRows.stream().filter(MetricRow::detected).count();
            double meanDurationMs = matchingRows.stream()
                    .mapToDouble(MetricRow::durationMs)
                    .average()
                    .orElse(0.0);
            double meanP95AppendMs = matchingRows.stream()
                    .mapToDouble(MetricRow::p95AppendMs)
                    .average()
                    .orElse(0.0);
            double meanDbBytes = matchingRows.stream()
                    .mapToLong(MetricRow::dbBytes)
                    .average()
                    .orElse(0.0);
            summaries.add(new ScenarioSummary(
                    row.scenario(),
                    row.operation(),
                    matchingRows.size(),
                    detections,
                    meanDurationMs,
                    meanP95AppendMs,
                    meanDbBytes
            ));
        }
        return summaries;
    }

    private static String reconstructedCommand(Config config) {
        return "mvn org.codehaus.mojo:exec-maven-plugin:3.5.0:java "
                + "-Dexec.mainClass=com.mycompany.pepitoapp.security.metrics.ThesisMetricsRunner "
                + "-Dexec.args=\""
                + "--records=" + config.records()
                + " --runs=" + config.runs()
                + " --tamper-index=" + config.tamperIndex()
                + " --output=" + config.outputDir()
                + " --seed=" + config.seed()
                + " --argon-iterations=" + config.argonIterations()
                + " --argon-memory-kb=" + config.argonMemoryKb()
                + "\"";
    }

    private static String csv(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        return '"' + value.replace("\"", "\"\"") + '"';
    }

    private static String jsonString(String value) {
        if (value == null) {
            return "\"\"";
        }
        return '"' + value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t")
                + '"';
    }

    private static String formatDouble(double value) {
        return String.format(Locale.ROOT, "%.6f", value);
    }

    private static String formatPercent(double value) {
        return String.format(Locale.ROOT, "%.2f%%", value * 100.0);
    }

    private record SummaryMetrics(
            Instant generatedAtUtc,
            String mechanism,
            int recordsPerRun,
            int runs,
            long tamperRuns,
            long detectedTamperRuns,
            double tamperDetectionRate,
            double meanAppendP95Ms,
            double meanVerificationMs
    ) {
    }

    private record ScenarioSummary(
            String scenario,
            String operation,
            int executions,
            long detections,
            double meanDurationMs,
            double meanP95AppendMs,
            double meanDbBytes
    ) {
    }

    private record ExperimentContext(
            SecureDatabaseProvider provider,
            CryptoService crypto,
            LedgerService ledger,
            Path databasePath
    ) {
    }

    private record VerificationMeasurement(
            String scenario,
            int records,
            int tamperIndex,
            boolean valid,
            boolean detected,
            long durationNs,
            int errorCount,
            String firstError
    ) {
        MetricRow toMetricRow(int run, int records, long dbBytes) {
            return new MetricRow(
                    Instant.now(),
                    run,
                    MECHANISM,
                    scenario,
                    "verify_chain",
                    records,
                    tamperIndex,
                    valid,
                    detected,
                    durationNs / 1_000_000.0,
                    0.0,
                    0.0,
                    0.0,
                    dbBytes,
                    errorCount,
                    firstError
            );
        }
    }

    private record MetricRow(
            Instant timestamp,
            int run,
            String mechanism,
            String scenario,
            String operation,
            int records,
            int tamperIndex,
            boolean valid,
            boolean detected,
            double durationMs,
            double meanAppendMs,
            double p95AppendMs,
            double p99AppendMs,
            long dbBytes,
            int errorCount,
            String firstError
    ) {
        static MetricRow appendBatch(
                int run,
                int records,
                long dbBytes,
                double meanAppendMs,
                double p95AppendMs,
                double p99AppendMs
        ) {
            return new MetricRow(
                    Instant.now(),
                    run,
                    MECHANISM,
                    "CONTROL_CLEAN",
                    "append_batch",
                    records,
                    -1,
                    true,
                    false,
                    0.0,
                    meanAppendMs,
                    p95AppendMs,
                    p99AppendMs,
                    dbBytes,
                    0,
                    ""
            );
        }

        String toCsvLine() {
            return String.join(",",
                    csv(timestamp.toString()),
                    Integer.toString(run),
                    csv(mechanism),
                    csv(scenario),
                    csv(operation),
                    Integer.toString(records),
                    Integer.toString(tamperIndex),
                    Boolean.toString(valid),
                    Boolean.toString(detected),
                    formatDouble(durationMs),
                    formatDouble(meanAppendMs),
                    formatDouble(p95AppendMs),
                    formatDouble(p99AppendMs),
                    Long.toString(dbBytes),
                    Integer.toString(errorCount),
                    csv(firstError)
            );
        }
    }

    private record Config(
            int records,
            int runs,
            int tamperIndex,
            long seed,
            int argonIterations,
            int argonMemoryKb,
            Path outputDir
    ) {
        static Config parse(String[] args) {
            int records = 200;
            int runs = 5;
            int tamperIndex = 50;
            long seed = 20260709L;
            int argonIterations = 1;
            int argonMemoryKb = 8192;
            Path outputDir = Path.of("build", "thesis-metrics");

            for (String arg : args) {
                if (arg.equals("--help") || arg.equals("-h")) {
                    printHelpAndExit();
                } else if (arg.startsWith("--records=")) {
                    records = positiveInt(arg, "--records=");
                } else if (arg.startsWith("--runs=")) {
                    runs = positiveInt(arg, "--runs=");
                } else if (arg.startsWith("--tamper-index=")) {
                    tamperIndex = positiveInt(arg, "--tamper-index=");
                } else if (arg.startsWith("--seed=")) {
                    seed = Long.parseLong(arg.substring("--seed=".length()));
                } else if (arg.startsWith("--argon-iterations=")) {
                    argonIterations = positiveInt(arg, "--argon-iterations=");
                } else if (arg.startsWith("--argon-memory-kb=")) {
                    argonMemoryKb = positiveInt(arg, "--argon-memory-kb=");
                } else if (arg.startsWith("--output=")) {
                    outputDir = Path.of(arg.substring("--output=".length()));
                } else {
                    throw new IllegalArgumentException("Unknown argument: " + arg);
                }
            }
            return new Config(records, runs, tamperIndex, seed, argonIterations, argonMemoryKb, outputDir);
        }

        private static int positiveInt(String arg, String prefix) {
            int value = Integer.parseInt(arg.substring(prefix.length()));
            if (value <= 0) {
                throw new IllegalArgumentException(prefix + " must be greater than zero");
            }
            return value;
        }

        private static void printHelpAndExit() {
            System.out.println("Usage: mvn -Pmetrics exec:java -Dexec.args=\"--records=200 --runs=5\"");
            System.out.println("Options:");
            System.out.println("  --records=N            Synthetic ledger entries per run");
            System.out.println("  --runs=N               Repeated executions");
            System.out.println("  --tamper-index=N       Ledger id edited after clean verification");
            System.out.println("  --output=PATH          Output directory for CSV/JSON");
            System.out.println("  --seed=N               Deterministic data seed");
            System.out.println("  --argon-iterations=N   Argon2 iterations for benchmark harness");
            System.out.println("  --argon-memory-kb=N    Argon2 memory cost for benchmark harness");
            System.exit(0);
        }
    }
}
