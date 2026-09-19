package se.bjurr.violations.comments.gitlab.lib;

/**
 * How {@link ViolationCommentsToGitLabApi#setApiToken(String)} is sent to GitLab: {@link #PRIVATE}
 * and {@link #JOB_TOKEN} are sent as their own header, {@link #ACCESS} and {@link #OAUTH2_ACCESS}
 * as a bearer {@code Authorization} header.
 */
public enum TokenType {
  PRIVATE,
  OAUTH2_ACCESS,
  ACCESS,
  JOB_TOKEN
}
