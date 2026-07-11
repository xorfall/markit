package com.markit.bookmarking.application;

/** Application-layer failures for the Bookmarking context, mapped to HTTP problems in presentation. */
public final class BookmarkingExceptions {

  private BookmarkingExceptions() {}

  /**
   * The requested resource does not exist, or is not owned by the current user. The two cases are
   * deliberately indistinguishable (non-enumerating, R-SEC-01 / NFR-SEC-002) — both surface as 404.
   */
  public static class NotFoundException extends RuntimeException {
    public NotFoundException(String message) {
      super(message);
    }
  }

  /** The URL already exists within the target category (FR-BMK-007). */
  public static class DuplicateUrlException extends RuntimeException {
    public DuplicateUrlException() {
      super("This URL already exists in the target category");
    }
  }
}
