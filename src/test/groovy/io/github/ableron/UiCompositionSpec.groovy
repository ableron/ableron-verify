package io.github.ableron

import org.testcontainers.containers.GenericContainer
import org.testcontainers.images.builder.ImageFromDockerfile
import spock.lang.Shared
import spock.lang.Specification

import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Path

@org.testcontainers.spock.Testcontainers
class UiCompositionSpec extends Specification {

  @Shared
  GenericContainer ableronJava = new GenericContainer<>(new ImageFromDockerfile("ableron/ableron-verify/ableron-java", true)
    .withDockerfile(Path.of("ableron-java", "Dockerfile")))
    .withExposedPorts(8080)

  @Shared
  URI verifyUrl

  @Shared
  def httpClient = HttpClient.newBuilder().build()

  def setupSpec() {
    verifyUrl = URI.create("http://${ableronJava.host}:${ableronJava.firstMappedPort}/verify")
  }

  def "should return content untouched if no includes are present"() {
    when:
    def response = httpClient.send(HttpRequest.newBuilder()
      .uri(verifyUrl)
      .POST(HttpRequest.BodyPublishers.ofString("test"))
      .build(), HttpResponse.BodyHandlers.ofString())

    then:
    response.statusCode() == 200
    response.body() == "test"
  }
}
