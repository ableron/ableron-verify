package io.github.ableron

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.extension.responsetemplating.ResponseTemplateTransformer
import org.testcontainers.Testcontainers
import org.testcontainers.containers.GenericContainer
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Timeout

import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.concurrent.TimeUnit

import static com.github.tomakehurst.wiremock.client.WireMock.*
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options

abstract class BaseSpec extends Specification {

  @Shared
  WireMockServer wiremockServer

  @Shared
  GenericContainer container

  @Shared
  String wiremockAddress

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
    wiremockAddress = "http://host.testcontainers.internal:${wiremockServer.port()}"
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

  def "should resolve includes with src attribute"() {
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
    responseTemplate                                                                       | expectedResponseBody
    "<ableron-include src=\"${wiremockAddress}/fragment\"/>"                               | "fragment"
    "<ableron-include src=\"${wiremockAddress}/fragment\" />"                              | "fragment"
    "<ableron-include\nsrc=\"${wiremockAddress}/fragment\"\n\n/>"                          | "fragment"
    "<ableron-include\tsrc=\"${wiremockAddress}/fragment\"\t\t/>"                          | "fragment"
    "<ableron-include src=\"${wiremockAddress}/fragment\"></ableron-include>"              | "fragment"
    "<ableron-include src=\"${wiremockAddress}/fragment\"> </ableron-include>"             | "fragment"
    "<ableron-include src=\"${wiremockAddress}/fragment\">foo\nbar\nbaz</ableron-include>" | "fragment"
    "\n<ableron-include src=\"${wiremockAddress}/fragment\"/>\n"                           | "\nfragment\n"
    "<div><ableron-include src=\"${wiremockAddress}/fragment\"/></div>"                    | "<div>fragment</div>"
    "<ableron-include src=\"${wiremockAddress}/fragment\"  fallback-src=\"...\"/>"         | "fragment"
    "<ableron-include foo=\"\" src=\"${wiremockAddress}/fragment\"/>"                      | "fragment"
    "<ableron-include -src=\"${wiremockAddress}/fragment\">fallback</ableron-include>"     | "fallback"
    "<ableron-include _src=\"${wiremockAddress}/fragment\">fallback</ableron-include>"     | "fallback"
    "<ableron-include 0src=\"${wiremockAddress}/fragment\">fallback</ableron-include>"     | "fallback"
  }

  def "should resolve include with fallback-src if src could not be loaded"() {
    given:
    wiremockServer.stubFor(get("/src-500").willReturn(serverError()
      .withBody("response-from-src")
    ))
    wiremockServer.stubFor(get("/fallback-src-200").willReturn(ok()
      .withBody("response-from-fallback-src")
    ))

    when:
    def response = httpClient.send(HttpRequest.newBuilder()
      .uri(verifyUrl)
      .POST(HttpRequest.BodyPublishers.ofString(
        "<ableron-include src=\"${wiremockAddress}/src-500\" fallback-src=\"${wiremockAddress}/fallback-src-200\"/>"
      ))
      .build(), HttpResponse.BodyHandlers.ofString())

    then:
    response.statusCode() == 200
    response.body() == "response-from-fallback-src"
  }

  def "should resolve include with fallback content if src and fallback-src could not be loaded"() {
    given:
    wiremockServer.stubFor(get("/src-500").willReturn(serverError()
      .withBody("response-from-src")
    ))
    wiremockServer.stubFor(get("/fallback-src-404").willReturn(notFound()
      .withBody("response-from-fallback-src")
    ))

    when:
    def response = httpClient.send(HttpRequest.newBuilder()
      .uri(verifyUrl)
      .POST(HttpRequest.BodyPublishers.ofString(
        "<ableron-include src=\"${wiremockAddress}/src-500\" fallback-src=\"${wiremockAddress}/fallback-src-404\">fallback content</ableron-include>"
      ))
      .build(), HttpResponse.BodyHandlers.ofString())

    then:
    response.statusCode() == 200
    response.body() == "fallback content"
  }

  def "should resolve multiple includes in same response"() {
    given:
    def responseTemplate = """
      <html>
      <head>
        <ableron-include src="${wiremockAddress}/echo/fragment1" />
        <title>Foo</title>
        <ableron-include foo="bar" src="${wiremockAddress}/echo/fragment2"/>
      </head>
      <body>
        <ableron-include src="${wiremockAddress}/echo/fragment3" fallback-src="https://example.com"/>
        <ableron-include src="${wiremockAddress}/echo/fragment3" fallback-src="https://example.com">fallback</ableron-include>
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
    def include = "<ableron-include src=\"${wiremockAddress}/fragment\"/>"
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

  def "should cache responses"() {
    given:
    def responseTemplate = """
      <html>
      <head>
        <ableron-include src="${wiremockAddress}/k5I9M"><!-- failed loading 1st include --></ableron-include>
        <title>Foo</title>
        <ableron-include src="${wiremockAddress}/k5I9M"><!-- failed loading 2nd include --></ableron-include>
      </head>
      <body>
        <ableron-include src="${wiremockAddress}/k5I9M"><!-- failed loading 3rd include --></ableron-include>
        <ableron-include src="${wiremockAddress}/k5I9M_404"><!-- failed loading 4th include --></ableron-include>
      </body>
      </html>
    """
    wiremockServer.stubFor(get("/k5I9M").willReturn(ok()
      .withBody("fragment")
      .withFixedDelay(200)
    ))
    wiremockServer.stubFor(get("/k5I9M_404").willReturn(notFound()))

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
        fragment
        <title>Foo</title>
        fragment
      </head>
      <body>
        fragment
        <!-- failed loading 4th include -->
      </body>
      </html>
    """
    wiremockServer.getAllServeEvents().size() == 2
    wiremockServer.verify(1, getRequestedFor(urlEqualTo("/k5I9M")))
    wiremockServer.verify(1, getRequestedFor(urlEqualTo("/k5I9M_404")))
  }

  def "should not cache responses if prohibited by Expires header"() {
    given:
    def responseTemplate = """
      <html>
      <head>
        <ableron-include src="${wiremockAddress}/heM8d"><!-- failed loading 1st include --></ableron-include>
        <title>Foo</title>
        <ableron-include src="${wiremockAddress}/heM8d"><!-- failed loading 2nd include --></ableron-include>
      </head>
      <body>
        <ableron-include src="${wiremockAddress}/heM8d"><!-- failed loading 3rd include --></ableron-include>
        <ableron-include src="${wiremockAddress}/heM8d_404"><!-- failed loading 4th include --></ableron-include>
      </body>
      </html>
    """
    wiremockServer.stubFor(get("/heM8d").willReturn(ok()
      .withBody("fragment")
      .withHeader("Expires", "0")
      .withFixedDelay(200)
    ))
    wiremockServer.stubFor(get("/heM8d_404").willReturn(notFound()))

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
        fragment
        <title>Foo</title>
        fragment
      </head>
      <body>
        fragment
        <!-- failed loading 4th include -->
      </body>
      </html>
    """
    wiremockServer.getAllServeEvents().size() == 4
    wiremockServer.verify(3, getRequestedFor(urlEqualTo("/heM8d")))
    wiremockServer.verify(1, getRequestedFor(urlEqualTo("/heM8d_404")))
  }

  @Timeout(value = 7, unit = TimeUnit.SECONDS)
  def "should resolve includes in parallel"() {
    given:
    def responseTemplate = """
      <html>
      <head>
        <ableron-include src="${wiremockAddress}/503"><!-- failed loading include #1 --></ableron-include>
        <title>Foo</title>
        <ableron-include src="${wiremockAddress}/1000ms-delay"><!-- failed loading include #2 --></ableron-include>
      </head>
      <body>
        <ableron-include src="${wiremockAddress}/2000ms-delay"><!-- failed loading include #3 --></ableron-include>
        <ableron-include src="${wiremockAddress}/2100ms-delay"><!-- failed loading include #4 --></ableron-include>
        <ableron-include src="${wiremockAddress}/2200ms-delay"><!-- failed loading include #5 --></ableron-include>
        <ableron-include src="${wiremockAddress}/404"><!-- failed loading include #6 --></ableron-include>
      </body>
      </html>
    """
    wiremockServer.stubFor(get("/503").willReturn(serviceUnavailable()
      .withBody("503")
      .withFixedDelay(2000)
    ))
    wiremockServer.stubFor(get("/1000ms-delay").willReturn(ok()
      .withBody("response-2")
      .withFixedDelay(1000)
    ))
    wiremockServer.stubFor(get("/2000ms-delay").willReturn(ok()
      .withBody("response-3")
      .withFixedDelay(2000)
    ))
    wiremockServer.stubFor(get("/2100ms-delay").willReturn(ok()
      .withBody("response-4")
      .withFixedDelay(2100)
    ))
    wiremockServer.stubFor(get("/2200ms-delay").willReturn(ok()
      .withBody("response-5")
      .withFixedDelay(2200)
    ))
    wiremockServer.stubFor(get("/404").willReturn(notFound()
      .withBody("404")
      .withFixedDelay(2200)
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
        <!-- failed loading include #1 -->
        <title>Foo</title>
        response-2
      </head>
      <body>
        response-3
        response-4
        response-5
        <!-- failed loading include #6 -->
      </body>
      </html>
    """
  }
}
