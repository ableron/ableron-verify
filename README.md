# Ableron Verification Test Suite
Test suite to verify all implementations of ableron provide a common feature set.

* [![ableron-java with Java 11 Status](https://github.com/ableron/ableron-verify/actions/workflows/ableron-java-java11.yml/badge.svg)](https://github.com/ableron/ableron-verify/actions/workflows/ableron-java-java11.yml)
* [![ableron-java with Java 17 Status](https://github.com/ableron/ableron-verify/actions/workflows/ableron-java-java17.yml/badge.svg)](https://github.com/ableron/ableron-verify/actions/workflows/ableron-java-java17.yml)
* [![ableron-java with Java 21 Status](https://github.com/ableron/ableron-verify/actions/workflows/ableron-java-java21.yml/badge.svg)](https://github.com/ableron/ableron-verify/actions/workflows/ableron-java-java21.yml)
* [![ableron-spring-boot with Spring Boot 2 and Java 11 Status](https://github.com/ableron/ableron-verify/actions/workflows/ableron-spring-boot-2-java11.yml/badge.svg)](https://github.com/ableron/ableron-verify/actions/workflows/ableron-spring-boot-2-java11.yml)
* [![ableron-spring-boot with Spring Boot 2 and Java 21 Status](https://github.com/ableron/ableron-verify/actions/workflows/ableron-spring-boot-2-java21.yml/badge.svg)](https://github.com/ableron/ableron-verify/actions/workflows/ableron-spring-boot-2-java21.yml)
* [![ableron-spring-boot with Spring Boot 3 and Java 17 Status](https://github.com/ableron/ableron-verify/actions/workflows/ableron-spring-boot-3-java17.yml/badge.svg)](https://github.com/ableron/ableron-verify/actions/workflows/ableron-spring-boot-3-java17.yml)
* [![ableron-spring-boot with Spring Boot 3 and Java 21 Status](https://github.com/ableron/ableron-verify/actions/workflows/ableron-spring-boot-3-java21.yml/badge.svg)](https://github.com/ableron/ableron-verify/actions/workflows/ableron-spring-boot-3-java21.yml)
* [![ableron-js with Node.js 21 Status](https://github.com/ableron/ableron-verify/actions/workflows/ableron-js-nodejs21.yml/badge.svg)](https://github.com/ableron/ableron-verify/actions/workflows/ableron-js-nodejs21.yml)

## Quick Start
* Run tests
   ```console
   $ ./gradlew clean test
   ```
* Check for outdated dependencies via [Gradle Versions Plugin](https://github.com/ben-manes/gradle-versions-plugin)
   ```console
   $ ./gradlew dependencyUpdates -Drevision=release
   ```

## How to add new spec
* New runnable application which shall be verified
   * Create folder `/ableron-<technology>-<spec-details>`, e.g. `/ableron-java-java17`
* New test which tests the created application
   * Create file `/src/test/groovy/io/github/ableron/<SpecName>Spec.groovy` (just copy existing spec and adjust path to application)
* New GitHub workflow which runs the new test
   * Create file `/.github/workflows/<SpecName>.yml` (just copy existing one and adjust test to execute)
* New badge in README.md which shows the status of the spec
