package com.markit.identity.application;

/** Application-layer failures for the Identity context, mapped to HTTP problems in presentation. */
public final class AuthExceptions {

  private AuthExceptions() {}

  /** Registration with an email that already exists. */
  public static class EmailAlreadyUsedException extends RuntimeException {
    public EmailAlreadyUsedException() {
      super("Email is already registered");
    }
  }

  /** Login failed. Deliberately does not reveal whether the email or the password was wrong. */
  public static class InvalidCredentialsException extends RuntimeException {
    public InvalidCredentialsException() {
      super("Invalid email or password");
    }
  }

  /** Refresh token missing, expired, revoked, or unknown. */
  public static class InvalidRefreshTokenException extends RuntimeException {
    public InvalidRefreshTokenException() {
      super("Invalid or expired refresh token");
    }
  }

  /** Google ID token could not be verified, or its email is unverified. */
  public static class InvalidGoogleTokenException extends RuntimeException {
    public InvalidGoogleTokenException() {
      super("Invalid Google credential");
    }
  }
}
