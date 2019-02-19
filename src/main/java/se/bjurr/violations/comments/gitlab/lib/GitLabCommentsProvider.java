package se.bjurr.violations.comments.gitlab.lib;

import static java.util.logging.Level.SEVERE;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
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
import org.gitlab4j.api.models.MergeRequest;
import org.gitlab4j.api.models.Note;
import org.gitlab4j.api.models.Position;
import org.gitlab4j.api.models.Project;
import se.bjurr.violations.comments.lib.CommentsProvider;
import se.bjurr.violations.comments.lib.PatchParser;
import se.bjurr.violations.comments.lib.ViolationsLogger;
import se.bjurr.violations.comments.lib.model.ChangedFile;
import se.bjurr.violations.comments.lib.model.Comment;

public class GitLabCommentsProvider implements CommentsProvider {
  private final ViolationCommentsToGitLabApi violationCommentsToGitLabApi;
  private final ViolationsLogger violationsLogger;
  private final GitLabApi gitLabApi;
  private final Project project;
  private final MergeRequest mergeRequest;

  public GitLabCommentsProvider(
      final ViolationsLogger violationsLogger,
      final ViolationCommentsToGitLabApi violationCommentsToGitLabApi) {
    this.violationsLogger = violationsLogger;
    final String hostUrl = violationCommentsToGitLabApi.getHostUrl();
    final String apiToken = violationCommentsToGitLabApi.getApiToken();
    final Map<String, Object> proxyConfig = getProxyConfig(violationCommentsToGitLabApi);
    final TokenType tokenType =
        TokenType.valueOf(violationCommentsToGitLabApi.getTokenType().name());
    final String secretToken = null;
    this.gitLabApi = new GitLabApi(hostUrl, tokenType, apiToken, secretToken, proxyConfig);
    gitLabApi.setIgnoreCertificateErrors(violationCommentsToGitLabApi.isIgnoreCertificateErrors());
    gitLabApi.enableRequestResponseLogging(Level.INFO);
    gitLabApi.withRequestResponseLogging(
        new Logger(GitLabCommentsProvider.class.getName(), null) {
          @Override
          public void log(final LogRecord record) {
            violationsLogger.log(record.getLevel(), record.getMessage());
          }
        },
        Level.FINE);

    final String projectId = violationCommentsToGitLabApi.getProjectId();
    try {
      this.project = gitLabApi.getProjectApi().getProject(projectId);
    } catch (final GitLabApiException e) {
      throw new RuntimeException("Could not get project " + projectId, e);
    }

    final Integer mergeRequestId = violationCommentsToGitLabApi.getMergeRequestIid();
    try {
      mergeRequest =
          gitLabApi.getMergeRequestApi().getMergeRequestChanges(project.getId(), mergeRequestId);
    } catch (final Throwable e) {
      throw new RuntimeException("Could not get MR " + projectId + " " + mergeRequestId, e);
    }

    this.violationCommentsToGitLabApi = violationCommentsToGitLabApi;
  }

  private Map<String, Object> getProxyConfig(final ViolationCommentsToGitLabApi api) {
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
  public void createCommentWithAllSingleFileComments(final String comment) {
    markMergeRequestAsWIP();
    try {
      this.gitLabApi
          .getNotesApi()
          .createMergeRequestNote(project.getId(), this.mergeRequest.getIid(), comment);
    } catch (final Throwable e) {
      violationsLogger.log(SEVERE, "Could create comment " + comment, e);
    }
  }

  /**
   * Set the merge request as "Work in Progress" if configured to do so by the shouldSetWIP flag.
   */
  private void markMergeRequestAsWIP() {
    if (!this.violationCommentsToGitLabApi.getShouldSetWIP()) {
      return;
    }

    final String currentTitle = mergeRequest.getTitle();
    final String startTitle = "WIP: (VIOLATIONS) ";
    if (currentTitle.startsWith(startTitle)) {
      // To avoid setting WIP again on new comments
      return;
    }
    final Integer projectId = this.project.getId();
    final Integer mergeRequestIid = this.mergeRequest.getIid();
    final String targetBranch = null;
    final Integer assigneeId = null;
    final String title = startTitle + currentTitle;
    final String description = null;
    final Constants.StateEvent stateEvent = null;
    final String labels = null;
    final Integer milestoneId = null;
    final Boolean removeSourceBranch = null;
    final Boolean squash = null;
    final Boolean discussionLocked = null;
    final Boolean allowCollaboration = null;
    try {
      mergeRequest.setTitle(title);
      gitLabApi
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
      violationsLogger.log(SEVERE, e.getMessage(), e);
    }
  }

  @Override
  public void createSingleFileComment(
      final ChangedFile file, final Integer newLine, final String content) {
    markMergeRequestAsWIP();
    final Integer projectId = project.getId();
    final String baseSha = mergeRequest.getDiffRefs().getBaseSha();
    final String startSha = mergeRequest.getDiffRefs().getStartSha();
    final String headSha = mergeRequest.getDiffRefs().getHeadSha();
    final String patchString = file.getSpecifics().get(0);
    final String oldPath = file.getSpecifics().get(1);
    final String newPath = file.getSpecifics().get(2);
    final Integer oldLine =
        new PatchParser(patchString) //
            .findOldLine(newLine) //
            .orElse(null);
    try {
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
      gitLabApi
          .getDiscussionsApi()
          .createMergeRequestDiscussion(
              projectId, mergeRequest.getIid(), content, date, positionHash, position);
    } catch (final Throwable e) {
      final String lineSeparator = System.lineSeparator();
      violationsLogger.log(
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
          gitLabApi.getNotesApi().getMergeRequestNotes(project.getId(), mergeRequest.getIid());

      for (final Note note : notes) {
        final String identifier = note.getId() + "";
        final String content = note.getBody();
        final String type = "PR";
        final List<String> specifics = new ArrayList<>();
        final Comment comment = new Comment(identifier, content, type, specifics);
        found.add(comment);
      }
    } catch (final Throwable e) {
      violationsLogger.log(SEVERE, "Could not get comments", e);
    }
    return found;
  }

  @Override
  public List<ChangedFile> getFiles() {
    final List<ChangedFile> changedFiles = new ArrayList<>();
    for (final Diff change : mergeRequest.getChanges()) {
      final String filename = change.getNewPath();
      final List<String> specifics = new ArrayList<>();
      final String patchString = change.getDiff();
      specifics.add(patchString);
      specifics.add(change.getOldPath());
      specifics.add(change.getNewPath());
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
            .deleteMergeRequestNote(project.getId(), mergeRequest.getIid(), noteId);
      } catch (final Throwable e) {
        violationsLogger.log(SEVERE, "Could not delete note " + comment, e);
      }
    }
  }

  @Override
  public boolean shouldComment(final ChangedFile changedFile, final Integer line) {
    final String patchString = changedFile.getSpecifics().get(0);
    if (!violationCommentsToGitLabApi.getCommentOnlyChangedContent()) {
      return true;
    }
    return new PatchParser(patchString) //
        .isLineInDiff(line);
  }

  @Override
  public boolean shouldCreateCommentWithAllSingleFileComments() {
    return violationCommentsToGitLabApi.getCreateCommentWithAllSingleFileComments();
  }

  @Override
  public boolean shouldCreateSingleFileComment() {
    return violationCommentsToGitLabApi.getCreateSingleFileComments();
  }

  @Override
  public boolean shouldKeepOldComments() {
    return violationCommentsToGitLabApi.getShouldKeepOldComments();
  }

  @Override
  public Optional<String> findCommentTemplate() {
    return violationCommentsToGitLabApi.findCommentTemplate();
  }
}
