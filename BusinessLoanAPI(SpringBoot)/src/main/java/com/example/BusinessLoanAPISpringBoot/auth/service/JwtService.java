package com.example.BusinessLoanAPISpringBoot.auth.service;

import com.example.BusinessLoanAPISpringBoot.auth.config.JwtProperties;
import com.example.BusinessLoanAPISpringBoot.auth.model.AppRole;
import com.example.BusinessLoanAPISpringBoot.auth.model.AppUser;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * Creates and validates JWT access tokens.
 */
@Service
public class JwtService {
    private final JwtProperties props;
    private final SecretKey key;

    public JwtService(JwtProperties props) {
        this.props = props;
        this.key = Keys.hmacShaKeyFor(props.secret().getBytes());
    }

    public String issueAccessToken(AppUser user) {
        Instant now = Instant.now();
        Instant exp = now.plusSeconds(props.accessTokenTtlSeconds());

        List<String> roles = user.getRoles().stream().map(AppRole::getName).map(Enum::name).toList();

        return Jwts.builder()
                .issuer(props.issuer())
                .subject(user.getId().toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp))
                .claim("email", user.getEmail())
                .claim("roles", roles)
                .signWith(key)
                .compact();
    }

    public Claims parseAndValidate(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .requireIssuer(props.issuer())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public UUID getUserId(Claims claims) {
        return UUID.fromString(claims.getSubject());
    }
}
