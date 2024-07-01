package io.github.ableron

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.http.HttpHeader
import com.github.tomakehurst.wiremock.http.HttpHeaders
import org.slf4j.LoggerFactory
import org.testcontainers.Testcontainers
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.output.Slf4jLogConsumer
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
import java.util.zip.GZIPOutputStream

import static com.github.tomakehurst.wiremock.client.WireMock.*
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options

abstract class BaseSpec extends Specification {

  private static final APPLICATION_PORT = 8080

  @Shared
  WireMockServer wiremockServer

  @Shared
  GenericContainer container

  @Shared
  String wiremockAddress

  @Shared
  URI verifyUrl

  @Shared
  def httpClient = HttpClient.newBuilder()
    .version(HttpClient.Version.HTTP_2)
    .build()

  abstract GenericContainer getContainerUnderTest()

  def setupSpec() {
    wiremockServer = new WireMockServer(options().dynamicPort().globalTemplating(true))
    wiremockServer.start()
    wiremockAddress = "http://host.testcontainers.internal:${wiremockServer.port()}"
    Testcontainers.exposeHostPorts(wiremockServer.port())
    container = getContainerUnderTest()
      .withLogConsumer(new Slf4jLogConsumer(LoggerFactory.getLogger(getClass())))
      .withExposedPorts(APPLICATION_PORT)
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
    expect:
    performUiIntegration(content) == content

    where:
    content << [
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

    expect:
    performUiIntegration(content) == expectedResolvedContent

    where:
    content                                                                                | expectedResolvedContent
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
      .withBody("fragment from src")))
    wiremockServer.stubFor(get("/fallback-src-200").willReturn(ok()
      .withBody("fragment from fallback-src")))

    expect:
    performUiIntegration(
      "<ableron-include src=\"${wiremockAddress}/src-500\" fallback-src=\"${wiremockAddress}/fallback-src-200\"/>"
    ) == "fragment from fallback-src"
  }

  def "should resolve include with fallback content if src and fallback-src could not be loaded"() {
    given:
    wiremockServer.stubFor(get("/src-500").willReturn(serverError()
      .withBody("fragment from src")))
    wiremockServer.stubFor(get("/fallback-src-404").willReturn(notFound()
      .withBody("fragment from fallback-src")))

    expect:
    performUiIntegration(
      "<ableron-include src=\"${wiremockAddress}/src-500\" fallback-src=\"${wiremockAddress}/fallback-src-404\">fallback content</ableron-include>"
    ) == "fallback content"
  }

  def "should resolve multiple includes in same response"() {
    given:
    wiremockServer.stubFor(get(urlPathMatching("/echo/.*"))
      .willReturn(ok().withBody("{{request.pathSegments.[1]}}")))

    expect:
    performUiIntegration("""
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
    """) == """
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
    expect:
    performUiIntegration("""
      <ableron-include src="foo-bar"><!-- #1 --></ableron-include>
      <ableron-include src="foo-bar"><!-- #1 --></ableron-include>
      <ableron-include src="foo-bar"><!-- #1 --></ableron-include>
      <ableron-include src="foo-bar"><!-- #2 --></ableron-include>
    """) == """
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
    def resultRandomStringWithIncludeAtTheBeginning = performUiIntegration(include + randomStringWithoutIncludes)
    def resultRandomStringWithIncludeAtTheEnd = performUiIntegration(randomStringWithoutIncludes + include)
    def resultRandomStringWithIncludeAtTheMiddle = performUiIntegration(randomStringWithoutIncludes + include + randomStringWithoutIncludes)

    then:
    resultRandomStringWithIncludeAtTheBeginning == "fragment" + randomStringWithoutIncludes
    resultRandomStringWithIncludeAtTheEnd == randomStringWithoutIncludes + "fragment"
    resultRandomStringWithIncludeAtTheMiddle == randomStringWithoutIncludes + "fragment" + randomStringWithoutIncludes
  }

  def "should set Content-Length header to zero for empty response"() {
    when:
    def response = performUiIntegrationRaw(content)

    then:
    response.headers().firstValue("Content-Length") == Optional.of(expectedResponseContentLength)
    response.body() == expectedResponseBody

    where:
    content                                                | expectedResponseBody | expectedResponseContentLength
    "<ableron-include />"                                  | ""                   | "0"
    "<ableron-include ></ableron-include>"                 | ""                   | "0"
    "<ableron-include _src=\"foo.bar\"></ableron-include>" | ""                   | "0"
    "<ableron-include > </ableron-include>"                | " "                  | "1"
  }

  def "should not crash due to #scenarioName"() {
    expect:
    performUiIntegration(
      "<ableron-include >before</ableron-include>" + content + "<ableron-include >after</ableron-include>"
    ) == "before" + expectedResolvedContent + "after"

    where:
    scenarioName                   | content                                                                        | expectedResolvedContent
    "invalid src url"              | '<ableron-include src=",._">fallback</ableron-include>'                        | "fallback"
    "invalid src timeout"          | '<ableron-include src-timeout-millis="5s">fallback</ableron-include>'          | "fallback"
    "invalid fallback-src timeout" | '<ableron-include fallback-src-timeout-millis="5s">fallback</ableron-include>' | "fallback"
  }

  @Timeout(value = 3, unit = TimeUnit.SECONDS)
  def "should not apply request collapsing"() {
    given:
    def includeSrcPath = randomIncludeSrcPath()
    wiremockServer.stubFor(get(includeSrcPath).willReturn(ok()
      .withBody("fragment")
      .withHeader("Cache-Control", "private, no-cache, no-store, must-revalidate")
      .withFixedDelay(2000)))

    expect:
    performUiIntegration("""
        <ableron-include src="${wiremockAddress}${includeSrcPath}"><!-- 1 --></ableron-include>
        <ableron-include src="${wiremockAddress}${includeSrcPath}"><!-- 2 --></ableron-include>
        <ableron-include src="${wiremockAddress}${includeSrcPath}"><!-- 3 --></ableron-include>
    """) == """
        fragment
        fragment
        fragment
    """
    wiremockServer.verify(3, getRequestedFor(urlEqualTo(includeSrcPath)))
  }

  @Timeout(value = 7, unit = TimeUnit.SECONDS)
  def "should resolve includes in parallel"() {
    given:
    wiremockServer.stubFor(get("/503").willReturn(serviceUnavailable()
      .withBody("503")
      .withFixedDelay(2000)))
    wiremockServer.stubFor(get("/1000ms-delay").willReturn(ok()
      .withBody("fragment-2")
      .withFixedDelay(1000)))
    wiremockServer.stubFor(get("/2000ms-delay").willReturn(ok()
      .withBody("fragment-3")
      .withFixedDelay(2000)))
    wiremockServer.stubFor(get("/2100ms-delay").willReturn(ok()
      .withBody("fragment-4")
      .withFixedDelay(2100)))
    wiremockServer.stubFor(get("/2200ms-delay").willReturn(ok()
      .withBody("fragment-5")
      .withFixedDelay(2200)))
    wiremockServer.stubFor(get("/404").willReturn(notFound()
      .withBody("404")
      .withFixedDelay(2200)))

    expect:
    performUiIntegration("""
      <html>
      <head>
        <ableron-include src="${wiremockAddress}/503"><!-- failed loading fragment #1 --></ableron-include>
        <title>Foo</title>
        <ableron-include src="${wiremockAddress}/1000ms-delay"><!-- failed loading fragment #2 --></ableron-include>
      </head>
      <body>
        <ableron-include src="${wiremockAddress}/2000ms-delay"><!-- failed loading fragment #3 --></ableron-include>
        <ableron-include src="${wiremockAddress}/2100ms-delay"><!-- failed loading fragment #4 --></ableron-include>
        <ableron-include src="${wiremockAddress}/2200ms-delay"><!-- failed loading fragment #5 --></ableron-include>
        <ableron-include src="${wiremockAddress}/404"><!-- failed loading fragment #6 --></ableron-include>
      </body>
      </html>
    """) == """
      <html>
      <head>
        <!-- failed loading fragment #1 -->
        <title>Foo</title>
        fragment-2
      </head>
      <body>
        fragment-3
        fragment-4
        fragment-5
        <!-- failed loading fragment #6 -->
      </body>
      </html>
    """
  }

  def "should not follow redirects when resolving URLs"() {
    given:
    wiremockServer.stubFor(get("/redirect-test").willReturn(temporaryRedirect(wiremockAddress + "/redirect-test-2")))
    wiremockServer.stubFor(get("/redirect-test-2").willReturn(ok()
      .withBody("fragment after redirect")))

    when:
    def result = performUiIntegration(
      "<ableron-include src=\"${wiremockAddress}/redirect-test\"><!-- failed --></ableron-include>")

    then:
    result == "<!-- failed -->"
  }

  def "should return redirect for primary includes"() {
    given:
    wiremockServer.stubFor(get("/redirect-test-3").willReturn(temporaryRedirect("/redirect-test-3")))

    when:
    def result = performUiIntegrationRaw(
      "<ableron-include src=\"${wiremockAddress}/redirect-test-3\" primary><!-- failed --></ableron-include>")

    then:
    result.statusCode() == 302
    result.headers().firstValue("Location").get() == "/redirect-test-3"
    result.body() == ""
  }

  def "should add default primary include response headers to final response"() {
    given:
    wiremockServer.stubFor(get("/primary-include-default-response-headers-to-pass").willReturn(ok()
      .withBody("fragment")
      .withHeader("Location", "/location")
      .withHeader("Refresh", "5")
      .withHeader("Content-Language", "en")
      .withHeader("X-Correlation-ID", "a-b-c-d")))

    when:
    def result = performUiIntegrationRaw(
      "<ableron-include src=\"${wiremockAddress}/primary-include-default-response-headers-to-pass\" primary><!-- failed --></ableron-include>")

    then:
    result.statusCode() == 200
    result.headers().firstValue("Location").get() == "/location"
    result.headers().firstValue("Refresh").get() == "5"
    result.headers().firstValue("Content-Language").get() == "en"
    result.headers().firstValue("X-Correlation-ID").isEmpty()
    result.body() == "fragment"
  }

  def "should favor include tag specific request timeout over global one"() {
    given:
    wiremockServer.stubFor(get("/5000ms-delay").willReturn(ok()
      .withBody("fragment")
      .withHeader("Expires", "0")
      .withFixedDelay(5000)))

    when:
    def result = performUiIntegration(content)

    then:
    result == expectedResolvedContent

    where:
    content                                                                                                    | expectedResolvedContent
    "<ableron-include src=\"${wiremockAddress}/5000ms-delay\"/>"                                               | ""
    "<ableron-include src=\"${wiremockAddress}/5000ms-delay\" src-timeout-millis=\"6000\"/>"                   | "fragment"
    "<ableron-include src=\"${wiremockAddress}/5000ms-delay\" fallback-src-timeout-millis=\"6000\"/>"          | ""
    "<ableron-include fallback-src=\"${wiremockAddress}/5000ms-delay\"/>"                                      | ""
    "<ableron-include fallback-src=\"${wiremockAddress}/5000ms-delay\" src-timeout-millis=\"6000\"/>"          | ""
    "<ableron-include fallback-src=\"${wiremockAddress}/5000ms-delay\" fallback-src-timeout-millis=\"6000\"/>" | "fragment"
  }

  @Unroll
  def "should cache fragment if status code is defined as cacheable in RFC 7231 - Status #responsStatus"() {
    when:
    def includeSrcPath = randomIncludeSrcPath()
    wiremockServer.stubFor(get(includeSrcPath)
      .inScenario(includeSrcPath)
      .whenScenarioStateIs("Started")
      .willReturn(status(responsStatus)
        .withBody(fragmentContent)
        .withHeader("Cache-Control", "max-age=10"))
      .willSetStateTo("1st req completed"))
    wiremockServer.stubFor(get(includeSrcPath)
      .inScenario(includeSrcPath)
      .whenScenarioStateIs("1st req completed")
      .willReturn(ok()
        .withBody("fragment 2nd req")))
    def result1 = performUiIntegration(
      "<ableron-include src=\"${wiremockAddress}${includeSrcPath}\">:(</ableron-include>")
    def result2 = performUiIntegration(
      "<ableron-include src=\"${wiremockAddress}${includeSrcPath}\">:( 2nd req</ableron-include>")

    then:
    result1 == expectedResult1stInclude
    result2 == expectedResult2stInclude

    where:
    responsStatus | fragmentContent | expectedFragmentCached | expectedResult1stInclude | expectedResult2stInclude
    100           | "fragment"      | false                  | ":("                     | "fragment 2nd req"
    200           | "fragment"      | true                   | "fragment"               | "fragment"
    202           | "fragment"      | false                  | ":("                     | "fragment 2nd req"
    203           | "fragment"      | true                   | "fragment"               | "fragment"
    204           | ""              | true                   | ""                       | ""
    205           | "fragment"      | false                  | ":("                     | "fragment 2nd req"
    206           | "fragment"      | true                   | "fragment"               | "fragment"
    // TODO: Testing status code 300 does not work on Java 11 because HttpClient fails with "IOException: Invalid redirection"
    // 300           | "fragment"      | true                   | ":("                     | ":( 2nd req"
    302           | "fragment"      | false                  | ":("                     | "fragment 2nd req"
    400           | "fragment"      | false                  | ":("                     | "fragment 2nd req"
    404           | "fragment"      | true                   | ":("                     | ":( 2nd req"
    405           | "fragment"      | true                   | ":("                     | ":( 2nd req"
    410           | "fragment"      | true                   | ":("                     | ":( 2nd req"
    414           | "fragment"      | true                   | ":("                     | ":( 2nd req"
    500           | "fragment"      | false                  | ":("                     | "fragment 2nd req"
    501           | "fragment"      | true                   | ":("                     | ":( 2nd req"
    502           | "fragment"      | false                  | ":("                     | "fragment 2nd req"
    503           | "fragment"      | false                  | ":("                     | "fragment 2nd req"
    504           | "fragment"      | false                  | ":("                     | "fragment 2nd req"
    505           | "fragment"      | false                  | ":("                     | "fragment 2nd req"
    506           | "fragment"      | false                  | ":("                     | "fragment 2nd req"
    507           | "fragment"      | false                  | ":("                     | "fragment 2nd req"
    508           | "fragment"      | false                  | ":("                     | "fragment 2nd req"
    509           | "fragment"      | false                  | ":("                     | "fragment 2nd req"
    510           | "fragment"      | false                  | ":("                     | "fragment 2nd req"
    511           | "fragment"      | false                  | ":("                     | "fragment 2nd req"
  }

  def "should cache fragment for s-maxage seconds if directive is present"() {
    given:
    def includeSrcPath = randomIncludeSrcPath()
    def content = "<ableron-include src=\"${wiremockAddress}${includeSrcPath}\"/>"
    wiremockServer.stubFor(get(includeSrcPath)
      .inScenario(includeSrcPath)
      .whenScenarioStateIs("Started")
      .willReturn(ok()
        .withBody("fragment 1st req")
        .withHeader("Cache-Control", "max-age=2, s-maxage=6 , public")
        .withHeader("Expires", "Wed, 21 Oct 2015 07:28:00 GMT"))
      .willSetStateTo("1st req completed"))
    wiremockServer.stubFor(get(includeSrcPath)
      .inScenario(includeSrcPath)
      .whenScenarioStateIs("1st req completed")
      .willReturn(ok()
        .withBody("fragment 2nd req"))
      .willSetStateTo("2nd req completed"))

    when:
    def result1 = performUiIntegration(content)
    sleep(4000)
    def result2 = performUiIntegration(content)
    sleep(4000)
    def result3 = performUiIntegration(content)

    then:
    result1 == "fragment 1st req"
    result2 == "fragment 1st req"
    result3 == "fragment 2nd req"
  }

  def "should cache fragment for max-age seconds if directive is present"() {
    given:
    def includeSrcPath = randomIncludeSrcPath()
    def content = "<ableron-include src=\"${wiremockAddress}${includeSrcPath}\"/>"
    wiremockServer.stubFor(get(includeSrcPath)
      .inScenario(includeSrcPath)
      .whenScenarioStateIs("Started")
      .willReturn(ok()
        .withBody("fragment 1st req")
        .withHeader("Cache-Control", "max-age=3")
        .withHeader("Expires", "Wed, 21 Oct 2015 07:28:00 GMT"))
      .willSetStateTo("1st req completed"))
    wiremockServer.stubFor(get(includeSrcPath)
      .inScenario(includeSrcPath)
      .whenScenarioStateIs("1st req completed")
      .willReturn(ok()
        .withBody("fragment 2nd req"))
      .willSetStateTo("2nd req completed"))

    when:
    def result1 = performUiIntegration(content)
    sleep(2000)
    def result2 = performUiIntegration(content)
    sleep(3000)
    def result3 = performUiIntegration(content)

    then:
    result1 == "fragment 1st req"
    result2 == "fragment 1st req"
    result3 == "fragment 2nd req"
  }

  def "should cache fragment for max-age seconds minus Age seconds if directive is present and Age header is set"() {
    given:
    def includeSrcPath = randomIncludeSrcPath()
    def content = "<ableron-include src=\"${wiremockAddress}${includeSrcPath}\"/>"
    wiremockServer.stubFor(get(includeSrcPath)
      .inScenario(includeSrcPath)
      .whenScenarioStateIs("Started")
      .willReturn(ok()
        .withBody("fragment 1st req")
        .withHeader("Cache-Control", "max-age=3600")
        .withHeader("Age", "3597")
        .withHeader("Expires", "Wed, 21 Oct 2015 07:28:00 GMT"))
      .willSetStateTo("1st req completed"))
    wiremockServer.stubFor(get(includeSrcPath)
      .inScenario(includeSrcPath)
      .whenScenarioStateIs("1st req completed")
      .willReturn(ok()
        .withBody("fragment 2nd req"))
      .willSetStateTo("2nd req completed"))

    when:
    def result1 = performUiIntegration(content)
    sleep(2000)
    def result2 = performUiIntegration(content)
    sleep(3000)
    def result3 = performUiIntegration(content)

    then:
    result1 == "fragment 1st req"
    result2 == "fragment 1st req"
    result3 == "fragment 2nd req"
  }

  def "should cache fragment based on Expires header and current time if Cache-Control header and Date header are not present"() {
    given:
    def dateTimeFormatter = DateTimeFormatter.RFC_1123_DATE_TIME
      .withZone(ZoneId.of("GMT"))
    def includeSrcPath = randomIncludeSrcPath()
    def content = "<ableron-include src=\"${wiremockAddress}${includeSrcPath}\"/>"
    wiremockServer.stubFor(get(includeSrcPath)
      .inScenario(includeSrcPath)
      .whenScenarioStateIs("Started")
      .willReturn(ok()
        .withBody("fragment 1st req")
        .withHeader("Cache-Control", "public")
        .withHeader("Expires", dateTimeFormatter.format(Instant.now().plusSeconds(3))))
      .willSetStateTo("1st req completed"))
    wiremockServer.stubFor(get(includeSrcPath)
      .inScenario(includeSrcPath)
      .whenScenarioStateIs("1st req completed")
      .willReturn(ok()
        .withBody("fragment 2nd req"))
      .willSetStateTo("2nd req completed"))

    when:
    def result1 = performUiIntegration(content)
    sleep(1000)
    def result2 = performUiIntegration(content)
    sleep(4000)
    def result3 = performUiIntegration(content)

    then:
    result1 == "fragment 1st req"
    result2 == "fragment 1st req"
    result3 == "fragment 2nd req"
  }

  def "should handle Expires header with value 0"() {
    given:
    def includeSrcPath = randomIncludeSrcPath()
    def content = "<ableron-include src=\"${wiremockAddress}${includeSrcPath}\"/>"
    wiremockServer.stubFor(get(includeSrcPath)
      .inScenario(includeSrcPath)
      .whenScenarioStateIs("Started")
      .willReturn(ok()
        .withBody("fragment 1st req")
        .withHeader("Expires", "0"))
      .willSetStateTo("1st req completed"))
    wiremockServer.stubFor(get(includeSrcPath)
      .inScenario(includeSrcPath)
      .whenScenarioStateIs("1st req completed")
      .willReturn(ok()
        .withBody("fragment 2nd req")))

    when:
    def result1 = performUiIntegration(content)
    def result2 = performUiIntegration(content)

    then:
    result1 == "fragment 1st req"
    result2 == "fragment 2nd req"
  }

  def "should treat http header names as case insensitive"() {
    given:
    def includeSrcPath = randomIncludeSrcPath()
    def content = "<ableron-include src=\"${wiremockAddress}${includeSrcPath}\"/>"
    wiremockServer.stubFor(get(includeSrcPath)
      .inScenario(includeSrcPath)
      .whenScenarioStateIs("Started")
      .willReturn(ok()
        .withBody("fragment 1st req")
        .withHeader("EXpIRes", "0"))
      .willSetStateTo("1st req completed"))
    wiremockServer.stubFor(get(includeSrcPath)
      .inScenario(includeSrcPath)
      .whenScenarioStateIs("1st req completed")
      .willReturn(ok()
        .withBody("fragment 2nd req")))

    when:
    def result1 = performUiIntegration(content)
    def result2 = performUiIntegration(content)

    then:
    result1 == "fragment 1st req"
    result2 == "fragment 2nd req"
  }

  def "should cache fragment based on Expires and Date header if Cache-Control header is not present"() {
    given:
    def includeSrcPath = randomIncludeSrcPath()
    def content = "<ableron-include src=\"${wiremockAddress}${includeSrcPath}\"/>"
    wiremockServer.stubFor(get(includeSrcPath)
      .inScenario(includeSrcPath)
      .whenScenarioStateIs("Started")
      .willReturn(ok()
        .withBody("fragment 1st req")
        .withHeader("Date", "Wed, 12 Oct 2050 07:27:57 GMT")
        .withHeader("Expires", "Wed, 12 Oct 2050 07:28:00 GMT"))
      .willSetStateTo("1st req completed"))
    wiremockServer.stubFor(get(includeSrcPath)
      .inScenario(includeSrcPath)
      .whenScenarioStateIs("1st req completed")
      .willReturn(ok()
        .withBody("fragment 2nd req")))

    when:
    def result1 = performUiIntegration(content)
    sleep(1000)
    def result2 = performUiIntegration(content)
    sleep(3000)
    def result3 = performUiIntegration(content)

    then:
    result1 == "fragment 1st req"
    result2 == "fragment 1st req"
    result3 == "fragment 2nd req"
  }

  def "should not cache fragment if Cache-Control header is set but without max-age directives"() {
    given:
    def includeSrcPath = randomIncludeSrcPath()
    def content = "<ableron-include src=\"${wiremockAddress}${includeSrcPath}\"/>"
    wiremockServer.stubFor(get(includeSrcPath)
      .inScenario(includeSrcPath)
      .whenScenarioStateIs("Started")
      .willReturn(ok()
        .withBody("fragment 1st req")
        .withHeader("Cache-Control", "no-cache,no-store,must-revalidate"))
      .willSetStateTo("1st req completed"))
    wiremockServer.stubFor(get(includeSrcPath)
      .inScenario(includeSrcPath)
      .whenScenarioStateIs("1st req completed")
      .willReturn(ok()
        .withBody("fragment 2nd req")))

    when:
    def result1 = performUiIntegration(content)
    def result2 = performUiIntegration(content)

    then:
    result1 == "fragment 1st req"
    result2 == "fragment 2nd req"
  }

  def "should not crash when cache headers contain invalid values"() {
    when:
    def includeSrcPath = randomIncludeSrcPath()
    wiremockServer.stubFor(get(includeSrcPath).willReturn(ok()
      .withBody("fragment")
      .withHeader(header1Name, header1Value)
      .withHeader(header2Name, header2Value)))
    def result = performUiIntegration(
      "<ableron-include src=\"${wiremockAddress}${includeSrcPath}\"/>")

    then:
    result == "fragment"

    where:
    header1Name     | header1Value                    | header2Name | header2Value
    "Cache-Control" | "s-maxage=not-numeric"          | "X-Dummy"   | "dummy"
    "Cache-Control" | "max-age=not-numeric"           | "X-Dummy"   | "dummy"
    "Cache-Control" | "max-age=3600"                  | "Age"       | "not-numeric"
    "Expires"       | "not-numeric"                   | "X-Dummy"   | "dummy"
    "Expires"       | "Wed, 12 Oct 2050 07:28:00 GMT" | "Date"      | "not-a-date"
  }

  def "should not cache fragment if no expiration time is indicated via response header"() {
    given:
    def includeSrcPath = randomIncludeSrcPath()
    def content = "<ableron-include src=\"${wiremockAddress}${includeSrcPath}\"/>"
    wiremockServer.stubFor(get(includeSrcPath)
      .inScenario(includeSrcPath)
      .whenScenarioStateIs("Started")
      .willReturn(ok()
        .withBody("fragment 1st req"))
      .willSetStateTo("1st req completed"))
    wiremockServer.stubFor(get(includeSrcPath)
      .inScenario(includeSrcPath)
      .whenScenarioStateIs("1st req completed")
      .willReturn(ok()
        .withBody("fragment 2nd req")))

    when:
    def result1 = performUiIntegration(content)
    sleep(2000)
    def result2 = performUiIntegration(content)

    then:
    result1 == "fragment 1st req"
    result2 == "fragment 2nd req"
  }

  def "should cache fragment if valid expiration time is indicated via response header"() {
    given:
    def includeSrcPath = randomIncludeSrcPath()
    def content = "<ableron-include src=\"${wiremockAddress}${includeSrcPath}\"/>"
    wiremockServer.stubFor(get(includeSrcPath)
      .inScenario(includeSrcPath)
      .whenScenarioStateIs("Started")
      .willReturn(ok()
        .withHeader("Cache-Control", "max-age=60")
        .withBody("fragment 1st req"))
      .willSetStateTo("1st req completed"))
    wiremockServer.stubFor(get(includeSrcPath)
      .inScenario(includeSrcPath)
      .whenScenarioStateIs("1st req completed")
      .willReturn(ok()
        .withBody("fragment 2nd req")))

    when:
    def result1 = performUiIntegration(content)
    sleep(2000)
    def result2 = performUiIntegration(content)

    then:
    result1 == "fragment 1st req"
    result2 == "fragment 1st req"
  }

  def "should pass allowed request headers to fragment requests"() {
    given:
    wiremockServer.stubFor(get("/pass-req-headers-01").willReturn(ok()))

    when:
    performUiIntegration(
      "<ableron-include src=\"${wiremockAddress}/pass-req-headers-01\"/>",
      ["X-Correlation-ID": ["pass-req-headers-test"]]
    )

    then:
    wiremockServer.verify(1, getRequestedFor(urlEqualTo("/pass-req-headers-01"))
      .withHeader("X-Correlation-ID", equalTo("pass-req-headers-test")))
  }

  def "should pass allowed request headers to fragment requests treating header names as case insensitive"() {
    given:
    wiremockServer.stubFor(get("/pass-req-headers-02").willReturn(ok()))

    when:
    performUiIntegration(
      "<ableron-include src=\"${wiremockAddress}/pass-req-headers-02\"/>",
      ["x-correlation-ID": ["pass-req-headers-test-case-insensitivity"]]
    )

    then:
    wiremockServer.verify(1, getRequestedFor(urlEqualTo("/pass-req-headers-02"))
      .withHeader("x-correlation-ID", equalTo("pass-req-headers-test-case-insensitivity")))
  }

  def "should not pass non-allowed request headers to fragment requests"() {
    given:
    wiremockServer.stubFor(get("/pass-req-headers-03").willReturn(ok()))

    when:
    performUiIntegration(
      "<ableron-include src=\"${wiremockAddress}/pass-req-headers-03\"/>",
      ["X-Test": ["not allowed to pass this header to fragment requests by default"]]
    )

    then:
    wiremockServer.verify(1, getRequestedFor(urlEqualTo("/pass-req-headers-03"))
      .withoutHeader("X-Test"))
  }

  def "should pass default User-Agent header to fragment requests"() {
    given:
    wiremockServer.stubFor(get("/pass-req-headers-04").willReturn(ok()))

    when:
    performUiIntegration("<ableron-include src=\"${wiremockAddress}/pass-req-headers-04\"/>")

    then:
    wiremockServer.verify(1, getRequestedFor(urlEqualTo("/pass-req-headers-04"))
      .withHeader("User-Agent", matching("^Java-http-client/.+")))
  }

  def "should pass provided User-Agent header to fragment requests by default"() {
    given:
    wiremockServer.stubFor(get("/pass-req-headers-05").willReturn(ok()))

    when:
    performUiIntegration(
      "<ableron-include src=\"${wiremockAddress}/pass-req-headers-05\"/>",
      ["User-Agent": ["pass-through-user-agent"]]
    )

    then:
    wiremockServer.verify(1, getRequestedFor(urlEqualTo("/pass-req-headers-05"))
      .withHeader("User-Agent", equalTo("pass-through-user-agent")))
  }

  def "should pass header with multiple values to fragment requests"() {
    given:
    wiremockServer.stubFor(get("/pass-req-headers-06").willReturn(ok()))

    when:
    performUiIntegration(
      "<ableron-include src=\"${wiremockAddress}/pass-req-headers-06\"/>",
      ["x-request-id": ["Foo", "Bar", "Baz"]]
    )

    then:
    try {
      wiremockServer.verify(1, getRequestedFor(urlEqualTo("/pass-req-headers-06"))
        .withHeader("x-request-id", equalTo("Foo"))
        .withHeader("x-request-id", equalTo("Bar"))
        .withHeader("x-request-id", equalTo("Baz")))
    } catch (Throwable ignore) {
      wiremockServer.verify(1, getRequestedFor(urlEqualTo("/pass-req-headers-06"))
        .withHeader("x-request-id", equalTo("Foo, Bar, Baz")))
    }
  }

  def "should send success status code of primary include"() {
    given:
    wiremockServer.stubFor(get("/primary-test-1").willReturn(status(206)
      .withBody("primary-test-1")))

    when:
    def result = performUiIntegrationRaw("""
      <ableron-include src="${wiremockAddress}/primary-test-1" primary><!-- failed --></ableron-include>
    """)

    then:
    result.statusCode() == 206
    result.body() == """
      primary-test-1
    """
  }

  def "should send success status code of fallback-src of primary include"() {
    given:
    wiremockServer.stubFor(get("/primary-test-2-src").willReturn(status(500)
      .withBody("primary-test-2-src")))
    wiremockServer.stubFor(get("/primary-test-2-fallback-src").willReturn(status(206)
      .withBody("primary-test-2-fallback-src")))

    when:
    def result = performUiIntegrationRaw("""
      <ableron-include
        src="${wiremockAddress}/primary-test-2-src"
        fallback-src="${wiremockAddress}/primary-test-2-fallback-src"
        primary><!-- failed --></ableron-include>
    """)

    then:
    result.statusCode() == 206
    result.body() == """
      primary-test-2-fallback-src
    """
  }

  def "should send error status of errored src in case also fallback-src errored"() {
    given:
    wiremockServer.stubFor(get("/primary-test-3-src").willReturn(status(503)
      .withBody("primary-test-3-src")))
    wiremockServer.stubFor(get("/primary-test-3-fallback-src").willReturn(status(500)
      .withBody("primary-test-3-fallback-src")))

    when:
    def result = performUiIntegrationRaw("""
      <ableron-include
        src="${wiremockAddress}/primary-test-3-src"
        fallback-src="${wiremockAddress}/primary-test-3-fallback-src"
        primary><!-- failed --></ableron-include>
    """)

    then:
    result.statusCode() == 503
    result.body() == """
      primary-test-3-src
    """
  }

  def "should send 4xx status code along fallback content for primary include"() {
    given:
    wiremockServer.stubFor(get("/primary-test-4-header").willReturn(ok()
      .withBody("header")))
    wiremockServer.stubFor(get("/primary-test-4-main").willReturn(notFound()
      .withBody("404 content")))
    wiremockServer.stubFor(get("/primary-test-4-footer").willReturn(ok()
      .withBody("footer")))

    when:
    def result = performUiIntegrationRaw("""
      <ableron-include src="${wiremockAddress}/primary-test-4-header"/>
      <ableron-include src="${wiremockAddress}/primary-test-4-main" primary><!-- 404 not found --></ableron-include>
      <ableron-include src="${wiremockAddress}/primary-test-4-footer"/>
    """)

    then:
    result.statusCode() == 404
    result.body() == """
      header
      404 content
      footer
    """
  }

  def "should ignore fallback content and set fragment status code and body of errored src if primary"() {
    given:
    wiremockServer.stubFor(get("/primary-test-5").willReturn(status(500)
      .withBody("primary-test-5")))

    when:
    def result = performUiIntegrationRaw("""
      <ableron-include src="${wiremockAddress}/primary-test-5" primary><!-- failed --></ableron-include>
    """)

    then:
    result.statusCode() == 500
    result.body() == """
      primary-test-5
    """
  }

  def "should override page expiration time based on resolved fragments"() {
    when:
    def includeSrcPath = randomIncludeSrcPath()
    wiremockServer.stubFor(get(includeSrcPath).willReturn(status(200)
      .withHeaders(new HttpHeaders(headers))
      .withBody("fragment")))
    def result = performUiIntegrationRaw("""
      <ableron-include src="${wiremockAddress}${includeSrcPath}" />
    """)

    then:
    result.statusCode() == 200
    result.headers().firstValue("Cache-Control").get() == expectedResultingCacheControl
    result.body() == """
      fragment
    """

    where:
    headers                                                                                            | expectedResultingCacheControl
    []                                                                                                 | "no-store"
    [new HttpHeader("Cache-Control", "max-age=0")]                                                     | "no-store"
    [new HttpHeader("Cache-Control", "no-cache,no-store,must-revalidate")]                             | "no-store"
    [new HttpHeader("Cache-Control", "s-maxage=not-numeric")]                                          | "no-store"
    [new HttpHeader("Cache-Control", "max-age=not-numeric")]                                           | "no-store"
    [new HttpHeader("Cache-Control", "max-age=3600"), new HttpHeader("Age", "not-numeric")]            | "no-store"
    [new HttpHeader("Expires", "not-a-date")]                                                          | "no-store"
    [new HttpHeader("Expires", "Wed, 12 Oct 2050 07:28:00 GMT"), new HttpHeader("Date", "not-a-date")] | "no-store"
    [new HttpHeader("Cache-Control", "max-age=300")]                                                   | "max-age=300"
    [new HttpHeader("Cache-Control", "max-age=1200")]                                                  | "max-age=600"
  }

  def "should utilize gzip encoding"() {
    given:
    def includeSrcPath = randomIncludeSrcPath()
    wiremockServer.stubFor(get(includeSrcPath)
      .withHeader("Accept-Encoding", containing("gzip"))
      .willReturn(ok()
        .withHeader("Content-Encoding", "gzip")
        .withBody(gzip("response body transferred gzipped"))))

    expect:
    performUiIntegration("""
      <ableron-include src="${wiremockAddress}${includeSrcPath}" />
    """) == """
      response body transferred gzipped
    """
  }

//  def "should handle broken gzip encoding"() {
//    given:
//    def includeSrcPath = randomIncludeSrcPath()
//    wiremockServer.stubFor(get(includeSrcPath)
//      .withHeader("Accept-Encoding", containing("gzip"))
//      .willReturn(ok()
//        .withHeader("Content-Encoding", "gzip")
//        .withBody(Arrays.copyOfRange(gzip("response body transferred gzipped"), 4, 10))))
//
//    expect:
//    performUiIntegration("<ableron-include src=\"${wiremockAddress}${includeSrcPath}\"/>") == ""
//  }

//  def "should handle unknown content encoding"() {
//    given:
//    def includeSrcPath = randomIncludeSrcPath()
//    wiremockServer.stubFor(get(includeSrcPath)
//      .withHeader("Accept-Encoding", containing("gzip"))
//      .willReturn(ok()
//        .withHeader("Content-Encoding", "br")
//        .withBody("plain text body but with wrong content-encoding")))
//
//    expect:
//    performUiIntegration("<ableron-include src=\"${wiremockAddress}${includeSrcPath}\"/>") == ""
//  }

  def "should consider cacheVaryByRequestHeaders"() {
    given:
    def includeSrcPathCacheVary = randomIncludeSrcPath()
    def includeSrcPathCacheNoVary = randomIncludeSrcPath()
    wiremockServer.stubFor(get(includeSrcPathCacheVary)
      .willReturn(ok()
        .withHeader("Cache-Control", "public, s-maxage=30")
        .withBody("Accept-Language: {{request.headers.Accept-Language}}")))
    wiremockServer.stubFor(get(includeSrcPathCacheNoVary)
      .willReturn(ok()
        .withHeader("Cache-Control", "public, s-maxage=30")
        .withBody("User-Agent: {{request.headers.User-Agent}}")))

    expect:
    performUiIntegration(
      "<ableron-include src=\"${wiremockAddress}${includeSrcPathCacheVary}\"/>",
      ["Accept-Language": ["a"]]
    ) == "Accept-Language: a"
    performUiIntegration(
      "<ableron-include src=\"${wiremockAddress}${includeSrcPathCacheVary}\"/>",
      ["Accept-Language": ["b"]]
    ) == "Accept-Language: b"
    performUiIntegration(
      "<ableron-include src=\"${wiremockAddress}${includeSrcPathCacheNoVary}\"/>",
      ["User-Agent": ["a"]]
    ) == "User-Agent: a"
    performUiIntegration(
      "<ableron-include src=\"${wiremockAddress}${includeSrcPathCacheNoVary}\"/>",
      ["User-Agent": ["b"]]
    ) == "User-Agent: a"
  }

  private String performUiIntegration(String content) {
    def response = performUiIntegrationRaw(content)
    assert response.statusCode() == 200
    return response.body()
  }

  private String performUiIntegration(String content, Map<String, List<String>> requestHeaders) {
    def response = performUiIntegrationRaw(content, requestHeaders)
    assert response.statusCode() == 200
    return response.body()
  }

  private HttpResponse<String> performUiIntegrationRaw(String content) {
    return performUiIntegrationRaw(content, [:])
  }

  private HttpResponse<String> performUiIntegrationRaw(String content, Map<String, List<String>> requestHeaders) {
    def requestBuilder = HttpRequest.newBuilder()
      .uri(verifyUrl)
    requestHeaders.each { name, values -> values.each {value -> requestBuilder.header(name, value) } }

    def response = httpClient.send(requestBuilder
      .POST(HttpRequest.BodyPublishers.ofString(content))
      .setHeader("Content-Type", "text/plain")
      .build(), HttpResponse.BodyHandlers.ofString())
    return response
  }

  private String randomIncludeSrcPath() {
    return "/" + UUID.randomUUID().toString()
  }

  private byte[] gzip(String data) {
    def bos = new ByteArrayOutputStream(data.length())
    def gzipOutputStream = new GZIPOutputStream(bos)
    gzipOutputStream.write(data.getBytes())
    gzipOutputStream.close()
    byte[] gzipped = bos.toByteArray()
    bos.close()
    return gzipped
  }
}
