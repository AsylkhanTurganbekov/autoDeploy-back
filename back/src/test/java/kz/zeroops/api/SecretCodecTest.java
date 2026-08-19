package kz.zeroops.api;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class SecretCodecTest {
  @Test
  void encryptsSecretWithoutPersistingPlainText() {
    String key = Base64.getEncoder().encodeToString(new byte[32]);
    String encrypted = new SecretCodec(key).encrypt("do-not-store-me");

    assertThat(encrypted).doesNotContain("do-not-store-me").contains(".");
  }
}
