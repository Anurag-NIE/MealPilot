package com.mealpilot.api.auth;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

  private final UserAccountRepository userAccountRepository;
  private final PasswordEncoder passwordEncoder;
  private final JwtEncoder jwtEncoder;
  private final long ttlSeconds;

  public AuthController(
      UserAccountRepository userAccountRepository,
      PasswordEncoder passwordEncoder,
      JwtEncoder jwtEncoder,
      @Value("${mealpilot.jwt.ttl-seconds:86400}") long ttlSeconds
  ) {
    this.userAccountRepository = userAccountRepository;
    this.passwordEncoder = passwordEncoder;
    this.jwtEncoder = jwtEncoder;
    this.ttlSeconds = ttlSeconds;
  }

    public record RegisterRequest(
      @NotBlank(message = "username is required")
      @Size(min = 3, max = 64, message = "username must be 3-64 characters")
      @Pattern(regexp = "^[A-Za-z0-9._-]+$", message = "username may contain letters, numbers, '.', '_', '-' only")
      String username,

      @NotBlank(message = "password is required")
      @Size(min = 8, max = 128, message = "password must be 8-128 characters")
      String password
    ) {}

    public record LoginRequest(
      @NotBlank(message = "username is required")
      @Size(min = 3, max = 64, message = "username must be 3-64 characters")
      @Pattern(regexp = "^[A-Za-z0-9._-]+$", message = "username may contain letters, numbers, '.', '_', '-' only")
      String username,

      @NotBlank(message = "password is required")
      @Size(min = 8, max = 128, message = "password must be 8-128 characters")
      String password
    ) {}

  public record AuthResponse(String accessToken, String tokenType, long expiresInSeconds) {}

  public record RegisteredUserResponse(String id, String username, Instant createdAt) {}

  @PostMapping("/register")
  @ResponseStatus(HttpStatus.CREATED)
  public Mono<RegisteredUserResponse> register(@Valid @RequestBody RegisterRequest body) {
    String username = body.username().trim();

    return userAccountRepository.findByUsername(username)
        .flatMap(existing -> Mono.<RegisteredUserResponse>error(new ResponseStatusException(HttpStatus.CONFLICT, "username already exists")))
        .switchIfEmpty(Mono.defer(() -> {
          UserAccount user = new UserAccount(
              null,
              username,
              passwordEncoder.encode(body.password()),
              List.of("ROLE_USER"),
              Instant.now()
          );
          return userAccountRepository.save(user)
              .map(saved -> new RegisteredUserResponse(saved.id(), saved.username(), saved.createdAt()));
        }));
  }

  @PostMapping("/login")
  public Mono<AuthResponse> login(@Valid @RequestBody LoginRequest body) {
    String username = body.username().trim();

    return userAccountRepository.findByUsername(username)
        .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid credentials")))
        .flatMap(user -> {
          if (!passwordEncoder.matches(body.password(), user.passwordHash())) {
            return Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid credentials"));
          }

          Instant now = Instant.now();
          Instant expiresAt = now.plus(ttlSeconds, ChronoUnit.SECONDS);

          JwtClaimsSet claims = JwtClaimsSet.builder()
              .issuer("mealpilot-api")
              .issuedAt(now)
              .expiresAt(expiresAt)
              .subject(user.username())
              .claim("roles", user.roles() == null ? List.of() : user.roles())
              .build();

          JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
          String token = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
          return Mono.just(new AuthResponse(token, "Bearer", ttlSeconds));
        });
  }
}
