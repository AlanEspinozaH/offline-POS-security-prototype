# Installation and DevSecOps Guide

This guide describes how to set up, execute, and validate the security prototype and the thesis metrics harness in a reproducible way.

The project is a research prototype for a local POS security layer. The main DevSecOps goal is to keep installation, execution, secrets handling, and metric collection explicit enough to be repeated during Proyecto de Tesis II.

## 1. Supported environment

Recommended baseline:

- Operating system: Windows 11 or Linux for development.
- JDK: 21 or newer.
- Build tool: Maven 3.9 or newer.
- Database: SQLite through `sqlite-jdbc`.
- Optional future target: SQLCipher-compatible JDBC driver for encrypted storage.

Security note: use the same JDK and Maven versions when comparing metrics. Runtime changes can affect latency, file size, and reproducibility.

## 2. Repository setup

Clone the repository and enter the project directory:

```bash
git clone https://github.com/AlanEspinozaH/offline-POS-security-prototype.git
cd offline-POS-security-prototype
```

Verify Java and Maven:

```bash
java -version
mvn -version
```

Build without running the JavaFX UI:

```bash
mvn clean package
```

Run the JavaFX application:

```bash
mvn javafx:run
```

## 3. Local secrets and passphrases

The prototype must not commit real keys, real passphrases, local databases, or production receipts.

For local runs, define a development passphrase through the environment:

```bash
export PEPITO_PASSPHRASE="replace-with-local-development-passphrase"
```

On Windows PowerShell:

```powershell
$env:PEPITO_PASSPHRASE="replace-with-local-development-passphrase"
```

Do not use the default demo passphrase for production-like experiments. For thesis measurements, record only that a fixed passphrase was used; do not publish the actual value if it is reused anywhere else.

## 4. Generated local files

The prototype can generate local SQLite databases, keystore material, CSV files, and JSON summaries. These files are experimental artifacts and must not be mixed with source code commits unless they are intentionally anonymized and small enough for review.

Expected generated paths include:

- `build/thesis-metrics/`
- `build/thesis-metrics-*/`
- local SQLite files such as `*.db`, `*.db-wal`, `*.db-shm`
- local keystore material under `.pepitoapp`-like development directories

Before committing, run:

```bash
git status --short
```

Only source code, documentation, workflows, and small anonymized sample outputs should be committed.

## 5. Running the thesis metrics harness

The metrics harness is intended to support the thesis methodology by producing repeatable CSV/JSON outputs.

Quick exploratory run:

```bash
mvn org.codehaus.mojo:exec-maven-plugin:3.5.0:java \
  -Dexec.mainClass=com.mycompany.pepitoapp.security.metrics.ThesisMetricsRunner \
  -Dexec.args="--records=200 --runs=5 --tamper-index=50"
```

More robust run for Proyecto de Tesis II:

```bash
mvn org.codehaus.mojo:exec-maven-plugin:3.5.0:java \
  -Dexec.mainClass=com.mycompany.pepitoapp.security.metrics.ThesisMetricsRunner \
  -Dexec.args="--records=1000 --runs=30 --tamper-index=500 --output=build/thesis-metrics-1000x30"
```

Expected outputs:

```text
build/thesis-metrics/thesis_metrics.csv
build/thesis-metrics/thesis_metrics_summary.json
```

## 6. Minimum acceptance checks

Before using results in the thesis document, perform these checks:

```bash
mvn clean package
mvn org.codehaus.mojo:exec-maven-plugin:3.5.0:java \
  -Dexec.mainClass=com.mycompany.pepitoapp.security.metrics.ThesisMetricsRunner \
  -Dexec.args="--records=50 --runs=2 --tamper-index=10 --output=build/thesis-metrics-smoke"
```

Then verify:

- the build finishes without compilation errors;
- `thesis_metrics.csv` exists;
- `thesis_metrics_summary.json` exists;
- the clean verification scenario reports a valid chain;
- the controlled edit scenario is detected;
- no generated database or keystore file appears as an untracked file outside ignored paths.

## 7. DevSecOps checklist

Use this checklist before merging security-related changes:

- Build is reproducible with a documented JDK and Maven version.
- No real secrets are committed.
- Generated databases and keystores are ignored.
- Metrics output is deterministic enough for repeated execution.
- Every benchmark run records number of records, number of repetitions, scenario, mechanism, and output directory.
- Security code is reviewed separately from UI or cosmetic changes.
- The threat model is not silently expanded without updating thesis documentation.
- Any SQLCipher claim is explicitly separated from SQLite-only execution until the SQLCipher driver is actually integrated and tested.
- Any rollback/truncation claim states whether an external anchor, checkpoint, or trusted last hash exists.

## 8. Branching workflow

Recommended flow:

```bash
git checkout main
git pull
git checkout -b feature/metrics-<short-name>
# implement one measurable change
git status --short
mvn clean package
# run a smoke metrics command
git add <source-and-doc-files-only>
git commit -m "Add <specific metrics capability>"
git push -u origin feature/metrics-<short-name>
```

Open a pull request and include:

- what metric or mechanism was added;
- command used to run the smoke test;
- sample output path;
- known limitations;
- whether the change affects thesis claims.

## 9. Known installation caveat

The README states that the target language/runtime is Java JDK 21 or newer, but the Maven compiler release should be checked to ensure it does not require a higher JDK than intended. For reproducible thesis work, align the Maven compiler release with the documented JDK baseline.

## 10. Thesis usage boundary

For Proyecto de Tesis I, treat these scripts as instrumentation and preliminary technical evidence. Do not present exploratory metrics as final benchmark results unless the complete protocol has been executed with all mechanisms, repetitions, dataset sizes, and alteration scenarios defined in the methodology.
