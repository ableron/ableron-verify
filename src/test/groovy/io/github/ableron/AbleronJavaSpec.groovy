package io.github.ableron

import org.testcontainers.containers.GenericContainer
import org.testcontainers.images.builder.ImageFromDockerfile
import org.testcontainers.spock.Testcontainers
import spock.lang.Shared
import spock.lang.Specification

import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Path

@Testcontainers
class AbleronJavaSpec extends Specification {

  GenericContainer ableronJava = new GenericContainer<>(new ImageFromDockerfile()
    .withDockerfile(Path.of("ableron-java", "Dockerfile")))
    .withExposedPorts(8080)

  URI verifyUrl

  def httpClient = HttpClient.newBuilder().build()

  def setup() {
//    ableronJava = new GenericContainer<>(new ImageFromDockerfile()
//      .withDockerfile(Path.of("ableron-java", "Dockerfile")))
//      .withExposedPorts(8080)
    verifyUrl = URI.create("http://${ableronJava.host}:${ableronJava.firstMappedPort}/verify")
    System.out.println(verifyUrl)
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
