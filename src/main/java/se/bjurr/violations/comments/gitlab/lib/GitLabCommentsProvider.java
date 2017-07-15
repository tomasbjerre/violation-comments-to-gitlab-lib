package se.bjurr.violations.comments.gitlab.lib;

import static se.bjurr.violations.comments.lib.CommentsCreator.FINGERPRINT;
import static se.bjurr.violations.comments.lib.PatchParser.findLineToComment;
import static se.bjurr.violations.comments.lib.utils.CommentsUtils.escapeHTML;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.gitlab.api.AuthMethod;
import org.gitlab.api.GitlabAPI;
import org.gitlab.api.TokenType;
import org.gitlab.api.models.GitlabCommitDiff;
import org.gitlab.api.models.GitlabMergeRequest;
import org.gitlab.api.models.GitlabNote;
import org.gitlab.api.models.GitlabProject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.bjurr.violations.comments.lib.model.ChangedFile;
import se.bjurr.violations.comments.lib.model.Comment;
import se.bjurr.violations.comments.lib.model.CommentsProvider;
import se.bjurr.violations.lib.model.Violation;
import se.bjurr.violations.lib.util.Optional;

public class GitLabCommentsProvider implements CommentsProvider {
  private static final Logger LOG = LoggerFactory.getLogger(GitLabCommentsProvider.class);

  private final ViolationCommentsToGitLabApi violationCommentsToGitLabApi;

  private final GitlabAPI gitlabApi;

  private GitlabProject project;

  private GitlabMergeRequest mergeRequest;

  public GitLabCommentsProvider(ViolationCommentsToGitLabApi violationCommentsToGitLabApi) {
    String hostUrl = violationCommentsToGitLabApi.getHostUrl();
    String apiToken = violationCommentsToGitLabApi.getApiToken();
    TokenType tokenType = violationCommentsToGitLabApi.getTokenType();
    AuthMethod method = violationCommentsToGitLabApi.getMethod();
    gitlabApi = GitlabAPI.connect(hostUrl, apiToken, tokenType, method);

    boolean ignoreCertificateErrors = violationCommentsToGitLabApi.isIgnoreCertificateErrors();
    gitlabApi.ignoreCertificateErrors(ignoreCertificateErrors);

    String projectId = violationCommentsToGitLabApi.getProjectId();
    try {
      project = gitlabApi.getProject(projectId);
    } catch (IOException e) {
      throw new RuntimeException("Could not get project " + projectId);
    }

    Integer mergeRequestId = violationCommentsToGitLabApi.getMergeRequestId();
    try {
      mergeRequest = gitlabApi.getMergeRequestChanges(project.getId(), mergeRequestId);
    } catch (IOException e) {
      throw new RuntimeException("Could not get MR " + projectId + " " + mergeRequestId, e);
    }

    this.violationCommentsToGitLabApi = violationCommentsToGitLabApi;
  }

  @Override
  public void createCommentWithAllSingleFileComments(String comment) {
    try {
      gitlabApi.createNote(mergeRequest, comment);
    } catch (IOException e) {
      LOG.error("Could create comment " + comment, e);
    }
  }

  @Override
  public void createSingleFileComment(ChangedFile file, Integer line, String comment) {
    Integer projectId = project.getId();
    String sha = mergeRequest.getSourceBranch();
    String note = comment;
    String path = file.getFilename();
    String line_type = "new";
    try {
      gitlabApi.createCommitComment(projectId, sha, note, path, line + "", line_type);
    } catch (IOException e) {
      LOG.error(
          "Could not create commit comment"
              + projectId
              + " "
              + sha
              + " "
              + note
              + " "
              + path
              + " "
              + line_type);
    }
  }

  @Override
  public List<Comment> getComments() {
    List<Comment> found = new ArrayList<>();
    try {
      List<GitlabNote> notes = gitlabApi.getAllNotes(mergeRequest);
      for (GitlabNote note : notes) {
        String identifier = note.getId() + "";
        String content = note.getBody();
        String type = "PR";
        List<String> specifics = new ArrayList<>();
        Comment comment = new Comment(identifier, content, type, specifics);
        found.add(comment);
      }
    } catch (IOException e) {
      LOG.error("Could not get comments", e);
    }
    return found;
  }

  @Override
  public List<ChangedFile> getFiles() {
    List<ChangedFile> changedFiles = new ArrayList<>();
    for (GitlabCommitDiff change : mergeRequest.getChanges()) {
      String filename = change.getNewPath();
      List<String> specifics = new ArrayList<>();
      String patchString = change.getDiff();
      specifics.add(patchString);
      ChangedFile changedFile = new ChangedFile(filename, specifics);
      changedFiles.add(changedFile);
    }

    return changedFiles;
  }

  @Override
  public void removeComments(List<Comment> comments) {
    for (Comment comment : comments) {
      try {
        GitlabNote noteToDelete = new GitlabNote();
        noteToDelete.setId(Integer.parseInt(comment.getIdentifier()));
        gitlabApi.deleteNote(mergeRequest, noteToDelete);
      } catch (IOException e) {
        LOG.error("Could not delete note " + comment);
      }
    }
  }

  @Override
  public boolean shouldComment(ChangedFile changedFile, Integer line) {
    String patchString = changedFile.getSpecifics().get(0);
    Optional<Integer> lineFoundOpt = findLineToComment(patchString, line);
    boolean commentOnlyChangedContent = violationCommentsToGitLabApi.getCommentOnlyChangedContent();
    return !commentOnlyChangedContent || commentOnlyChangedContent && lineFoundOpt.isPresent();
  }

  @Override
  public boolean shouldCreateCommentWithAllSingleFileComments() {
    return violationCommentsToGitLabApi.getCreateCommentWithAllSingleFileComments();
  }

  @Override
  public Optional<String> findCommentFormat(ChangedFile changedFile, Violation violation) {
    String source =
        violation.getSource().isPresent()
            ? "**Source**: " + violation.getSource().get() + "\n\n"
            : "";
    String string =
        ""
            + "**Reporter**: "
            + violation.getReporter()
            + "\n\n"
            + "**Rule**: "
            + violation.getRule().or("?")
            + "\n\n"
            + "**Severity**: "
            + violation.getSeverity()
            + "\n\n"
            + "**File**: "
            + changedFile.getFilename()
            + " L"
            + violation.getStartLine()
            + "\n\n"
            + source
            + "\n\n"
            + escapeHTML(violation.getMessage())
            + "\n\n"
            + "\n\n"
            + FINGERPRINT
            + "<hr/>"
            + "\n\n\n\n";
    return Optional.fromNullable(string);
  }

  @Override
  public boolean shouldCreateSingleFileComment() {
    return false;
  }
}
