package io.github.ableron

import com.github.tomakehurst.wiremock.WireMockServer
import org.testcontainers.Testcontainers
import org.testcontainers.containers.GenericContainer
import spock.lang.Shared
import spock.lang.Specification

import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

import static com.github.tomakehurst.wiremock.client.WireMock.get
import static com.github.tomakehurst.wiremock.client.WireMock.ok
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options

abstract class BaseSpec extends Specification {

  @Shared
  WireMockServer wiremockServer

  @Shared
  GenericContainer container

  @Shared
  URI verifyUrl

  @Shared
  def httpClient = HttpClient.newBuilder().build()

  abstract GenericContainer getContainerUnderTest()

  def setupSpec() {
    wiremockServer = new WireMockServer(options().dynamicPort())
    wiremockServer.start()
    Testcontainers.exposeHostPorts(wiremockServer.port())
    container = getContainerUnderTest()
    container.start()
    verifyUrl = URI.create("http://${container.host}:${container.firstMappedPort}/verify")
  }

  def cleanupSpec() {
    wiremockServer.stop()
    container.stop()
  }

  def cleanup() {
    wiremockServer.resetAll()
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
