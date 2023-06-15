package io.github.ableron.ableronverify;

import io.github.ableron.Ableron;
import io.github.ableron.AbleronConfig;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.CollectionUtils;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class VerifyController {

  private final Ableron ableron;

  public VerifyController() {
    this.ableron = new Ableron(AbleronConfig.builder().build());
  }

  @PostMapping(value = "/verify", produces = MediaType.TEXT_HTML_VALUE)
  public ResponseEntity<String> verify(@RequestBody String content, @RequestHeader MultiValueMap<String, String> requestHeaders) {
    var transclusionResult = ableron.resolveIncludes(content, requestHeaders);

    return new ResponseEntity<>(
      transclusionResult.getContent(),
      new HttpHeaders(CollectionUtils.toMultiValueMap(transclusionResult.getPrimaryIncludeResponseHeaders())),
      transclusionResult.getPrimaryIncludeStatusCode()
        .map(HttpStatus::valueOf)
        .orElse(HttpStatus.OK)
    );
  }
}
