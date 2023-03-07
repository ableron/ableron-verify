package io.github.ableron

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.extension.responsetemplating.ResponseTemplateTransformer
import org.testcontainers.Testcontainers
import org.testcontainers.containers.GenericContainer
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Timeout
import spock.lang.Unroll

import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
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
      .withBody("fragment")))

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
      .withBody("response-from-src")))
    wiremockServer.stubFor(get("/fallback-src-200").willReturn(ok()
      .withBody("response-from-fallback-src")))

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
      .withBody("response-from-src")))
    wiremockServer.stubFor(get("/fallback-src-404").willReturn(notFound()
      .withBody("response-from-fallback-src")))

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
      .withTransformers("response-template")))

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
      .withBody("fragment")))

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
      .withHeader("Cache-Control", "max-age=30")
      .withFixedDelay(200)))
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
      .withFixedDelay(200)))
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
      .withFixedDelay(2000)))
    wiremockServer.stubFor(get("/1000ms-delay").willReturn(ok()
      .withBody("response-2")
      .withFixedDelay(1000)))
    wiremockServer.stubFor(get("/2000ms-delay").willReturn(ok()
      .withBody("response-3")
      .withFixedDelay(2000)))
    wiremockServer.stubFor(get("/2100ms-delay").willReturn(ok()
      .withBody("response-4")
      .withFixedDelay(2100)))
    wiremockServer.stubFor(get("/2200ms-delay").willReturn(ok()
      .withBody("response-5")
      .withFixedDelay(2200)))
    wiremockServer.stubFor(get("/404").willReturn(notFound()
      .withBody("404")
      .withFixedDelay(2200)))

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

  def "should follow redirects when resolving URLs"() {
    given:
    wiremockServer.stubFor(get("/redirect-test").willReturn(temporaryRedirect(wiremockAddress + "/redirect-test-2")))
    wiremockServer.stubFor(get("/redirect-test-2").willReturn(temporaryRedirect(wiremockAddress + "/redirect-test-3")))
    wiremockServer.stubFor(get("/redirect-test-3").willReturn(ok()
      .withBody("response after redirect")))

    when:
    def response = httpClient.send(HttpRequest.newBuilder()
      .uri(verifyUrl)
      .POST(HttpRequest.BodyPublishers.ofString("<ableron-include src=\"${wiremockAddress}/redirect-test\"/>"))
      .build(), HttpResponse.BodyHandlers.ofString())

    then:
    response.statusCode() == 200
    response.body() == "response after redirect"
  }

  def "should favor include tag specific request timeout over global one"() {
    given:
    wiremockServer.stubFor(get("/5500ms-delay").willReturn(ok()
      .withBody("response")
      .withHeader("Expires", "0")
      .withFixedDelay(5500)))

    when:
    def response = httpClient.send(HttpRequest.newBuilder()
      .uri(verifyUrl)
      .POST(HttpRequest.BodyPublishers.ofString(include))
      .build(), HttpResponse.BodyHandlers.ofString())

    then:
    response.statusCode() == 200
    response.body() == expectedResolvedInclude

    where:
    include                                                                                                    | expectedResolvedInclude
    "<ableron-include src=\"${wiremockAddress}/5500ms-delay\"/>"                                               | ""
    "<ableron-include src=\"${wiremockAddress}/5500ms-delay\" src-timeout-millis=\"6000\"/>"                   | "response"
    "<ableron-include src=\"${wiremockAddress}/5500ms-delay\" fallback-src-timeout-millis=\"6000\"/>"          | ""
    "<ableron-include fallback-src=\"${wiremockAddress}/5500ms-delay\"/>"                                      | ""
    "<ableron-include fallback-src=\"${wiremockAddress}/5500ms-delay\" src-timeout-millis=\"6000\"/>"          | ""
    "<ableron-include fallback-src=\"${wiremockAddress}/5500ms-delay\" fallback-src-timeout-millis=\"6000\"/>" | "response"
  }

  @Unroll
  def "should cache HTTP response if status code is defined as cacheable in RFC 7231 - Status #responsStatus"() {
    when:
    def includeSrcPath = "/test-caching-" + UUID.randomUUID().toString()
    wiremockServer.stubFor(get(includeSrcPath)
      .inScenario("response cache test")
      .whenScenarioStateIs("Started")
      .willReturn(status(responsStatus)
        .withBody(responseBody)
        .withHeader("Cache-Control", "max-age=10"))
      .willSetStateTo("1st req completed"))
    wiremockServer.stubFor(get(includeSrcPath)
      .inScenario("response cache test")
      .whenScenarioStateIs("1st req completed")
      .willReturn(ok()
        .withBody("response 2nd req")))
    def response1 = httpClient.send(HttpRequest.newBuilder()
      .uri(verifyUrl)
      .POST(HttpRequest.BodyPublishers.ofString(
        "<ableron-include src=\"${wiremockAddress}${includeSrcPath}\">:(</ableron-include>"))
      .build(), HttpResponse.BodyHandlers.ofString())
    def response2 = httpClient.send(HttpRequest.newBuilder()
      .uri(verifyUrl)
      .POST(HttpRequest.BodyPublishers.ofString(
        "<ableron-include src=\"${wiremockAddress}${includeSrcPath}\">:( 2nd req</ableron-include>"))
      .build(), HttpResponse.BodyHandlers.ofString())

    then:
    response1.statusCode() == 200
    response1.body() == expectedResult1stInclude
    response2.statusCode() == 200
    response2.body() == expectedResult2stInclude

    where:
    responsStatus | responseBody | expectedResponseCached | expectedResult1stInclude | expectedResult2stInclude
    100           | "response"   | false                  | ":("                     | "response 2nd req"
    200           | "response"   | true                   | "response"               | "response"
    202           | "response"   | false                  | ":("                     | "response 2nd req"
    203           | "response"   | true                   | "response"               | "response"
    204           | ""           | true                   | ""                       | ""
    205           | "response"   | false                  | ":("                     | "response 2nd req"
    206           | "response"   | true                   | "response"               | "response"
    // TODO: Testing status code 300 does not work on Java 11 because HttpClient fails with "IOException: Invalid redirection"
    // 300           | "response"   | true                   | ":("                     | "fallback 2nd req"
    302           | "response"   | false                  | ":("                     | "response 2nd req"
    400           | "response"   | false                  | ":("                     | "response 2nd req"
    404           | "response"   | true                   | ":("                     | ":( 2nd req"
    405           | "response"   | true                   | ":("                     | ":( 2nd req"
    410           | "response"   | true                   | ":("                     | ":( 2nd req"
    414           | "response"   | true                   | ":("                     | ":( 2nd req"
    500           | "response"   | false                  | ":("                     | "response 2nd req"
    501           | "response"   | true                   | ":("                     | ":( 2nd req"
    502           | "response"   | false                  | ":("                     | "response 2nd req"
    503           | "response"   | false                  | ":("                     | "response 2nd req"
    504           | "response"   | false                  | ":("                     | "response 2nd req"
    505           | "response"   | false                  | ":("                     | "response 2nd req"
    506           | "response"   | false                  | ":("                     | "response 2nd req"
    507           | "response"   | false                  | ":("                     | "response 2nd req"
    508           | "response"   | false                  | ":("                     | "response 2nd req"
    509           | "response"   | false                  | ":("                     | "response 2nd req"
    510           | "response"   | false                  | ":("                     | "response 2nd req"
    511           | "response"   | false                  | ":("                     | "response 2nd req"
  }

  def "should cache response for s-maxage seconds if directive is present"() {
    given:
    def include = "<ableron-include src=\"${wiremockAddress}/test-s-maxage\"/>"
    wiremockServer.stubFor(get("/test-s-maxage")
      .inScenario("s-maxage test")
      .whenScenarioStateIs("Started")
      .willReturn(ok()
        .withBody("response 1st req")
        .withHeader("Cache-Control", "max-age=2, s-maxage=4 , public")
        .withHeader("Expires", "Wed, 21 Oct 2015 07:28:00 GMT"))
      .willSetStateTo("1st req completed"))
    wiremockServer.stubFor(get("/test-s-maxage")
      .inScenario("s-maxage test")
      .whenScenarioStateIs("1st req completed")
      .willReturn(ok()
        .withBody("response 2nd req"))
      .willSetStateTo("2nd req completed"))

    when:
    def responseInitial = httpClient.send(HttpRequest.newBuilder()
      .uri(verifyUrl)
      .POST(HttpRequest.BodyPublishers.ofString(include))
      .build(), HttpResponse.BodyHandlers.ofString())
    sleep(3000)
    def responseAfter3Seconds = httpClient.send(HttpRequest.newBuilder()
      .uri(verifyUrl)
      .POST(HttpRequest.BodyPublishers.ofString(include))
      .build(), HttpResponse.BodyHandlers.ofString())
    sleep(2000)
    def responseAfter5Seconds = httpClient.send(HttpRequest.newBuilder()
      .uri(verifyUrl)
      .POST(HttpRequest.BodyPublishers.ofString(include))
      .build(), HttpResponse.BodyHandlers.ofString())

    then:
    responseInitial.statusCode() == 200
    responseInitial.body() == "response 1st req"
    responseAfter3Seconds.statusCode() == 200
    responseAfter3Seconds.body() == "response 1st req"
    responseAfter5Seconds.statusCode() == 200
    responseAfter5Seconds.body() == "response 2nd req"
  }

  def "should cache response for max-age seconds if directive is present"() {
    given:
    def include = "<ableron-include src=\"${wiremockAddress}/test-max-age\"/>"
    wiremockServer.stubFor(get("/test-max-age")
      .inScenario("max-age test")
      .whenScenarioStateIs("Started")
      .willReturn(ok()
        .withBody("response 1st req")
        .withHeader("Cache-Control", "max-age=3")
        .withHeader("Expires", "Wed, 21 Oct 2015 07:28:00 GMT"))
      .willSetStateTo("1st req completed"))
    wiremockServer.stubFor(get("/test-max-age")
      .inScenario("max-age test")
      .whenScenarioStateIs("1st req completed")
      .willReturn(ok()
        .withBody("response 2nd req"))
      .willSetStateTo("2nd req completed"))

    when:
    def responseInitial = httpClient.send(HttpRequest.newBuilder()
      .uri(verifyUrl)
      .POST(HttpRequest.BodyPublishers.ofString(include))
      .build(), HttpResponse.BodyHandlers.ofString())
    sleep(2000)
    def responseAfter2Seconds = httpClient.send(HttpRequest.newBuilder()
      .uri(verifyUrl)
      .POST(HttpRequest.BodyPublishers.ofString(include))
      .build(), HttpResponse.BodyHandlers.ofString())
    sleep(2000)
    def responseAfter4Seconds = httpClient.send(HttpRequest.newBuilder()
      .uri(verifyUrl)
      .POST(HttpRequest.BodyPublishers.ofString(include))
      .build(), HttpResponse.BodyHandlers.ofString())

    then:
    responseInitial.statusCode() == 200
    responseInitial.body() == "response 1st req"
    responseAfter2Seconds.statusCode() == 200
    responseAfter2Seconds.body() == "response 1st req"
    responseAfter4Seconds.statusCode() == 200
    responseAfter4Seconds.body() == "response 2nd req"
  }

  def "should cache response for max-age seconds minus Age seconds if directive is present and Age header is set"() {
    given:
    def include = "<ableron-include src=\"${wiremockAddress}/test-max-age-and-age\"/>"
    wiremockServer.stubFor(get("/test-max-age-and-age")
      .inScenario("max-age and age test")
      .whenScenarioStateIs("Started")
      .willReturn(ok()
        .withBody("response 1st req")
        .withHeader("Cache-Control", "max-age=3600")
        .withHeader("Age", "3597")
        .withHeader("Expires", "Wed, 21 Oct 2015 07:28:00 GMT"))
      .willSetStateTo("1st req completed"))
    wiremockServer.stubFor(get("/test-max-age-and-age")
      .inScenario("max-age and age test")
      .whenScenarioStateIs("1st req completed")
      .willReturn(ok()
        .withBody("response 2nd req"))
      .willSetStateTo("2nd req completed"))

    when:
    def responseInitial = httpClient.send(HttpRequest.newBuilder()
      .uri(verifyUrl)
      .POST(HttpRequest.BodyPublishers.ofString(include))
      .build(), HttpResponse.BodyHandlers.ofString())
    sleep(2000)
    def responseAfter2Seconds = httpClient.send(HttpRequest.newBuilder()
      .uri(verifyUrl)
      .POST(HttpRequest.BodyPublishers.ofString(include))
      .build(), HttpResponse.BodyHandlers.ofString())
    sleep(2000)
    def responseAfter4Seconds = httpClient.send(HttpRequest.newBuilder()
      .uri(verifyUrl)
      .POST(HttpRequest.BodyPublishers.ofString(include))
      .build(), HttpResponse.BodyHandlers.ofString())

    then:
    responseInitial.statusCode() == 200
    responseInitial.body() == "response 1st req"
    responseAfter2Seconds.statusCode() == 200
    responseAfter2Seconds.body() == "response 1st req"
    responseAfter4Seconds.statusCode() == 200
    responseAfter4Seconds.body() == "response 2nd req"
  }

  def "should cache response based on Expires header and current time if Cache-Control header and Date header are not present"() {
    given:
    def dateTimeFormatter = DateTimeFormatter.RFC_1123_DATE_TIME
      .withZone(ZoneId.of("GMT"))
    def include = "<ableron-include src=\"${wiremockAddress}/test-expires-header\"/>"
    wiremockServer.stubFor(get("/test-expires-header")
      .inScenario("expires header test")
      .whenScenarioStateIs("Started")
      .willReturn(ok()
        .withBody("response 1st req")
        .withHeader("Cache-Control", "public")
        .withHeader("Expires", dateTimeFormatter.format(Instant.now().plusSeconds(3))))
      .willSetStateTo("1st req completed"))
    wiremockServer.stubFor(get("/test-expires-header")
      .inScenario("expires header test")
      .whenScenarioStateIs("1st req completed")
      .willReturn(ok()
        .withBody("response 2nd req"))
      .willSetStateTo("2nd req completed"))

    when:
    def responseInitial = httpClient.send(HttpRequest.newBuilder()
      .uri(verifyUrl)
      .POST(HttpRequest.BodyPublishers.ofString(include))
      .build(), HttpResponse.BodyHandlers.ofString())
    sleep(2000)
    def responseAfter2Seconds = httpClient.send(HttpRequest.newBuilder()
      .uri(verifyUrl)
      .POST(HttpRequest.BodyPublishers.ofString(include))
      .build(), HttpResponse.BodyHandlers.ofString())
    sleep(2000)
    def responseAfter4Seconds = httpClient.send(HttpRequest.newBuilder()
      .uri(verifyUrl)
      .POST(HttpRequest.BodyPublishers.ofString(include))
      .build(), HttpResponse.BodyHandlers.ofString())

    then:
    responseInitial.statusCode() == 200
    responseInitial.body() == "response 1st req"
    responseAfter2Seconds.statusCode() == 200
    responseAfter2Seconds.body() == "response 1st req"
    responseAfter4Seconds.statusCode() == 200
    responseAfter4Seconds.body() == "response 2nd req"
  }

  def "should handle Expires header with value 0"() {
    given:
    def include = "<ableron-include src=\"${wiremockAddress}/test-expires-header-0\"/>"
    wiremockServer.stubFor(get("/test-expires-header-0")
      .inScenario("expires 0 header test")
      .whenScenarioStateIs("Started")
      .willReturn(ok()
        .withBody("response 1st req")
        .withHeader("Expires", "0"))
      .willSetStateTo("1st req completed"))
    wiremockServer.stubFor(get("/test-expires-header-0")
      .inScenario("expires 0 header test")
      .whenScenarioStateIs("1st req completed")
      .willReturn(ok()
        .withBody("response 2nd req")))

    when:
    def responseReq1 = httpClient.send(HttpRequest.newBuilder()
      .uri(verifyUrl)
      .POST(HttpRequest.BodyPublishers.ofString(include))
      .build(), HttpResponse.BodyHandlers.ofString())
    def responseReq2 = httpClient.send(HttpRequest.newBuilder()
      .uri(verifyUrl)
      .POST(HttpRequest.BodyPublishers.ofString(include))
      .build(), HttpResponse.BodyHandlers.ofString())

    then:
    responseReq1.statusCode() == 200
    responseReq1.body() == "response 1st req"
    responseReq2.statusCode() == 200
    responseReq2.body() == "response 2nd req"
  }

  def "should treat http header names as case insensitive"() {
    given:
    def include = "<ableron-include src=\"${wiremockAddress}/test-case-insensitive-header-names\"/>"
    wiremockServer.stubFor(get("/test-case-insensitive-header-names")
      .inScenario("case insensitive header names test")
      .whenScenarioStateIs("Started")
      .willReturn(ok()
        .withBody("response 1st req")
        .withHeader("EXpIRes", "0"))
      .willSetStateTo("1st req completed"))
    wiremockServer.stubFor(get("/test-case-insensitive-header-names")
      .inScenario("case insensitive header names test")
      .whenScenarioStateIs("1st req completed")
      .willReturn(ok()
        .withBody("response 2nd req")))

    when:
    def responseReq1 = httpClient.send(HttpRequest.newBuilder()
      .uri(verifyUrl)
      .POST(HttpRequest.BodyPublishers.ofString(include))
      .build(), HttpResponse.BodyHandlers.ofString())
    def responseReq2 = httpClient.send(HttpRequest.newBuilder()
      .uri(verifyUrl)
      .POST(HttpRequest.BodyPublishers.ofString(include))
      .build(), HttpResponse.BodyHandlers.ofString())

    then:
    responseReq1.statusCode() == 200
    responseReq1.body() == "response 1st req"
    responseReq2.statusCode() == 200
    responseReq2.body() == "response 2nd req"
  }
}
