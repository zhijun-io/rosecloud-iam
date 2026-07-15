package io.rosecloud.iam.session;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import io.rosecloud.iam.bootstrap.RosecloudIamProperties;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class JwtIssuer {

  private final RosecloudIamProperties properties;
  private final Clock clock;
  private final RSAPrivateKey privateKey;
  private final RSAPublicKey publicKey;
  private final String keyId;

  JwtIssuer(RosecloudIamProperties properties) {
    this.properties = properties;
    this.clock = Clock.systemUTC();
    KeyPair keyPair = generateKeyPair();
    this.privateKey = (RSAPrivateKey) keyPair.getPrivate();
    this.publicKey = (RSAPublicKey) keyPair.getPublic();
    this.keyId = UUID.randomUUID().toString();
  }

  IssuedAccessToken issueOperatorToken(UUID operatorId) {
    Instant issuedAt = Instant.now(clock);
    Instant expiresAt = issuedAt.plus(properties.jwt().accessTokenTtl());

    JWTClaimsSet claims =
        new JWTClaimsSet.Builder()
            .subject(operatorId.toString())
            .claim("typ", "operator")
            .issueTime(Date.from(issuedAt))
            .expirationTime(Date.from(expiresAt))
            .build();

    SignedJWT jwt =
        new SignedJWT(
            new JWSHeader.Builder(JWSAlgorithm.RS256)
                .type(JOSEObjectType.JWT)
                .keyID(keyId)
                .build(),
            claims);

    try {
      jwt.sign(new RSASSASigner(privateKey));
      return new IssuedAccessToken(jwt.serialize(), properties.jwt().accessTokenTtl().toSeconds());
    } catch (JOSEException exception) {
      throw new IllegalStateException("Unable to sign access token", exception);
    }
  }

  public Map<String, Object> jwks() {
    RSAKey jwk =
        new RSAKey.Builder(publicKey).keyID(keyId).algorithm(JWSAlgorithm.RS256).build();
    return Map.of("keys", List.of(jwk.toPublicJWK().toJSONObject()));
  }

  private static KeyPair generateKeyPair() {
    try {
      KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
      generator.initialize(2048);
      return generator.generateKeyPair();
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("RSA unavailable", exception);
    }
  }

  record IssuedAccessToken(String value, long expiresInSeconds) {}
}
