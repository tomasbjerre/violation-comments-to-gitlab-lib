package se.bjurr.violations.comments.gitlab.lib;

import static java.util.logging.Level.SEVERE;
import static se.bjurr.violations.comments.lib.PatchParser.findLineToComment;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import org.gitlab.api.AuthMethod;
import org.gitlab.api.GitlabAPI;
import org.gitlab.api.TokenType;
import org.gitlab.api.models.GitlabCommitDiff;
import org.gitlab.api.models.GitlabMergeRequest;
import org.gitlab.api.models.GitlabNote;
import org.gitlab.api.models.GitlabProject;
import se.bjurr.violations.comments.lib.CommentsProvider;
import se.bjurr.violations.comments.lib.ViolationsLogger;
import se.bjurr.violations.comments.lib.model.ChangedFile;
import se.bjurr.violations.comments.lib.model.Comment;
import se.bjurr.violations.lib.util.Optional;

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
    addingComment();
    try {
      gitlabApi.createNote(mergeRequest, comment);
    } catch (final Throwable e) {
      violationsLogger.log(SEVERE, "Could create comment " + comment, e);
    }
  }

  private void addingComment() {
    if (this.violationCommentsToGitLabApi.getShouldSetWIP()) {
      final String currentTitle = mergeRequest.getTitle();
      if (currentTitle.startsWith("WIP:")) {
        return;
      }
      final Serializable projectId = violationCommentsToGitLabApi.getProjectId();
      final Integer mergeRequestId = violationCommentsToGitLabApi.getMergeRequestIid();
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
            mergeRequestId,
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
  }

  @Override
  public void createSingleFileComment(
      final ChangedFile file, final Integer line, final String comment) {
    addingComment();
    final Integer projectId = project.getId();
    final String sha = mergeRequest.getSourceBranch();
    final String note = comment;
    final String path = file.getFilename();
    final String line_type = "new";
    try {
      gitlabApi.createCommitComment(projectId, sha, note, path, line + "", line_type);
    } catch (final Throwable e) {
      violationsLogger.log(
          SEVERE,
          "Could not create commit comment"
              + projectId
              + " "
              + sha
              + " "
              + note
              + " "
              + path
              + " "
              + line_type,
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
        violationsLogger.log(SEVERE, "Could not delete note " + comment, e);
      }
    }
  }

  @Override
  public boolean shouldComment(final ChangedFile changedFile, final Integer line) {
    final String patchString = changedFile.getSpecifics().get(0);
    final Optional<Integer> lineFoundOpt = findLineToComment(patchString, line);
    final boolean commentOnlyChangedContent =
        violationCommentsToGitLabApi.getCommentOnlyChangedContent();
    return !commentOnlyChangedContent || lineFoundOpt.isPresent();
  }

  @Override
  public boolean shouldCreateCommentWithAllSingleFileComments() {
    return violationCommentsToGitLabApi.getCreateCommentWithAllSingleFileComments();
  }

  @Override
  public boolean shouldCreateSingleFileComment() {
    return false;
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
