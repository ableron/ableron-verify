package io.github.ableron

import org.testcontainers.containers.GenericContainer
import org.testcontainers.images.builder.ImageFromDockerfile

import java.nio.file.Path

class AbleronSpringBoot2Spec extends UiCompositionBaseSpec {

  @Override
  GenericContainer getContainerUnderTest() {
    return new GenericContainer<>(new ImageFromDockerfile()
      .withDockerfile(Path.of("ableron-spring-boot-2", "Dockerfile")))
      .withExposedPorts(8080)
  }
}
