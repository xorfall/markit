package com.markit.identity.presentation;

import com.markit.identity.application.LoginService;
import com.markit.identity.application.LogoutService;
import com.markit.identity.application.RefreshSessionService;
import com.markit.identity.application.RegisterUserService;
import com.markit.identity.application.SessionTokens;
import com.markit.identity.presentation.AuthDtos.LoginRequest;
import com.markit.identity.presentation.AuthDtos.LogoutRequest;
import com.markit.identity.presentation.AuthDtos.RefreshRequest;
import com.markit.identity.presentation.AuthDtos.RegisterRequest;
import com.markit.identity.presentation.AuthDtos.TokenResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Authentication endpoints (FR-IDN-001/002/004). */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

  private final RegisterUserService registerUser;
  private final LoginService login;
  private final RefreshSessionService refreshSession;
  private final LogoutService logout;

  public AuthController(
      RegisterUserService registerUser,
      LoginService login,
      RefreshSessionService refreshSession,
      LogoutService logout) {
    this.registerUser = registerUser;
    this.login = login;
    this.refreshSession = refreshSession;
    this.logout = logout;
  }

  @PostMapping("/register")
  @ResponseStatus(HttpStatus.CREATED)
  public TokenResponse register(@Valid @RequestBody RegisterRequest request) {
    return toResponse(registerUser.register(request.email(), request.password()));
  }

  @PostMapping("/login")
  public TokenResponse login(@Valid @RequestBody LoginRequest request) {
    return toResponse(login.login(request.email(), request.password()));
  }

  @PostMapping("/refresh")
  public TokenResponse refresh(@Valid @RequestBody RefreshRequest request) {
    return toResponse(refreshSession.refresh(request.refreshToken()));
  }

  @PostMapping("/logout")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void logout(@Valid @RequestBody LogoutRequest request) {
    logout.logout(request.refreshToken());
  }

  private static TokenResponse toResponse(SessionTokens tokens) {
    return new TokenResponse(
        tokens.accessToken(),
        tokens.refreshToken(),
        tokens.tokenType(),
        tokens.expiresInSeconds());
  }
}
