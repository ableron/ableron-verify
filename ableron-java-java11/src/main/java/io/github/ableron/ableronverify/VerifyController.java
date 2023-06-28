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

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@RestController
public class VerifyController {

  private final Ableron ableron;

  public VerifyController() {
    this.ableron = new Ableron(AbleronConfig.builder().build());
  }

  @PostMapping(value = "/verify", produces = MediaType.TEXT_HTML_VALUE)
  public ResponseEntity<String> verify(@RequestBody String content, @RequestHeader MultiValueMap<String, String> requestHeaders) {
    var transclusionResult = ableron.resolveIncludes(content, requestHeaders);
    var responseHeaders = new HttpHeaders(CollectionUtils.toMultiValueMap(transclusionResult.getPrimaryIncludeResponseHeaders()));
    transclusionResult.getContentExpirationTime().ifPresent(contentExpirationTime -> {
      if (contentExpirationTime.isBefore(Instant.now())) {
        responseHeaders.set(HttpHeaders.CACHE_CONTROL, "no-store");
      } else if (contentExpirationTime.isBefore(Instant.now().plusSeconds(600))) {
        responseHeaders.set(HttpHeaders.CACHE_CONTROL, "max-age=" + ChronoUnit.SECONDS.between(Instant.now(), contentExpirationTime));
      } else {
        responseHeaders.set(HttpHeaders.CACHE_CONTROL, "max-age=600");
      }
    });

    return new ResponseEntity<>(
      transclusionResult.getContent(),
      responseHeaders,
      transclusionResult.getPrimaryIncludeStatusCode()
        .map(HttpStatus::valueOf)
        .orElse(HttpStatus.OK)
    );
  }
}
