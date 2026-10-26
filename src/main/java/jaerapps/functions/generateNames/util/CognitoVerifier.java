package jaerapps.functions.generateNames.util;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.jwk.source.JWKSourceBuilder;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.proc.DefaultJWTClaimsVerifier;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;

import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Checks the Cognito ID token the page sends as `Authorization: Bearer <token>`. The user pool is
 * /secretsanta's, in flyte-frontend's AWS stack (email + password, and Google through Cognito). Everything is
 * keyed on the token's email, so it has to be verified: Cognito verifies password sign-ups with a code, and
 * maps Google's own email_verified for Google sign-ins.
 */
public class CognitoVerifier {
    private static final Logger LOGGER = Logger.getLogger(CognitoVerifier.class.getName());

    /** Who's calling: their verified, lower-cased email, and whether they may force pairs. */
    public record Caller(String email, boolean admin) {
    }

    private final DefaultJWTProcessor<SecurityContext> processor;
    private final String adminEmail;
    /** Local dry runs accept the page's mock sign-in tokens ("mock." + base64 JSON claims) instead. */
    private final boolean acceptMockTokens;

    public CognitoVerifier(String userPoolId, String clientId, String adminEmail, boolean acceptMockTokens) {
        this.adminEmail = SantaRun.normalize(adminEmail);
        this.acceptMockTokens = acceptMockTokens;
        if (userPoolId.isEmpty() || clientId.isEmpty()) {
            this.processor = null;
            return;
        }

        String region = userPoolId.substring(0, userPoolId.indexOf('_'));
        String issuer = "https://cognito-idp." + region + ".amazonaws.com/" + userPoolId;
        try {
            JWKSource<SecurityContext> keys = JWKSourceBuilder.create(new URL(issuer + "/.well-known/jwks.json")).retrying(true).build();
            processor = new DefaultJWTProcessor<>();
            processor.setJWSKeySelector(new JWSVerificationKeySelector<>(JWSAlgorithm.RS256, keys));
            processor.setJWTClaimsSetVerifier(new DefaultJWTClaimsVerifier<>(
                    clientId,
                    new JWTClaimsSet.Builder().issuer(issuer).claim("token_use", "id").build(),
                    Set.of("sub", "email", "exp")));
        } catch (Exception e) {
            throw new IllegalStateException("Bad Cognito settings", e);
        }
    }

    /** @return null unless the header holds a valid token with a verified email */
    public Caller verify(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            return null;
        }
        String token = authorizationHeader.substring("Bearer ".length()).trim();

        if (acceptMockTokens && token.startsWith("mock.")) {
            JsonObject claims = new Gson().fromJson(
                    new String(Base64.getDecoder().decode(token.substring("mock.".length())), StandardCharsets.UTF_8), JsonObject.class);
            return caller(claims.get("email").getAsString());
        }
        if (processor == null) {
            return null;
        }

        try {
            JWTClaimsSet claims = processor.process(token, null);
            Object verified = claims.getClaim("email_verified");
            // Cognito sends a boolean for its own users; a mapped Google attribute can arrive as "true".
            if (!Boolean.TRUE.equals(verified) && !"true".equals(verified)) {
                return null;
            }
            return caller(claims.getStringClaim("email"));
        } catch (Exception e) {
            LOGGER.info("Rejected token: " + e.getMessage());
            return null;
        }
    }

    private Caller caller(String email) {
        String normalized = SantaRun.normalize(email);
        return normalized.isEmpty() ? null : new Caller(normalized, normalized.equals(adminEmail));
    }
}
