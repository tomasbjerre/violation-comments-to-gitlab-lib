package se.bjurr.violations.comments.gitlab.lib;

import static java.lang.Integer.MAX_VALUE;
import static se.bjurr.violations.comments.lib.CommentsCreator.createComments;

import java.util.List;

import org.gitlab.api.AuthMethod;
import org.gitlab.api.TokenType;

import se.bjurr.violations.comments.lib.model.CommentsProvider;
import se.bjurr.violations.lib.model.Violation;

public class ViolationCommentsToGitLabApi {
  public static ViolationCommentsToGitLabApi violationCommentsToGitLabApi() {
    return new ViolationCommentsToGitLabApi();
  }

  private List<Violation> violations;
  private boolean createCommentWithAllSingleFileComments = false;
  private boolean commentOnlyChangedContent = false;
  private String hostUrl;
  private String apiToken;
  private TokenType tokenType;
  private AuthMethod method;
  private boolean ignoreCertificateErrors;
  private String projectId;
  private Integer mergeRequestIid;
  private boolean shouldKeepOldComments;
  private boolean shouldSetWIP;

  public List<Violation> getViolations() {
    return violations;
  }

  public ViolationCommentsToGitLabApi setViolations(final List<Violation> violations) {
    this.violations = violations;
    return this;
  }

  public String getHostUrl() {
    return hostUrl;
  }

  public ViolationCommentsToGitLabApi setHostUrl(final String hostUrl) {
    this.hostUrl = hostUrl;
    return this;
  }

  public String getApiToken() {
    return apiToken;
  }

  public ViolationCommentsToGitLabApi setApiToken(final String apiToken) {
    this.apiToken = apiToken;
    return this;
  }

  public TokenType getTokenType() {
    return tokenType;
  }

  public ViolationCommentsToGitLabApi setTokenType(final TokenType tokenType) {
    this.tokenType = tokenType;
    return this;
  }

  public AuthMethod getMethod() {
    return method;
  }

  public ViolationCommentsToGitLabApi setMethod(final AuthMethod method) {
    this.method = method;
    return this;
  }

  public boolean isIgnoreCertificateErrors() {
    return ignoreCertificateErrors;
  }

  public ViolationCommentsToGitLabApi setIgnoreCertificateErrors(final boolean ignoreCertificateErrors) {
    this.ignoreCertificateErrors = ignoreCertificateErrors;
    return this;
  }

  public String getProjectId() {
    return projectId;
  }

  public ViolationCommentsToGitLabApi setProjectId(final String projectId) {
    this.projectId = projectId;
    return this;
  }

  public Integer getMergeRequestIid() {
    return mergeRequestIid;
  }

  public ViolationCommentsToGitLabApi setMergeRequestIid(final Integer mergeRequestIid) {
    this.mergeRequestIid = mergeRequestIid;
    return this;
  }

  public ViolationCommentsToGitLabApi setCreateCommentWithAllSingleFileComments(
      final boolean createCommentWithAllSingleFileComments) {
    this.createCommentWithAllSingleFileComments = createCommentWithAllSingleFileComments;
    return this;
  }

  public ViolationCommentsToGitLabApi setCommentOnlyChangedContent(
      final boolean commentOnlyChangedContent) {
    this.commentOnlyChangedContent = commentOnlyChangedContent;
    return this;
  }

  private ViolationCommentsToGitLabApi() {}

  public boolean getCommentOnlyChangedContent() {
    return commentOnlyChangedContent;
  }

  public boolean getCreateCommentWithAllSingleFileComments() {
    return createCommentWithAllSingleFileComments;
  }

  public void toPullRequest() throws Exception {
    final CommentsProvider commentsProvider = new GitLabCommentsProvider(this);
    createComments(commentsProvider, violations, MAX_VALUE);
  }

  public ViolationCommentsToGitLabApi setShouldKeepOldComments(final boolean shouldKeepOldComments) {
    this.shouldKeepOldComments = shouldKeepOldComments;
    return this;
  }

  public boolean getShouldKeepOldComments() {
    return shouldKeepOldComments;
  }

  public ViolationCommentsToGitLabApi setShouldSetWIP(final boolean shouldSetWIP) {
    this.shouldSetWIP = shouldSetWIP;
    return this;
  }

  public boolean getShouldSetWIP() {
    return shouldSetWIP;
  }
}
