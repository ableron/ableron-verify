package io.github.ableron

import org.testcontainers.containers.GenericContainer
import org.testcontainers.images.builder.ImageFromDockerfile

import java.nio.file.Path

class AbleronSpringBoot3Spec extends UiCompositionBaseSpec {

  @Override
  GenericContainer getContainerUnderTest() {
    return new GenericContainer<>(new ImageFromDockerfile()
      .withDockerfile(Path.of("ableron-spring-boot-3", "Dockerfile")))
      .withExposedPorts(8080)
  }
}
