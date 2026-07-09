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
        writeCsv(csvPath, rows);
        writeSummaryJson(summaryPath, rows, config);

        System.out.println("Metrics written to: " + csvPath.toAbsolutePath());
        System.out.println("Summary written to: " + summaryPath.toAbsolutePath());
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

    private static void writeSummaryJson(Path path, List<MetricRow> rows, Config config) throws IOException {
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

        String json = "{\n"
                + "  \"generatedAtUtc\": \"" + Instant.now() + "\",\n"
                + "  \"mechanism\": \"" + MECHANISM + "\",\n"
                + "  \"recordsPerRun\": " + config.records() + ",\n"
                + "  \"runs\": " + config.runs() + ",\n"
                + "  \"tamperRuns\": " + tamperRuns + ",\n"
                + "  \"detectedTamperRuns\": " + detectedRuns + ",\n"
                + "  \"tamperDetectionRate\": " + formatDouble(detectionRate) + ",\n"
                + "  \"meanAppendP95Ms\": " + formatDouble(appendP95Mean) + ",\n"
                + "  \"meanVerificationMs\": " + formatDouble(verificationMsMean) + "\n"
                + "}\n";
        Files.writeString(path, json, StandardCharsets.UTF_8);
    }

    private static String csv(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        return '"' + value.replace("\"", "\"\"") + '"';
    }

    private static String formatDouble(double value) {
        return String.format(Locale.ROOT, "%.6f", value);
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
