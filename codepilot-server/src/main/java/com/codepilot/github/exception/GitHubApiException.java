package com.codepilot.github.exception;

public class GitHubApiException extends RuntimeException {

    private final int statusCode;

    public GitHubApiException(String message) {
        super(message);
        this.statusCode = 0;
    }

    public GitHubApiException(String message, int statusCode) {
        super(message);
        this.statusCode = statusCode;
    }

    public GitHubApiException(String message, Throwable cause) {
        super(message, cause);
        this.statusCode = 0;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public boolean isRateLimited() {
        return statusCode == 403 || statusCode == 429;
    }

    public boolean isNotFound() {
        return statusCode == 404;
    }
}
