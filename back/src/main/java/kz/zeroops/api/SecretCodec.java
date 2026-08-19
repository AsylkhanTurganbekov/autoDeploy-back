package kz.zeroops.api;

import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/** Encrypts secret values before persistence. The master key is deliberately never stored in DB. */
@Component
public class SecretCodec {
  private static final int KEY_BYTES = 32;
  private static final int IV_BYTES = 12;
  private final String masterKey;
  private final SecureRandom random = new SecureRandom();

  public SecretCodec(@Value("${SECRETS_MASTER_KEY:}") String masterKey) {
    this.masterKey = masterKey;
  }

  public String encrypt(String plainText) {
    try {
      byte[] decoded = Base64.getDecoder().decode(masterKey);
      if (decoded.length < KEY_BYTES) throw new IllegalArgumentException("key is too short");
      byte[] iv = new byte[IV_BYTES];
      random.nextBytes(iv);
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(Arrays.copyOf(decoded, KEY_BYTES), "AES"), new GCMParameterSpec(128, iv));
      return Base64.getEncoder().encodeToString(ByteBuffer.allocate(IV_BYTES).put(iv).array()) + "." +
          Base64.getEncoder().encodeToString(cipher.doFinal(plainText.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    } catch (Exception exception) {
      throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
          "Secret storage requires SECRETS_MASTER_KEY: a base64-encoded 32-byte key", exception);
    }
  }
  public String decrypt(String encrypted) {
    try {
      byte[] decoded = Base64.getDecoder().decode(masterKey);
      String[] parts = encrypted.split("\\.", 2);
      if (parts.length != 2) throw new IllegalArgumentException("invalid ciphertext");
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(Arrays.copyOf(decoded, KEY_BYTES), "AES"), new GCMParameterSpec(128, Base64.getDecoder().decode(parts[0])));
      return new String(cipher.doFinal(Base64.getDecoder().decode(parts[1])), java.nio.charset.StandardCharsets.UTF_8);
    } catch (Exception exception) {
      throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Stored secret cannot be decrypted", exception);
    }
  }
}
