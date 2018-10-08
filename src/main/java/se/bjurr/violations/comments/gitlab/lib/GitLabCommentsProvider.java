package se.bjurr.violations.comments.gitlab.lib;

import static java.util.logging.Level.INFO;
import static java.util.logging.Level.SEVERE;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.gitlab.api.AuthMethod;
import org.gitlab.api.GitlabAPI;
import org.gitlab.api.TokenType;
import org.gitlab.api.models.GitlabCommitDiff;
import org.gitlab.api.models.GitlabMergeRequest;
import org.gitlab.api.models.GitlabNote;
import org.gitlab.api.models.GitlabProject;
import se.bjurr.violations.comments.lib.CommentsProvider;
import se.bjurr.violations.comments.lib.PatchParser;
import se.bjurr.violations.comments.lib.ViolationsLogger;
import se.bjurr.violations.comments.lib.model.ChangedFile;
import se.bjurr.violations.comments.lib.model.Comment;

public class GitLabCommentsProvider implements CommentsProvider {
  private final ViolationCommentsToGitLabApi violationCommentsToGitLabApi;

  private final GitlabAPI gitlabApi;
  private final ViolationsLogger violationsLogger;

  private GitlabProject project;

  private GitlabMergeRequest mergeRequest;

  public GitLabCommentsProvider(
      ViolationsLogger violationsLogger,
      final ViolationCommentsToGitLabApi violationCommentsToGitLabApi) {
    this.violationsLogger = violationsLogger;
    final String hostUrl = violationCommentsToGitLabApi.getHostUrl();
    final String apiToken = violationCommentsToGitLabApi.getApiToken();
    final TokenType tokenType = violationCommentsToGitLabApi.getTokenType();
    final AuthMethod method = violationCommentsToGitLabApi.getMethod();
    gitlabApi = GitlabAPI.connect(hostUrl, apiToken, tokenType, method);

    final boolean ignoreCertificateErrors =
        violationCommentsToGitLabApi.isIgnoreCertificateErrors();
    gitlabApi.ignoreCertificateErrors(ignoreCertificateErrors);

    final String projectId = violationCommentsToGitLabApi.getProjectId();
    try {
      project = gitlabApi.getProject(projectId);
    } catch (final Throwable e) {
      throw new RuntimeException("Could not get project " + projectId, e);
    }

    final Integer mergeRequestId = violationCommentsToGitLabApi.getMergeRequestIid();
    try {
      mergeRequest = gitlabApi.getMergeRequestChanges(project.getId(), mergeRequestId);
    } catch (final Throwable e) {
      throw new RuntimeException("Could not get MR " + projectId + " " + mergeRequestId, e);
    }

    this.violationCommentsToGitLabApi = violationCommentsToGitLabApi;
  }

  @Override
  public void createCommentWithAllSingleFileComments(final String comment) {
    markMergeRequestAsWIP();
    try {
      gitlabApi.createNote(mergeRequest, comment);
    } catch (final Throwable e) {
      violationsLogger.log(SEVERE, "Could create comment " + comment, e);
    }
  }

  /**
   * Set the {@link GitlabMergeRequest} as "Work in Progress" if configured to do so by the
   * shouldSetWIP flag.
   */
  private void markMergeRequestAsWIP() {
    if (!this.violationCommentsToGitLabApi.getShouldSetWIP()) {
      return;
    }
    final String currentTitle = mergeRequest.getTitle();
    if (currentTitle.startsWith("WIP:")) {
      return;
    }
    final Serializable projectId = violationCommentsToGitLabApi.getProjectId();
    final Integer mergeRequestIid = violationCommentsToGitLabApi.getMergeRequestIid();
    final String targetBranch = null;
    final Integer assigneeId = null;
    final String title = "WIP: >>> CONTAINS VIOLATIONS! <<< " + currentTitle;
    final String description = null;
    final String stateEvent = null;
    final String labels = null;
    try {
      mergeRequest.setTitle(title); // To avoid setting WIP again on new comments
      gitlabApi.updateMergeRequest(
          projectId,
          mergeRequestIid,
          targetBranch,
          assigneeId,
          title,
          description,
          stateEvent,
          labels);
    } catch (final Throwable e) {
      violationsLogger.log(SEVERE, e.getMessage(), e);
    }
  }

  @Override
  public void createSingleFileComment(
      final ChangedFile file, final Integer newLine, final String content) {
    markMergeRequestAsWIP();
    final Integer projectId = project.getId();
    final String baseSha = mergeRequest.getBaseSha();
    final String startSha = mergeRequest.getStartSha();
    final String headSha = mergeRequest.getHeadSha();
    final String patchString = file.getSpecifics().get(0);
    final String oldPath = file.getSpecifics().get(1);
    final String newPath = file.getSpecifics().get(2);
    Integer oldLine =
        new PatchParser(patchString) //
            .findOldLine(newLine) //
            .orElse(null);
    try {
      gitlabApi.createTextDiscussion(
          mergeRequest,
          content,
          null,
          baseSha,
          startSha,
          headSha,
          newPath,
          newLine,
          oldPath,
          oldLine);
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
      final List<GitlabNote> notes = gitlabApi.getAllNotes(mergeRequest);
      for (final GitlabNote note : notes) {
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
    for (final GitlabCommitDiff change : mergeRequest.getChanges()) {
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
        final GitlabNote noteToDelete = new GitlabNote();
        noteToDelete.setId(Integer.parseInt(comment.getIdentifier()));
        gitlabApi.deleteNote(mergeRequest, noteToDelete);
      } catch (final Throwable e) {
        violationsLogger.log(
            INFO,
            "Exception thrown when delete note "
                + comment.getIdentifier()
                + ". This is probably because of "
                + "https://github.com/timols/java-gitlab-api/issues/321");
        // violationsLogger.log(SEVERE, "Could not delete note " + comment, e);
      }
    }
  }

  @Override
  public boolean shouldComment(final ChangedFile changedFile, final Integer line) {
    final String patchString = changedFile.getSpecifics().get(0);
    if (!violationCommentsToGitLabApi.getCommentOnlyChangedContent()) return true;
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
