package io.github.ableron.ableronverify;

import io.github.ableron.Ableron;
import io.github.ableron.AbleronConfig;
import io.github.ableron.TransclusionResult;
import org.springframework.http.MediaType;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.stream.Collectors;

@RestController
public class VerifyController {

  private final AbleronConfig ableronConfig;
  private final Ableron ableron;

  public VerifyController() {
    this.ableronConfig = AbleronConfig.builder().build();
    this.ableron = new Ableron(this.ableronConfig);
  }

  @PostMapping(value = "/verify", produces = MediaType.TEXT_HTML_VALUE)
  public String verify(@RequestBody String content, @RequestHeader MultiValueMap<String, String> headers) {
    var requestHeaders = headers.entrySet()
      .stream()
      .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    var transclusionResult = ableron.resolveIncludes(content, requestHeaders);
    return transclusionResult.getContent();
  }
}
