package se.bjurr.violations.comments.gitlab.lib;

import static java.lang.Integer.MAX_VALUE;
import static se.bjurr.violations.comments.lib.CommentsCreator.createComments;
import static se.bjurr.violations.lib.util.Optional.fromNullable;

import com.github.mustachejava.resolver.DefaultResolver;
import java.io.Reader;
import java.util.List;
import java.util.Scanner;
import org.gitlab.api.AuthMethod;
import org.gitlab.api.TokenType;
import org.slf4j.LoggerFactory;
import se.bjurr.violations.comments.lib.CommentsProvider;
import se.bjurr.violations.comments.lib.ViolationsLogger;
import se.bjurr.violations.lib.model.Violation;
import se.bjurr.violations.lib.util.Optional;
import se.bjurr.violations.lib.util.Utils;

public class ViolationCommentsToGitLabApi {
  private static final String DEFAULT_VIOLATION_TEMPLATE_MUSTACH =
      "default-violation-template-gitlab.mustach";

  public static ViolationCommentsToGitLabApi violationCommentsToGitLabApi() {
    return new ViolationCommentsToGitLabApi();
  }

  private List<Violation> violations;
  private boolean createCommentWithAllSingleFileComments = false;
  private boolean createCommentPerViolation = false;
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
  private String commentTemplate;
  private ViolationsLogger violationsLogger =
      new ViolationsLogger() {
        @Override
        public void log(final String string) {
          LoggerFactory.getLogger(ViolationsLogger.class).info(string);
        }
      };

  public ViolationCommentsToGitLabApi withViolationsLogger(
      final ViolationsLogger violationsLogger) {
    this.violationsLogger = violationsLogger;
    return this;
  }

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

  public ViolationCommentsToGitLabApi setIgnoreCertificateErrors(
      final boolean ignoreCertificateErrors) {
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

  public ViolationCommentsToGitLabApi setCreateCommentPerViolation(
      final boolean createCommentPerViolation) {
    this.createCommentPerViolation = createCommentPerViolation;
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

  /**
   * Gets if a comment per violation should be created.
   *
   * @return <code>true</code> if a comment should be created for each violation, <code>false</code>
   *     otherwise.
   */
  public boolean getCreateCommentPerViolation() {
    return createCommentPerViolation;
  }

  public void toPullRequest() throws Exception {
    if (Utils.isNullOrEmpty(commentTemplate)) {
      commentTemplate = getDefaultTemplate();
    }
    final CommentsProvider commentsProvider = new GitLabCommentsProvider(this);
    createComments(violationsLogger, violations, MAX_VALUE, commentsProvider);
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

  public ViolationCommentsToGitLabApi withCommentTemplate(final String commentTemplate) {
    this.commentTemplate = commentTemplate;
    return this;
  }

  public Optional<String> findCommentTemplate() {
    return fromNullable(commentTemplate);
  }
}
