package io.github.ableron

import com.github.tomakehurst.wiremock.WireMockServer
import org.testcontainers.Testcontainers
import org.testcontainers.containers.GenericContainer
import org.testcontainers.images.builder.ImageFromDockerfile
import spock.lang.Shared
import spock.lang.Specification

import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Path

import static com.github.tomakehurst.wiremock.client.WireMock.get
import static com.github.tomakehurst.wiremock.client.WireMock.ok
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options

class UiCompositionWiremockSpec extends Specification {

  @Shared
  WireMockServer wiremockServer

  @Shared
  GenericContainer ableronJava

  @Shared
  URI verifyUrl

  @Shared
  def httpClient = HttpClient.newBuilder().build()

  def setupSpec() {
    wiremockServer = new WireMockServer(options().dynamicPort())
    wiremockServer.start()
    Testcontainers.exposeHostPorts(wiremockServer.port())
    ableronJava = new GenericContainer<>(new ImageFromDockerfile().withDockerfile(Path.of("ableron-java", "Dockerfile")))
      .withExposedPorts(8080)
    ableronJava.start()
    verifyUrl = URI.create("http://${ableronJava.host}:${ableronJava.firstMappedPort}/verify")
  }

  def cleanupSpec() {
    wiremockServer.stop()
    ableronJava.stop()
  }

  def cleanup() {
    wiremockServer.resetAll()
  }

  def "should resolve include"() {
    given:
    wiremockServer.stubFor(get("/fragment").willReturn(ok()
      .withBody("included content")
    ))

    when:
    def response = httpClient.send(HttpRequest.newBuilder()
      .uri(verifyUrl)
      .POST(HttpRequest.BodyPublishers.ofString("<ableron-include src=\"http://host.testcontainers.internal:" + wiremockServer.port() + "/fragment\">fallback</ableron-include>"))
      .build(), HttpResponse.BodyHandlers.ofString())

    then:
    response.statusCode() == 200
    response.body() == "included content"
  }
}
