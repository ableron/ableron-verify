# Ableron Verification Test Suite
[![Build Status](https://github.com/ableron/ableron-verify/actions/workflows/main.yml/badge.svg)](https://github.com/ableron/ableron-verify/actions/workflows/main.yml)

Test suite to verify all implementations of ableron provide a common feature set.

## Quick Start
* Run tests
   ```console
   $ ./gradlew clean test
   ```
* Check for outdated dependencies via [Gradle Versions Plugin](https://github.com/ben-manes/gradle-versions-plugin)
   ```console
   $ ./gradlew dependencyUpdates -Drevision=release
   ```
