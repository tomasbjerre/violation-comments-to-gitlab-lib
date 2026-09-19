package se.bjurr.violations.comments.gitlab.lib.client;

public class GitLabResponse {
  private final int statusCode;
  private final String body;

  public GitLabResponse(final int statusCode, final String body) {
    this.statusCode = statusCode;
    this.body = body;
  }

  public int getStatusCode() {
    return this.statusCode;
  }

  public String getBody() {
    return this.body;
  }

  public boolean isSuccessful() {
    return this.statusCode >= 200 && this.statusCode < 300;
  }
}
