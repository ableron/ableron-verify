package io.github.ableron

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.extension.responsetemplating.ResponseTemplateTransformer
import org.testcontainers.Testcontainers
import org.testcontainers.containers.GenericContainer
import spock.lang.Shared
import spock.lang.Specification

import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

import static com.github.tomakehurst.wiremock.client.WireMock.*
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
    wiremockServer = new WireMockServer(options()
      .dynamicPort()
      .extensions(new ResponseTemplateTransformer(false)))
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

  def "should return content untouched if no (valid) includes are present"() {
    when:
    def response = httpClient.send(HttpRequest.newBuilder()
      .uri(verifyUrl)
      .POST(HttpRequest.BodyPublishers.ofString(responseTemplate))
      .build(), HttpResponse.BodyHandlers.ofString())

    then:
    response.statusCode() == 200
    response.body() == responseTemplate

    where:
    responseTemplate << [
      "test",
      "<ableron-include/>",
      "<ableron-include >",
      "<ableron-include src=\"s\">",
      "<ableron-include src=\"s\" b=\"b\">"
    ]
  }

  def "should resolve includes"() {
    given:
    wiremockServer.stubFor(get("/fragment").willReturn(ok()
      .withBody("fragment")
    ))

    when:
    def response = httpClient.send(HttpRequest.newBuilder()
      .uri(verifyUrl)
      .POST(HttpRequest.BodyPublishers.ofString(responseTemplate))
      .build(), HttpResponse.BodyHandlers.ofString())

    then:
    response.statusCode() == 200
    response.body() == expectedResponseBody

    where:
    responseTemplate                                                                                                                 | expectedResponseBody
    "<ableron-include src=\"http://host.testcontainers.internal:${wiremockServer.port()}/fragment\"/>"                               | "fragment"
    "<ableron-include src=\"http://host.testcontainers.internal:${wiremockServer.port()}/fragment\" />"                              | "fragment"
    "<ableron-include\nsrc=\"http://host.testcontainers.internal:${wiremockServer.port()}/fragment\"\n\n/>"                          | "fragment"
    "<ableron-include\tsrc=\"http://host.testcontainers.internal:${wiremockServer.port()}/fragment\"\t\t/>"                          | "fragment"
    "<ableron-include src=\"http://host.testcontainers.internal:${wiremockServer.port()}/fragment\"></ableron-include>"              | "fragment"
    "<ableron-include src=\"http://host.testcontainers.internal:${wiremockServer.port()}/fragment\"> </ableron-include>"             | "fragment"
    "<ableron-include src=\"http://host.testcontainers.internal:${wiremockServer.port()}/fragment\">foo\nbar\nbaz</ableron-include>" | "fragment"
    "\n<ableron-include src=\"http://host.testcontainers.internal:${wiremockServer.port()}/fragment\"/>\n"                           | "\nfragment\n"
    "<div><ableron-include src=\"http://host.testcontainers.internal:${wiremockServer.port()}/fragment\"/></div>"                    | "<div>fragment</div>"
    "<ableron-include src=\"http://host.testcontainers.internal:${wiremockServer.port()}/fragment\"  fallback-src=\"...\"/>"         | "fragment"
    "<ableron-include foo=\"\" src=\"http://host.testcontainers.internal:${wiremockServer.port()}/fragment\"/>"                      | "fragment"
    "<ableron-include -src=\"http://host.testcontainers.internal:${wiremockServer.port()}/fragment\">fallback</ableron-include>"     | "fallback"
    "<ableron-include _src=\"http://host.testcontainers.internal:${wiremockServer.port()}/fragment\">fallback</ableron-include>"     | "fallback"
    "<ableron-include 0src=\"http://host.testcontainers.internal:${wiremockServer.port()}/fragment\">fallback</ableron-include>"     | "fallback"
  }

  def "should resolve multiple includes in same response"() {
    given:
    def responseTemplate = """
      <html>
      <head>
        <ableron-include src="http://host.testcontainers.internal:${wiremockServer.port()}/echo/fragment1" />
        <title>Foo</title>
        <ableron-include foo="bar" src="http://host.testcontainers.internal:${wiremockServer.port()}/echo/fragment2"/>
      </head>
      <body>
        <ableron-include src="http://host.testcontainers.internal:${wiremockServer.port()}/echo/fragment3" fallback-src="https://example.com"/>
        <ableron-include src="http://host.testcontainers.internal:${wiremockServer.port()}/echo/fragment3" fallback-src="https://example.com">fallback</ableron-include>
      </body>
      </html>
    """
    wiremockServer.stubFor(get(urlPathMatching("/echo/.*")).willReturn(ok()
      .withBody("{{request.pathSegments.[1]}}")
      .withTransformers("response-template")
    ))

    when:
    def response = httpClient.send(HttpRequest.newBuilder()
      .uri(verifyUrl)
      .POST(HttpRequest.BodyPublishers.ofString(responseTemplate))
      .build(), HttpResponse.BodyHandlers.ofString())

    then:
    response.statusCode() == 200
    response.body() == """
      <html>
      <head>
        fragment1
        <title>Foo</title>
        fragment2
      </head>
      <body>
        fragment3
        fragment3
      </body>
      </html>
    """
  }

  def "should replace identical includes"() {
    given:
    def responseTemplate = """
      <ableron-include src="foo-bar"><!-- #1 --></ableron-include>
      <ableron-include src="foo-bar"><!-- #1 --></ableron-include>
      <ableron-include src="foo-bar"><!-- #1 --></ableron-include>
      <ableron-include src="foo-bar"><!-- #2 --></ableron-include>
    """

    when:
    def response = httpClient.send(HttpRequest.newBuilder()
      .uri(verifyUrl)
      .POST(HttpRequest.BodyPublishers.ofString(responseTemplate))
      .build(), HttpResponse.BodyHandlers.ofString())

    then:
    response.statusCode() == 200
    response.body() == """
      <!-- #1 -->
      <!-- #1 -->
      <!-- #1 -->
      <!-- #2 -->
    """
  }

  def "should resolve includes in big content"() {
    given:
    def randomStringWithoutIncludes = new Random().ints(32, 127)
      .limit(512 * 1024)
      .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
      .toString()
    def include = "<ableron-include src=\"http://host.testcontainers.internal:${wiremockServer.port()}/fragment\"/>"
    wiremockServer.stubFor(get("/fragment").willReturn(ok()
      .withBody("fragment")
    ))

    when:
    def responseRandomStringWithIncludeAtTheBeginning = httpClient.send(HttpRequest.newBuilder()
      .uri(verifyUrl)
      .POST(HttpRequest.BodyPublishers.ofString(include + randomStringWithoutIncludes))
      .build(), HttpResponse.BodyHandlers.ofString())
    def responseRandomStringWithIncludeAtTheEnd = httpClient.send(HttpRequest.newBuilder()
      .uri(verifyUrl)
      .POST(HttpRequest.BodyPublishers.ofString(randomStringWithoutIncludes + include))
      .build(), HttpResponse.BodyHandlers.ofString())
    def responseRandomStringWithIncludeAtTheMiddle = httpClient.send(HttpRequest.newBuilder()
      .uri(verifyUrl)
      .POST(HttpRequest.BodyPublishers.ofString(randomStringWithoutIncludes + include + randomStringWithoutIncludes))
      .build(), HttpResponse.BodyHandlers.ofString())

    then:
    responseRandomStringWithIncludeAtTheBeginning.statusCode() == 200
    responseRandomStringWithIncludeAtTheBeginning.body() == "fragment" + randomStringWithoutIncludes
    responseRandomStringWithIncludeAtTheEnd.statusCode() == 200
    responseRandomStringWithIncludeAtTheEnd.body() == randomStringWithoutIncludes + "fragment"
    responseRandomStringWithIncludeAtTheMiddle.statusCode() == 200
    responseRandomStringWithIncludeAtTheMiddle.body() == randomStringWithoutIncludes + "fragment" + randomStringWithoutIncludes
  }

  def "should set Content-Length header to zero for empty response"() {
    when:
    def response = httpClient.send(HttpRequest.newBuilder()
      .uri(verifyUrl)
      .POST(HttpRequest.BodyPublishers.ofString(responseTemplate))
      .build(), HttpResponse.BodyHandlers.ofString())

    then:
    response.statusCode() == 200
    response.headers().firstValue("Content-Length") == Optional.of(expectedResponseContentLength)
    response.body() == expectedResponseBody

    where:
    responseTemplate                                       | expectedResponseBody | expectedResponseContentLength
    "<ableron-include />"                                  | ""                   | "0"
    "<ableron-include ></ableron-include>"                 | ""                   | "0"
    "<ableron-include _src=\"foo.bar\"></ableron-include>" | ""                   | "0"
    "<ableron-include > </ableron-include>"                | " "                  | "1"
  }

  def "should not crash due to #scenarioName"() {
    when:
    def response = httpClient.send(HttpRequest.newBuilder()
      .uri(verifyUrl)
      .POST(HttpRequest.BodyPublishers.ofString("<ableron-include >before</ableron-include>" + includeTag + "<ableron-include >after</ableron-include>"))
      .build(), HttpResponse.BodyHandlers.ofString())

    then:
    response.statusCode() == 200
    response.body() == "before" + expectedResult + "after"

    where:
    scenarioName                   | includeTag                                                                     | expectedResult
    "invalid src url"              | '<ableron-include src=",._">fallback</ableron-include>'                        | "fallback"
    "invalid src timeout"          | '<ableron-include src-timeout-millis="5s">fallback</ableron-include>'          | "fallback"
    "invalid fallback-src timeout" | '<ableron-include fallback-src-timeout-millis="5s">fallback</ableron-include>' | "fallback"
  }
}
