package io.github.ableron

import spock.lang.Specification

class TestSpec extends Specification {

  def "should run tests"() {
    expect:
    20 == 10 * 2
  }
}
