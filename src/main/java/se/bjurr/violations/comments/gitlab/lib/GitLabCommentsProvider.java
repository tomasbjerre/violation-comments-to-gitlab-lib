package se.bjurr.violations.comments.gitlab.lib;

import static java.util.logging.Level.SEVERE;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.gitlab4j.api.Constants;
import org.gitlab4j.api.Constants.TokenType;
import org.gitlab4j.api.GitLabApi;
import org.gitlab4j.api.GitLabApiException;
import org.gitlab4j.api.ProxyClientConfig;
import org.gitlab4j.api.models.Diff;
import org.gitlab4j.api.models.DiffRef;
import org.gitlab4j.api.models.MergeRequest;
import org.gitlab4j.api.models.Note;
import org.gitlab4j.api.models.Position;
import org.gitlab4j.api.models.Project;
import se.bjurr.violations.comments.lib.CommentsProvider;
import se.bjurr.violations.comments.lib.model.ChangedFile;
import se.bjurr.violations.comments.lib.model.Comment;
import se.bjurr.violations.lib.ViolationsLogger;
import se.bjurr.violations.lib.util.PatchParserUtil;

public class GitLabCommentsProvider implements CommentsProvider {
  private static final String MASK = "HIDDEN";
  static final String START_TITLE = "WIP: (VIOLATIONS)";
  private final ViolationCommentsToGitLabApi api;
  private final ViolationsLogger violationsLogger;
  private final GitLabApi gitLabApi;
  private final Project project;
  private final MergeRequest mergeRequestChanges;
  private final MergeRequest mergeRequest;

  public GitLabCommentsProvider(final ViolationsLogger violationsLogger,
                                final ViolationCommentsToGitLabApi api) {
    this(violationsLogger, api, initGitLabApi(violationsLogger, api));
  }

  protected GitLabCommentsProvider(ViolationsLogger violationsLogger,
                                   ViolationCommentsToGitLabApi api,
                                   GitLabApi gitLabApi,
                                   Project project,
                                   MergeRequest mergeRequestChanges,
                                   MergeRequest mergeRequest) {
    this.api = api;
    this.violationsLogger = violationsLogger;
    this.gitLabApi = gitLabApi;
    this.project = project;
    this.mergeRequestChanges = mergeRequestChanges;
    this.mergeRequest = mergeRequest;
  }

  private GitLabCommentsProvider(ViolationsLogger violationsLogger,
                                 ViolationCommentsToGitLabApi api,
                                 GitLabApi gitLabApi) {
    this(violationsLogger,
        api,
        gitLabApi,
        initProject(api, gitLabApi),
        initMergeRequestChanges(api, gitLabApi),
        initMergeRequest(api, gitLabApi));
  }

  @SuppressFBWarnings({"NP_LOAD_OF_KNOWN_NULL_VALUE", "SIC_INNER_SHOULD_BE_STATIC_ANON"})
  private static GitLabApi initGitLabApi(ViolationsLogger violationsLogger, ViolationCommentsToGitLabApi api) {
    final String hostUrl = api.getHostUrl();
    final String apiToken = api.getApiToken();
    final Map<String, Object> proxyConfig = getProxyConfig(api);
    final TokenType tokenType = TokenType.valueOf(api.getTokenType().name());
    final String secretToken = null;
    GitLabApi gitLabApi = new GitLabApi(hostUrl, tokenType, apiToken, secretToken, proxyConfig);
    gitLabApi.setIgnoreCertificateErrors(api.isIgnoreCertificateErrors());
    gitLabApi.withRequestResponseLogging(
        new Logger(GitLabCommentsProvider.class.getName(), null) {
          @Override
          public void log(final LogRecord record) {
            String masked =
                record
                    .getMessage() //
                    .replace(apiToken, MASK);
            if (api.findProxyPassword().isPresent()) {
              masked = masked.replace(api.findProxyPassword().get(), MASK);
            }
            violationsLogger.log(record.getLevel(), masked);
          }
        },
        Level.INFO);
    return gitLabApi;
  }

  private static Project initProject(ViolationCommentsToGitLabApi api, GitLabApi gitLabApi) {
    final String projectId = api.getProjectId();
    try {
      return gitLabApi.getProjectApi().getProject(projectId);
    } catch (final GitLabApiException e) {
      throw new RuntimeException("Could not get project " + projectId, e);
    }
  }

  private static MergeRequest initMergeRequest(ViolationCommentsToGitLabApi api, GitLabApi gitLabApi) {
    String projectId = api.getProjectId();
    final Integer mergeRequestId = api.getMergeRequestIid();
    try {
      // This will populate diff_refs,
      // https://docs.gitlab.com/ee/api/merge_requests.html#get-single-mr
      return gitLabApi
          .getMergeRequestApi()
          .getMergeRequest(projectId, mergeRequestId);
    } catch (final Throwable e) {
      throw new RuntimeException("Could not get MR " + projectId + " " + mergeRequestId, e);
    }
  }

  private static MergeRequest initMergeRequestChanges(ViolationCommentsToGitLabApi api, GitLabApi gitLabApi) {
    String projectId = api.getProjectId();
    final Integer mergeRequestId = api.getMergeRequestIid();
    try {
      return gitLabApi
          .getMergeRequestApi()
          .getMergeRequestChanges(projectId, mergeRequestId);
    } catch (final Throwable e) {
      throw new RuntimeException("Could not get MR " + projectId + " " + mergeRequestId, e);
    }
  }

  private static Map<String, Object> getProxyConfig(final ViolationCommentsToGitLabApi api) {
    if (api.findProxyServer().isPresent()) {
      if (!api.findProxyUser().isPresent() || !api.findProxyPassword().isPresent()) {
        return ProxyClientConfig.createProxyClientConfig(api.findProxyServer().get());
      }
      if (api.findProxyUser().isPresent() && api.findProxyPassword().isPresent()) {
        return ProxyClientConfig.createProxyClientConfig(
            api.findProxyServer().get(), api.findProxyUser().get(), api.findProxyPassword().get());
      }
    }
    return null;
  }

  @Override
  public void createComment(final String comment) {
    this.markMergeRequestAsWIP();
    try {
      this.gitLabApi
          .getNotesApi()
          .createMergeRequestNote(this.project.getId(), this.mergeRequestChanges.getIid(), comment);
    } catch (final Throwable e) {
      this.violationsLogger.log(SEVERE, "Could create comment " + comment, e);
    }
  }

  /**
   * Set the merge request as "Work in Progress" if configured to do so by the shouldSetWIP flag.
   */
  @SuppressFBWarnings("NP_LOAD_OF_KNOWN_NULL_VALUE")
  private void markMergeRequestAsWIP() {
    if (!this.api.getShouldSetWIP()) {
      return;
    }

    final String currentTitle = this.mergeRequestChanges.getTitle();
    final Optional<String> titleOpt = getTitleWithWipPrefix(currentTitle);
    if (!titleOpt.isPresent()) {
      // To avoid setting WIP again on new comments
      return;
    }
    final Integer projectId = this.project.getId();
    final Integer mergeRequestIid = this.mergeRequestChanges.getIid();
    final String targetBranch = null;
    final Integer assigneeId = null;
    final String title = titleOpt.get();
    final String description = null;
    final Constants.StateEvent stateEvent = null;
    final String labels = null;
    final Integer milestoneId = null;
    final Boolean removeSourceBranch = null;
    final Boolean squash = null;
    final Boolean discussionLocked = null;
    final Boolean allowCollaboration = null;
    try {
      this.mergeRequestChanges.setTitle(title);
      this.gitLabApi
          .getMergeRequestApi()
          .updateMergeRequest(
              projectId,
              mergeRequestIid,
              targetBranch,
              title,
              assigneeId,
              description,
              stateEvent,
              labels,
              milestoneId,
              removeSourceBranch,
              squash,
              discussionLocked,
              allowCollaboration);
    } catch (final Throwable e) {
      this.violationsLogger.log(SEVERE, e.getMessage(), e);
    }
  }

  static Optional<String> getTitleWithWipPrefix(String currentTitle) {
    if (currentTitle.startsWith(START_TITLE)) {
      return Optional.empty();
    }
    if (currentTitle.startsWith("WIP:")) {
      currentTitle = currentTitle.substring(4);
    }
    if (currentTitle.startsWith("WIP")) {
      currentTitle = currentTitle.substring(3);
    }
    final String title = START_TITLE + " " + currentTitle.trim();
    return Optional.of(title.trim());
  }

  @Override
  @SuppressFBWarnings("NP_LOAD_OF_KNOWN_NULL_VALUE")
  public void createSingleFileComment(
      final ChangedFile file, final Integer newLine, final String content) {
    this.markMergeRequestAsWIP();
    Integer projectId = null;
    String baseSha = null;
    String startSha = null;
    String headSha = null;
    String newPath = null;
    final DiffRef diffRefs = this.mergeRequest.getDiffRefs();
    Objects.requireNonNull(
        diffRefs,
        "diffRefs is null for MR with Iid "
            + this.mergeRequest.getIid()
            + " in projectId "
            + this.mergeRequest.getProjectId());
    try {
      projectId = this.project.getId();
      baseSha = diffRefs.getBaseSha();
      startSha = diffRefs.getStartSha();
      headSha = diffRefs.getHeadSha();
      final String patchString = file.getSpecifics().get(0);
      final String oldPath = file.getSpecifics().get(1);
      newPath = file.getSpecifics().get(2);
      final Integer oldLine =
          new PatchParserUtil(patchString) //
              .findOldLine(newLine) //
              .orElse(null);
      final Date date = null;
      final String positionHash = null;
      final Position position = new Position();
      position.setPositionType(Position.PositionType.TEXT);
      position.setBaseSha(baseSha);
      position.setStartSha(startSha);
      position.setHeadSha(headSha);
      position.setNewLine(newLine);
      position.setNewPath(newPath);
      position.setOldLine(oldLine);
      position.setOldPath(oldPath);
      this.gitLabApi
          .getDiscussionsApi()
          .createMergeRequestDiscussion(
              projectId, this.mergeRequestChanges.getIid(), content, date, positionHash, position);
    } catch (final Throwable e) {
      final String lineSeparator = System.lineSeparator();
      this.violationsLogger.log(
          SEVERE,
          "Could not create diff discussion!"
              + lineSeparator
              + "ProjectID: "
              + projectId
              + lineSeparator
              + "SourceSha: "
              + baseSha
              + lineSeparator
              + "HeadSha: "
              + headSha
              + lineSeparator
              + "TargetSha: "
              + startSha
              + lineSeparator
              + "Path "
              + newPath
              + lineSeparator
              + "Violation: "
              + content
              + lineSeparator
              + "NewLine "
              + newLine
              + ", OldLine"
              + newLine,
          e);
    }
  }

  @Override
  public List<Comment> getComments() {
    final List<Comment> found = new ArrayList<>();
    try {

      final List<Note> notes =
          this.gitLabApi
              .getNotesApi()
              .getMergeRequestNotes(this.project.getId(), this.mergeRequestChanges.getIid());

      for (final Note note : notes) {
        final String identifier = note.getId() + "";
        final String content = note.getBody();
        final String type = "PR";
        final List<String> specifics = new ArrayList<>();
        final Comment comment = new Comment(identifier, content, type, specifics);
        found.add(comment);
      }
    } catch (final Throwable e) {
      this.violationsLogger.log(SEVERE, "Could not get comments", e);
    }
    return found;
  }

  @Override
  public List<ChangedFile> getFiles() {
    final List<ChangedFile> changedFiles = new ArrayList<>();
    for (final Diff change : this.mergeRequestChanges.getChanges()) {
      final String filename = change.getNewPath();
      final List<String> specifics = new ArrayList<>();
      final String patchString = change.getDiff();
      specifics.add(patchString);
      specifics.add(change.getOldPath());
      specifics.add(change.getNewPath());
      specifics.add(change.getNewFile().toString());
      specifics.add(change.getRenamedFile().toString());
      specifics.add(change.getDeletedFile().toString());
      final ChangedFile changedFile = new ChangedFile(filename, specifics);
      changedFiles.add(changedFile);
    }

    return changedFiles;
  }

  @Override
  public void removeComments(final List<Comment> comments) {
    for (final Comment comment : comments) {
      try {
        final int noteId = Integer.parseInt(comment.getIdentifier());
        this.gitLabApi
            .getNotesApi()
            .deleteMergeRequestNote(
                this.project.getId(), this.mergeRequestChanges.getIid(), noteId);
      } catch (final Throwable e) {
        this.violationsLogger.log(SEVERE, "Could not delete note " + comment, e);
      }
    }
  }

  @Override
  public boolean shouldComment(final ChangedFile changedFile, final Integer line) {
    final String patchString = changedFile.getSpecifics().get(0);
    if (!this.api.getCommentOnlyChangedContent()) {
      return true;
    }
    if (patchString.isEmpty() && Boolean.parseBoolean(changedFile.getSpecifics().get(3))) {
      return true;
    }
    return new PatchParserUtil(patchString) //
        .isLineInDiff(line);
  }

  @Override
  public boolean shouldCreateCommentWithAllSingleFileComments() {
    return this.api.getCreateCommentWithAllSingleFileComments();
  }

  @Override
  public boolean shouldCreateSingleFileComment() {
    return this.api.getCreateSingleFileComments();
  }

  @Override
  public boolean shouldKeepOldComments() {
    return this.api.getShouldKeepOldComments();
  }

  @Override
  public Optional<String> findCommentTemplate() {
    return this.api.findCommentTemplate();
  }

  @Override
  public Integer getMaxNumberOfViolations() {
    return this.api.getMaxNumberOfViolations();
  }

  @Override
  public Integer getMaxCommentSize() {
    return this.api.getMaxCommentSize();
  }

  @Override
  public boolean shouldCommentOnlyChangedFiles() {
    return this.api.getShouldCommentOnlyChangedFiles();
  }
}
