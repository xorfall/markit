package com.markit.platform.web;

import com.markit.identity.application.AuthExceptions.EmailAlreadyUsedException;
import com.markit.identity.application.AuthExceptions.InvalidCredentialsException;
import com.markit.identity.application.AuthExceptions.InvalidRefreshTokenException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Maps application failures to RFC 7807 problem responses (api-contract §7). */
@RestControllerAdvice
public class ApiExceptionHandler {

  @ExceptionHandler(EmailAlreadyUsedException.class)
  public ProblemDetail handleEmailTaken(EmailAlreadyUsedException ex) {
    return problem(HttpStatus.CONFLICT, "EMAIL_TAKEN", ex.getMessage());
  }

  @ExceptionHandler(InvalidCredentialsException.class)
  public ProblemDetail handleBadCredentials(InvalidCredentialsException ex) {
    return problem(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", ex.getMessage());
  }

  @ExceptionHandler(InvalidRefreshTokenException.class)
  public ProblemDetail handleBadRefresh(InvalidRefreshTokenException ex) {
    return problem(HttpStatus.UNAUTHORIZED, "INVALID_REFRESH_TOKEN", ex.getMessage());
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ProblemDetail handleIllegalArgument(IllegalArgumentException ex) {
    return problem(HttpStatus.BAD_REQUEST, "VALIDATION", ex.getMessage());
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
    ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "VALIDATION", "Request validation failed");
    List<String> errors =
        ex.getBindingResult().getFieldErrors().stream()
            .map(e -> e.getField() + ": " + e.getDefaultMessage())
            .toList();
    problem.setProperty("errors", errors);
    return problem;
  }

  private static ProblemDetail problem(HttpStatus status, String code, String detail) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
    problem.setProperty("code", code);
    return problem;
  }
}
