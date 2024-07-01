# Ableron Verification Test Suite
Test suite to verify all implementations of ableron provide a common feature set.

* [![ableron-java @ Java 11 Status](https://github.com/ableron/ableron-verify/actions/workflows/java11.yml/badge.svg)](https://github.com/ableron/ableron-verify/actions/workflows/java11.yml)
* [![ableron-java @ Java 17 Status](https://github.com/ableron/ableron-verify/actions/workflows/java17.yml/badge.svg)](https://github.com/ableron/ableron-verify/actions/workflows/java17.yml)
* [![ableron-java @ Java 21 Status](https://github.com/ableron/ableron-verify/actions/workflows/java21.yml/badge.svg)](https://github.com/ableron/ableron-verify/actions/workflows/java21.yml)
* [![ableron-spring-boot @ Java 11 & Spring Boot 2 Status](https://github.com/ableron/ableron-verify/actions/workflows/java11-spring-boot-2.yml/badge.svg)](https://github.com/ableron/ableron-verify/actions/workflows/java11-spring-boot-2.yml)
* [![ableron-spring-boot @ Java 21 & Spring Boot 2 Status](https://github.com/ableron/ableron-verify/actions/workflows/java21-spring-boot-2.yml/badge.svg)](https://github.com/ableron/ableron-verify/actions/workflows/java21-spring-boot-2.yml)
* [![ableron-spring-boot @ Java 17 & Spring Boot 3 Status](https://github.com/ableron/ableron-verify/actions/workflows/java17-spring-boot-3.yml/badge.svg)](https://github.com/ableron/ableron-verify/actions/workflows/java17-spring-boot-3.yml)
* [![ableron-spring-boot @ Java 21 & Spring Boot 3 Status](https://github.com/ableron/ableron-verify/actions/workflows/java21-spring-boot-3.yml/badge.svg)](https://github.com/ableron/ableron-verify/actions/workflows/java21-spring-boot-3.yml)
* [![ableron-js @ Node.js 22 CommonJS Status](https://github.com/ableron/ableron-verify/actions/workflows/nodejs22-cjs.yml/badge.svg)](https://github.com/ableron/ableron-verify/actions/workflows/nodejs22-cjs.yml)
* [![ableron-js @ Node.js 22 ES Modules Status](https://github.com/ableron/ableron-verify/actions/workflows/nodejs22-esm.yml/badge.svg)](https://github.com/ableron/ableron-verify/actions/workflows/nodejs22-esm.yml)
* [![ableron-express @ Node.js 22 & Express 4 Status](https://github.com/ableron/ableron-verify/actions/workflows/nodejs22-express4.yml/badge.svg)](https://github.com/ableron/ableron-verify/actions/workflows/nodejs22-express4.yml)
* [![ableron-express @ Node.js 22 & Express 4 & CJS Status](https://github.com/ableron/ableron-verify/actions/workflows/nodejs22-express4-cjs.yml/badge.svg)](https://github.com/ableron/ableron-verify/actions/workflows/nodejs22-express4-cjs.yml)
* [![ableron-fastify @ Node.js 22 & Fastify 4 Status](https://github.com/ableron/ableron-verify/actions/workflows/nodejs22-fastify4.yml/badge.svg)](https://github.com/ableron/ableron-verify/actions/workflows/nodejs22-fastify4.yml)

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
   * Create folder `/<language>-<framework>`, e.g. `/java21-spring-boot-3`
   * Application must run on port `8080`
* New test which tests the created application
   * Create file `/src/test/groovy/io/github/ableron/<SpecName>Spec.groovy` (just copy existing spec and adjust path to application)
* New GitHub workflow which runs the new test
   * Create file `/.github/workflows/<SpecName>.yml` (just copy existing one and adjust test to execute)
* New badge in README.md which shows the status of the spec

## Contributing

All contributions are greatly appreciated, be it pull requests, feature requests or bug reports. See
[ableron.github.io](https://ableron.github.io/) for details.

## License

Licensed under [MIT](./LICENSE).
