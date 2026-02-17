package com.codeops.registry.security;

import com.codeops.registry.config.JwtProperties;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtTokenValidatorTest {

    private static final String VALID_SECRET = "test-secret-key-minimum-32-characters-long-for-hs256-testing";
    private static final String WRONG_SECRET = "wrong-secret-key-minimum-32-characters-long-for-hs256-wrong";

    private JwtTokenValidator validator;

    @BeforeEach
    void setUp() {
        JwtProperties props = new JwtProperties();
        props.setSecret(VALID_SECRET);
        validator = new JwtTokenValidator(props);
        validator.validateSecret();
    }

    private String buildToken(UUID userId, String email, Instant expiry) {
        SecretKey key = Keys.hmacShaKeyFor(VALID_SECRET.getBytes());
        return Jwts.builder()
                .subject(userId.toString())
                .claim("email", email)
                .claim("roles", List.of("MEMBER", "ADMIN"))
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(expiry))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    private String buildTokenWithTeams(UUID userId, String email, List<UUID> teamIds, Map<String, String> teamRoles) {
        SecretKey key = Keys.hmacShaKeyFor(VALID_SECRET.getBytes());
        return Jwts.builder()
                .subject(userId.toString())
                .claim("email", email)
                .claim("roles", List.of("MEMBER"))
                .claim("teamIds", teamIds.stream().map(UUID::toString).toList())
                .claim("teamRoles", teamRoles)
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plus(1, ChronoUnit.HOURS)))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    @Test
    void validateToken_validToken_returnsTrue() {
        UUID userId = UUID.randomUUID();
        String token = buildToken(userId, "test@test.com", Instant.now().plus(1, ChronoUnit.HOURS));
        assertThat(validator.validateToken(token)).isTrue();
    }

    @Test
    void validateToken_expiredToken_returnsFalse() {
        UUID userId = UUID.randomUUID();
        String token = buildToken(userId, "test@test.com", Instant.now().minus(1, ChronoUnit.HOURS));
        assertThat(validator.validateToken(token)).isFalse();
    }

    @Test
    void validateToken_malformedToken_returnsFalse() {
        assertThat(validator.validateToken("not.a.valid.jwt")).isFalse();
    }

    @Test
    void validateToken_wrongSecret_returnsFalse() {
        UUID userId = UUID.randomUUID();
        SecretKey wrongKey = Keys.hmacShaKeyFor(WRONG_SECRET.getBytes());
        String token = Jwts.builder()
                .subject(userId.toString())
                .claim("email", "test@test.com")
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plus(1, ChronoUnit.HOURS)))
                .signWith(wrongKey, Jwts.SIG.HS256)
                .compact();
        assertThat(validator.validateToken(token)).isFalse();
    }

    @Test
    void validateToken_emptyString_returnsFalse() {
        assertThat(validator.validateToken("")).isFalse();
    }

    @Test
    void getUserIdFromToken_returnsCorrectUUID() {
        UUID userId = UUID.randomUUID();
        String token = buildToken(userId, "test@test.com", Instant.now().plus(1, ChronoUnit.HOURS));
        assertThat(validator.getUserIdFromToken(token)).isEqualTo(userId);
    }

    @Test
    void getEmailFromToken_returnsCorrectEmail() {
        UUID userId = UUID.randomUUID();
        String email = "user@example.com";
        String token = buildToken(userId, email, Instant.now().plus(1, ChronoUnit.HOURS));
        assertThat(validator.getEmailFromToken(token)).isEqualTo(email);
    }

    @Test
    void getRolesFromToken_returnsCorrectRoles() {
        UUID userId = UUID.randomUUID();
        String token = buildToken(userId, "test@test.com", Instant.now().plus(1, ChronoUnit.HOURS));
        List<String> roles = validator.getRolesFromToken(token);
        assertThat(roles).containsExactly("MEMBER", "ADMIN");
    }

    @Test
    void getTeamIdsFromToken_returnsTeamIds() {
        UUID userId = UUID.randomUUID();
        UUID teamId1 = UUID.randomUUID();
        UUID teamId2 = UUID.randomUUID();
        String token = buildTokenWithTeams(userId, "test@test.com",
                List.of(teamId1, teamId2), Map.of(teamId1.toString(), "OWNER"));
        List<UUID> teamIds = validator.getTeamIdsFromToken(token);
        assertThat(teamIds).containsExactly(teamId1, teamId2);
    }

    @Test
    void getTeamIdsFromToken_missingClaim_returnsEmptyList() {
        UUID userId = UUID.randomUUID();
        String token = buildToken(userId, "test@test.com", Instant.now().plus(1, ChronoUnit.HOURS));
        assertThat(validator.getTeamIdsFromToken(token)).isEmpty();
    }

    @Test
    void getTeamRolesFromToken_returnsTeamRoles() {
        UUID userId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        String token = buildTokenWithTeams(userId, "test@test.com",
                List.of(teamId), Map.of(teamId.toString(), "OWNER"));
        Map<UUID, String> teamRoles = validator.getTeamRolesFromToken(token);
        assertThat(teamRoles).containsEntry(teamId, "OWNER");
    }

    @Test
    void getTeamRolesFromToken_missingClaim_returnsEmptyMap() {
        UUID userId = UUID.randomUUID();
        String token = buildToken(userId, "test@test.com", Instant.now().plus(1, ChronoUnit.HOURS));
        assertThat(validator.getTeamRolesFromToken(token)).isEmpty();
    }

    @Test
    void validateSecret_secretTooShort_throwsException() {
        JwtProperties shortProps = new JwtProperties();
        shortProps.setSecret("short");
        JwtTokenValidator shortValidator = new JwtTokenValidator(shortProps);
        assertThatThrownBy(shortValidator::validateSecret)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least 32 characters");
    }

    @Test
    void validateSecret_nullSecret_throwsException() {
        JwtProperties nullProps = new JwtProperties();
        nullProps.setSecret(null);
        JwtTokenValidator nullValidator = new JwtTokenValidator(nullProps);
        assertThatThrownBy(nullValidator::validateSecret)
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void validateSecret_blankSecret_throwsException() {
        JwtProperties blankProps = new JwtProperties();
        blankProps.setSecret("   ");
        JwtTokenValidator blankValidator = new JwtTokenValidator(blankProps);
        assertThatThrownBy(blankValidator::validateSecret)
                .isInstanceOf(IllegalStateException.class);
    }
}
