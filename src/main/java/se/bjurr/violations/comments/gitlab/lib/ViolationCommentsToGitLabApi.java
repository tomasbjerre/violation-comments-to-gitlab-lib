package se.bjurr.violations.comments.gitlab.lib;

import static java.util.Optional.ofNullable;
import static se.bjurr.violations.comments.lib.CommentsCreator.createComments;

import com.github.mustachejava.resolver.DefaultResolver;
import java.io.Reader;
import java.util.Optional;
import java.util.Scanner;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.gitlab4j.api.Constants.TokenType;
import se.bjurr.violations.comments.lib.CommentsProvider;
import se.bjurr.violations.lib.ViolationsLogger;
import se.bjurr.violations.lib.model.Violation;
import se.bjurr.violations.lib.util.Utils;

public class ViolationCommentsToGitLabApi {
  private static class ViolationsLoggerJavaLogger implements ViolationsLogger {
    @Override
    public void log(final Level level, final String string) {
      Logger.getLogger(ViolationsLogger.class.getSimpleName()).log(level, string);
    }

    @Override
    public void log(final Level level, final String string, final Throwable t) {
      Logger.getLogger(ViolationsLogger.class.getSimpleName()).log(level, string, t);
    }
  }

  private static final String DEFAULT_VIOLATION_TEMPLATE_MUSTACH =
      "default-violation-template-gitlab.mustach";

  public static ViolationCommentsToGitLabApi violationCommentsToGitLabApi() {
    return new ViolationCommentsToGitLabApi();
  }

  private Set<Violation> violations;
  private boolean createCommentWithAllSingleFileComments = false;
  private boolean createSingleFileComments = false;
  private boolean commentOnlyChangedContent = false;
  private String hostUrl;
  private String apiToken;
  private TokenType tokenType;
  private boolean ignoreCertificateErrors;
  private String projectId;
  private Integer mergeRequestIid;
  private boolean shouldKeepOldComments;
  private boolean shouldSetWIP;
  private String commentTemplate;
  private ViolationsLogger violationsLogger = new ViolationsLoggerJavaLogger();
  private String proxyServer;
  private String proxyUser;
  private String proxyPassword;
  private Integer maxNumberOfViolations;
  private Integer maxCommentSize;
  private boolean shouldCommentOnlyChangedFiles = true;

  public ViolationCommentsToGitLabApi setViolationsLogger(final ViolationsLogger violationsLogger) {
    this.violationsLogger = violationsLogger;
    return this;
  }

  public Set<Violation> getViolations() {
    return this.violations;
  }

  public ViolationCommentsToGitLabApi setViolations(final Set<Violation> violations) {
    this.violations = violations;
    return this;
  }

  public String getHostUrl() {
    return this.hostUrl;
  }

  public ViolationCommentsToGitLabApi setHostUrl(final String hostUrl) {
    this.hostUrl = this.emptyToNull(hostUrl);
    return this;
  }

  public String getApiToken() {
    return this.apiToken;
  }

  public ViolationCommentsToGitLabApi setApiToken(final String apiToken) {
    this.apiToken = this.emptyToNull(apiToken);
    return this;
  }

  public TokenType getTokenType() {
    return this.tokenType;
  }

  public ViolationCommentsToGitLabApi setTokenType(final TokenType tokenType) {
    this.tokenType = tokenType;
    return this;
  }

  public boolean isIgnoreCertificateErrors() {
    return this.ignoreCertificateErrors;
  }

  public ViolationCommentsToGitLabApi setIgnoreCertificateErrors(
      final boolean ignoreCertificateErrors) {
    this.ignoreCertificateErrors = ignoreCertificateErrors;
    return this;
  }

  public String getProjectId() {
    return this.projectId;
  }

  public ViolationCommentsToGitLabApi setProjectId(final String projectId) {
    this.projectId = this.emptyToNull(projectId);
    return this;
  }

  public Integer getMergeRequestIid() {
    return this.mergeRequestIid;
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

  public ViolationCommentsToGitLabApi setCreateSingleFileComments(
      final boolean createSingleFileComments) {
    this.createSingleFileComments = createSingleFileComments;
    return this;
  }

  public ViolationCommentsToGitLabApi setCommentOnlyChangedContent(
      final boolean commentOnlyChangedContent) {
    this.commentOnlyChangedContent = commentOnlyChangedContent;
    return this;
  }

  ViolationCommentsToGitLabApi() {}

  public boolean getCommentOnlyChangedContent() {
    return this.commentOnlyChangedContent;
  }

  public boolean getShouldCommentOnlyChangedFiles() {
    return this.shouldCommentOnlyChangedFiles;
  }

  public boolean getCreateCommentWithAllSingleFileComments() {
    return this.createCommentWithAllSingleFileComments;
  }

  /**
   * Gets if a comment per violation should be created.
   *
   * @return <code>true</code> if a comment should be created for each violation, <code>false</code>
   *     otherwise.
   */
  public boolean getCreateSingleFileComments() {
    return this.createSingleFileComments;
  }

  public void toPullRequest() throws Exception {
    if (Utils.isNullOrEmpty(this.commentTemplate)) {
      this.commentTemplate = this.getDefaultTemplate();
    }
    final CommentsProvider commentsProvider =
        new GitLabCommentsProvider(this.violationsLogger, this);
    createComments(this.violationsLogger, this.violations, commentsProvider);
  }

  private String getDefaultTemplate() {
    try {
      final Reader reader =
          new DefaultResolver() //
              .getReader(DEFAULT_VIOLATION_TEMPLATE_MUSTACH);
      try (Scanner scanner = new Scanner(reader)) {
        scanner.useDelimiter("\\A");
        return scanner.hasNext() ? scanner.next() : "";
      }
    } catch (final Throwable t) {
      throw new RuntimeException(t.getMessage(), t);
    }
  }

  public ViolationCommentsToGitLabApi setShouldKeepOldComments(
      final boolean shouldKeepOldComments) {
    this.shouldKeepOldComments = shouldKeepOldComments;
    return this;
  }

  public ViolationCommentsToGitLabApi withShouldCommentOnlyChangedFiles(
      final boolean shouldCommentOnlyChangedFiles) {
    this.shouldCommentOnlyChangedFiles = shouldCommentOnlyChangedFiles;
    return this;
  }

  public boolean getShouldKeepOldComments() {
    return this.shouldKeepOldComments;
  }

  public ViolationCommentsToGitLabApi setShouldSetWIP(final boolean shouldSetWIP) {
    this.shouldSetWIP = shouldSetWIP;
    return this;
  }

  public boolean getShouldSetWIP() {
    return this.shouldSetWIP;
  }

  public ViolationCommentsToGitLabApi setCommentTemplate(final String commentTemplate) {
    this.commentTemplate = commentTemplate;
    return this;
  }

  public Optional<String> findCommentTemplate() {
    return ofNullable(this.commentTemplate);
  }

  public ViolationCommentsToGitLabApi setProxyUser(final String proxyUser) {
    this.proxyUser = this.emptyToNull(proxyUser);
    return this;
  }

  public ViolationCommentsToGitLabApi setProxyPassword(final String proxyPassword) {
    this.proxyPassword = this.emptyToNull(proxyPassword);
    return this;
  }

  public ViolationCommentsToGitLabApi setProxyServer(final String proxyServer) {
    this.proxyServer = this.emptyToNull(proxyServer);
    return this;
  }

  public Optional<String> findProxyServer() {
    return Optional.ofNullable(this.proxyServer);
  }

  public Optional<String> findProxyPassword() {
    return Optional.ofNullable(this.proxyPassword);
  }

  public Optional<String> findProxyUser() {
    return Optional.ofNullable(this.proxyUser);
  }

  private String emptyToNull(final String str) {
    if (str == null) {
      return null;
    }
    if (str.trim().isEmpty()) {
      return null;
    }
    return str.trim();
  }

  public ViolationCommentsToGitLabApi setMaxCommentSize(final Integer maxCommentSize) {
    this.maxCommentSize = maxCommentSize;
    return this;
  }

  public ViolationCommentsToGitLabApi setMaxNumberOfViolations(final Integer maxNumberOfComments) {
    this.maxNumberOfViolations = maxNumberOfComments;
    return this;
  }

  public Integer getMaxNumberOfViolations() {
    return this.maxNumberOfViolations;
  }

  public Integer getMaxCommentSize() {
    return this.maxCommentSize;
  }
}
