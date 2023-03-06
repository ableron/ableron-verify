package io.github.ableron.ableronverify;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class VerifyController {

  @PostMapping(value = "/verify", produces = MediaType.TEXT_HTML_VALUE)
  public String verify(@RequestBody String content) {
    return content;
  }
}
