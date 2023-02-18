# Ableron Verification Test Suite
Test suite to verify all implementations of ableron provide a common feature set.

* [![Java Integration Status](https://github.com/ableron/ableron-verify/actions/workflows/java-integration.yml/badge.svg)](https://github.com/ableron/ableron-verify/actions/workflows/java-integration.yml)
* [![Spring Boot 2 Integration Status](https://github.com/ableron/ableron-verify/actions/workflows/spring-boot-2-integration.yml/badge.svg)](https://github.com/ableron/ableron-verify/actions/workflows/spring-boot-2-integration.yml)
* [![Spring Boot 3 Integration Status](https://github.com/ableron/ableron-verify/actions/workflows/spring-boot-3-integration.yml/badge.svg)](https://github.com/ableron/ableron-verify/actions/workflows/spring-boot-3-integration.yml)

## Quick Start
* Run tests
   ```console
   $ ./gradlew clean test
   ```
* Check for outdated dependencies via [Gradle Versions Plugin](https://github.com/ben-manes/gradle-versions-plugin)
   ```console
   $ ./gradlew dependencyUpdates -Drevision=release
   ```
