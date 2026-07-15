package io.rosecloud.iam.bootstrap;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.text.ParseException;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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

  public IssuedAccessToken issueOperatorToken(UUID operatorId, UUID sessionId) {
    return issueToken(
        new JWTClaimsSet.Builder()
            .subject(operatorId.toString())
            .claim("typ", "operator")
            .claim("sid", sessionId.toString()));
  }

  public IssuedAccessToken issueUserToken(UUID userId, UUID sessionId) {
    return issueToken(
        new JWTClaimsSet.Builder()
            .subject(userId.toString())
            .claim("typ", "user")
            .claim("sid", sessionId.toString()));
  }

  public IssuedAccessToken issueTenantToken(
      UUID userId,
      UUID sessionId,
      UUID tenantId,
      UUID membershipId,
      List<String> permissions) {
    JWTClaimsSet.Builder builder =
        new JWTClaimsSet.Builder()
            .subject(userId.toString())
            .claim("typ", "tenant")
            .claim("tenant_id", tenantId.toString())
            .claim("membership_id", membershipId.toString())
            .claim("permissions", permissions);
    if (sessionId != null) {
      builder.claim("sid", sessionId.toString());
    }
    return issueToken(builder);
  }

  public Map<String, Object> jwks() {
    RSAKey jwk =
        new RSAKey.Builder(publicKey).keyID(keyId).algorithm(JWSAlgorithm.RS256).build();
    return Map.of("keys", List.of(jwk.toPublicJWK().toJSONObject()));
  }

  public Optional<VerifiedAccessToken> verifyAccessToken(String serializedToken) {
    try {
      SignedJWT jwt = SignedJWT.parse(serializedToken);
      JWSHeader header = jwt.getHeader();
      if (!JWSAlgorithm.RS256.equals(header.getAlgorithm()) || !keyId.equals(header.getKeyID())) {
        return Optional.empty();
      }
      if (!jwt.verify(new RSASSAVerifier(publicKey))) {
        return Optional.empty();
      }

      JWTClaimsSet claims = jwt.getJWTClaimsSet();
      Instant now = Instant.now(clock);
      if (claims.getExpirationTime() == null || !claims.getExpirationTime().toInstant().isAfter(now)) {
        return Optional.empty();
      }

      String typ = claims.getStringClaim("typ");
      String subject = claims.getSubject();
      if (typ == null || subject == null) {
        return Optional.empty();
      }

      UUID subjectId = UUID.fromString(subject);
      UUID sessionId = parseUuidClaim(claims, "sid");
      return switch (typ) {
        case "operator" ->
            Optional.of(
                new VerifiedAccessToken(
                    typ, subjectId, sessionId, null, null, Collections.emptyList()));
        case "user" ->
            Optional.of(
                new VerifiedAccessToken(
                    typ, subjectId, sessionId, null, null, Collections.emptyList()));
        case "tenant" -> Optional.of(tenantClaims(claims, subjectId));
        default -> Optional.empty();
      };
    } catch (ParseException | JOSEException | IllegalArgumentException exception) {
      return Optional.empty();
    }
  }

  private VerifiedAccessToken tenantClaims(JWTClaimsSet claims, UUID subjectId) throws ParseException {
    String tenantId = claims.getStringClaim("tenant_id");
    String membershipId = claims.getStringClaim("membership_id");
    if (tenantId == null || membershipId == null) {
      throw new ParseException("tenant claims missing", 0);
    }

    List<String> permissions =
        Optional.ofNullable(claims.getStringListClaim("permissions")).orElseGet(List::of);
    return new VerifiedAccessToken(
        "tenant",
        subjectId,
        parseUuidClaim(claims, "sid"),
        UUID.fromString(tenantId),
        UUID.fromString(membershipId),
        List.copyOf(permissions));
  }

  private static UUID parseUuidClaim(JWTClaimsSet claims, String name) throws ParseException {
    String value = claims.getStringClaim(name);
    if (value == null || value.isBlank()) {
      return null;
    }
    return UUID.fromString(value);
  }

  private IssuedAccessToken issueToken(JWTClaimsSet.Builder claimsBuilder) {
    Instant issuedAt = Instant.now(clock);
    Instant expiresAt = issuedAt.plus(properties.jwt().accessTokenTtl());

    JWTClaimsSet claims =
        claimsBuilder
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

  private static KeyPair generateKeyPair() {
    try {
      KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
      generator.initialize(2048);
      return generator.generateKeyPair();
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("RSA unavailable", exception);
    }
  }

  public record IssuedAccessToken(String value, long expiresInSeconds) {}

  public record VerifiedAccessToken(
      String typ,
      UUID subjectId,
      UUID sessionId,
      UUID tenantId,
      UUID membershipId,
      List<String> permissions) {}
}
