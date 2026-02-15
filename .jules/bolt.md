## 2024-05-23 - Long Polling Optimization
**Learning:** Telegram API long polling (30s timeout) requires an HTTP client read timeout *greater* than 30s. The default OkHttp timeout (10s) causes frequent `SocketTimeoutException`, breaking the long-poll efficiency and causing unnecessary re-connections/battery drain.
**Action:** Configure a custom `OkHttpClient` with `readTimeout(60, TimeUnit.SECONDS)` for long-polling services.

## 2024-05-23 - Kotlin/Kapt JDK 16+ Compatibility
**Learning:** Kapt fails on JDK 16+ due to restricted internal API access (`com.sun.tools.javac`). It requires explicit `--add-opens` flags for `jdk.compiler` modules to function correctly. Additionally, strict version compatibility between Kotlin and Compose Compiler is enforced (e.g., Kotlin 1.9.22 requires Compose Compiler 1.5.8).
**Action:** Use `org.gradle.jvmargs` or `JAVA_TOOL_OPTIONS` with `--add-opens` flags and verify versions in `libs.versions.toml`/`build.gradle.kts`.
