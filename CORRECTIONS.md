# EcoCode correction notes

This revision prioritizes precision and avoids unsupported claims. It preserves the original Spring Boot / React architecture and all eight rule IDs. It is a source patch, not a verified production release.

| Area | Change |
|---|---|
| Symbol resolution | JavaParser combined source/JRE type solver; qualified signatures; no global arbitrary name/arity fallback. Explicitly imported API types on fields/parameters can provide limited evidence if dependencies are missing. |
| EC101 | Database evidence based on recognized JDBC/JPA/Spring API types; transitive traversal starts from the specific callee, not its caller; simple return/throw/break and indefinite-loop filtering. |
| EC102 | Only loop-carried String accumulation candidates; independent formatting does not count. Recommend builder outside loop. |
| EC103 | List type evidence instead of receiver variable spelling; distinguish membership from index lookup. |
| EC104 | Only recognized Spring Data repository fetches; pagination advice is conditional. |
| EC105 | Restrict to resolved immutable numeric object types and constant constructor inputs; never suggest unconditional hoisting. |
| EC106 | Deep static path is advisory and excluded from score penalties. No claim about measured stack depth. |
| EC107 | Recognize try-with-resources, explicit close and possible ownership transfer; report a candidate, not a proven leak. |
| EC108 | Conservative allowlist of resolved Math/StrictMath computations with literal arguments in a common block; no caching advice for scanner, random, time or unknown calls. |
| Runtime | Disable fabricated metrics and automatic execution of arbitrary uploaded builds. Runtime profiling requires an explicit workload and real instrumentation, not implemented here. |
| Scoring and UI | Missing runtime metrics are null/N/A; normalize available weights. In a new scan, the score is static-only, not a carbon or energy measurement. Avoid comparing old composite scores to new static-only scores. |

## Regression tests

Added `SemanticRegressionTest` (16 tests, including the four Java files from the user's original Student Management System, with its license) and `ScoreRegressionTest` (1 test). Updated existing tests to expect type/purity evidence. The student fixture is in `backend/src/test/resources/student-regression` and should produce no EC101, EC102 or EC108 findings for the originally reported cases. The tests also check positive examples to guard against silencing the rules altogether.

Run from the extracted project on a machine with Java 21 and dependency access:

```sh
cd backend
chmod +x gradlew
./gradlew test
```

Windows: `gradlew.bat test`.

## Verification in the supplied environment

- Java 21 compiler syntax parsing of all 66 Java files (including test fixtures): zero syntax errors.
- Full Gradle test execution was attempted but blocked before compilation by `UnknownHostException: services.gradle.org` when downloading Gradle 8.5. Maven Central was also unreachable. Dependencies were unavailable locally.
- Full dependency-aware compilation, test assertions, frontend build, Docker integration and fresh end-to-end scan remain unverified. No new numerical EcoScore or precision figure is claimed.

## Known limitations / intentional trade-offs

The analyzer is conservative: it can miss true positives. It does not build an uploaded project's dependency classpath, execute external build files, implement a complete control-flow graph, prove method purity, model dynamic dispatch exhaustively, track every resource escape, or measure actual CPU/heap/network/database activity. EC101 may miss custom repository interfaces if their external inheritance cannot resolve. Loop exit detection is a limited syntactic approximation. EC102 and EC108 deliberately support a narrow safe subset. EC105/EC106 are advisory. Static score weights are project heuristics, not calibrated industry benchmarks. A clean scan does not prove efficiency. Existing stored results were not rewritten; rescan both versions before comparing. This build intentionally trades recall for precision and transparency.
