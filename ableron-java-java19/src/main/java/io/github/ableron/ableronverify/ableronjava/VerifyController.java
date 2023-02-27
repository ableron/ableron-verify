package io.github.ableron.ableronverify.ableronjava;

import io.github.ableron.Ableron;
import io.github.ableron.AbleronConfig;
import io.github.ableron.TransclusionResult;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class VerifyController {

  private final AbleronConfig ableronConfig;
  private final Ableron ableron;

  public VerifyController() {
    this.ableronConfig = AbleronConfig.builder().build();
    this.ableron = new Ableron(this.ableronConfig);
  }

  @PostMapping(value = "/verify", produces = MediaType.TEXT_HTML_VALUE)
  public String verify(@RequestBody String content) {
    var transclusionResult = ableron.resolveIncludes(content);
    return transclusionResult.getContent();
  }
}
