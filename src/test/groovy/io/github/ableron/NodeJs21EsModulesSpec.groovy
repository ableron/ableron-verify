package io.github.ableron

import org.slf4j.LoggerFactory
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.output.Slf4jLogConsumer
import org.testcontainers.images.builder.ImageFromDockerfile

import java.nio.file.Path

class NodeJs21EsModulesSpec extends BaseSpec {

  @Override
  GenericContainer getContainerUnderTest() {
    return new GenericContainer<>(new ImageFromDockerfile()
      .withDockerfile(Path.of("nodejs21-esm", "Dockerfile")))
      .withLogConsumer(new Slf4jLogConsumer(LoggerFactory.getLogger(getClass())))
      .withExposedPorts(8080)
  }
}
