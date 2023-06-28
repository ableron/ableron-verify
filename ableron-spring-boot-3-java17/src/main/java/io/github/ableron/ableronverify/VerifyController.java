package io.github.ableron.ableronverify;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

@RestController
public class VerifyController {

  @PostMapping(value = "/verify", produces = MediaType.TEXT_HTML_VALUE)
  public ResponseEntity<String> verify(@RequestBody String content) {
    return ResponseEntity.ok()
      .cacheControl(CacheControl.maxAge(Duration.ofSeconds(600)))
      .body(content);
  }
}
