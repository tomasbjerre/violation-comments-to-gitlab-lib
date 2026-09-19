package se.bjurr.violations.comments.gitlab.lib;

import static java.util.logging.Level.SEVERE;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.IntStream;
import se.bjurr.violations.comments.gitlab.lib.client.GitLabApiClient;
import se.bjurr.violations.comments.gitlab.lib.client.model.DiffRefDto;
import se.bjurr.violations.comments.gitlab.lib.client.model.DiscussionDto;
import se.bjurr.violations.comments.gitlab.lib.client.model.MergeRequestDto;
import se.bjurr.violations.comments.gitlab.lib.client.model.NoteDto;
import se.bjurr.violations.comments.gitlab.lib.client.model.PositionInput;
import se.bjurr.violations.comments.gitlab.lib.client.model.ProjectDto;
import se.bjurr.violations.comments.lib.CommentsProvider;
import se.bjurr.violations.comments.lib.model.ChangedFile;
import se.bjurr.violations.comments.lib.model.Comment;
import se.bjurr.violations.lib.ViolationsLogger;
import se.bjurr.violations.lib.util.PatchParserUtil;

public class GitLabCommentsProvider implements CommentsProvider {
  static final String START_TITLE = "WIP: (VIOLATIONS)";
  private final ViolationCommentsToGitLabApi api;
  private final ViolationsLogger violationsLogger;
  private final GitLabApiClient gitLabApiClient;
  private final ProjectDto project;
  private final MergeRequestDto mergeRequestChanges;
  private final MergeRequestDto mergeRequest;
  private boolean hasPendingDraftNotes;

  public GitLabCommentsProvider(
      final ViolationsLogger violationsLogger, final ViolationCommentsToGitLabApi api) {
    this(violationsLogger, api, initGitLabApiClient(violationsLogger, api));
  }

  protected GitLabCommentsProvider(
      final ViolationsLogger violationsLogger,
      final ViolationCommentsToGitLabApi api,
      final GitLabApiClient gitLabApiClient,
      final ProjectDto project,
      final MergeRequestDto mergeRequestChanges,
      final MergeRequestDto mergeRequest) {
    this.api = api;
    this.violationsLogger = violationsLogger;
    this.gitLabApiClient = gitLabApiClient;
    this.project = project;
    this.mergeRequestChanges = mergeRequestChanges;
    this.mergeRequest = mergeRequest;
  }

  private GitLabCommentsProvider(
      final ViolationsLogger violationsLogger,
      final ViolationCommentsToGitLabApi api,
      final GitLabApiClient gitLabApiClient) {
    this(
        violationsLogger,
        api,
        gitLabApiClient,
        initProject(api, gitLabApiClient),
        initMergeRequestChanges(api, gitLabApiClient),
        initMergeRequest(api, gitLabApiClient));
  }

  private static GitLabApiClient initGitLabApiClient(
      final ViolationsLogger violationsLogger, final ViolationCommentsToGitLabApi api) {
    return new GitLabApiClient(
        violationsLogger,
        api.getHostUrl(),
        api.getTokenType(),
        api.getApiToken(),
        api.isIgnoreCertificateErrors(),
        api.findProxyServer().orElse(null),
        api.findProxyUser().orElse(null),
        api.findProxyPassword().orElse(null),
        api.isLogRequestResponse());
  }

  private static ProjectDto initProject(
      final ViolationCommentsToGitLabApi api, final GitLabApiClient gitLabApiClient) {
    final String projectId = api.getProjectId();
    try {
      return gitLabApiClient.getProject(projectId);
    } catch (final Throwable e) {
      throw new RuntimeException("Could not get project " + projectId, e);
    }
  }

  private static MergeRequestDto initMergeRequest(
      final ViolationCommentsToGitLabApi api, final GitLabApiClient gitLabApiClient) {
    final String projectId = api.getProjectId();
    final Long mergeRequestId = api.getMergeRequestIid();
    try {
      return gitLabApiClient.getMergeRequest(projectId, mergeRequestId);
    } catch (final Throwable e) {
      throw new RuntimeException("Could not get MR " + projectId + " " + mergeRequestId, e);
    }
  }

  private static MergeRequestDto initMergeRequestChanges(
      final ViolationCommentsToGitLabApi api, final GitLabApiClient gitLabApiClient) {
    final String projectId = api.getProjectId();
    final Long mergeRequestId = api.getMergeRequestIid();
    try {
      return gitLabApiClient.getMergeRequestChanges(projectId, mergeRequestId);
    } catch (final Throwable e) {
      throw new RuntimeException("Could not get MR " + projectId + " " + mergeRequestId, e);
    }
  }

  @Override
  public void createComment(final String comment) {
    this.markMergeRequestAsWIP();
    try {
      if (this.api.getCreateCommentsAsResolvableThreads()) {
        // A discussion created without a diff position is a general thread, not anchored to a
        // line - but it's still a resolvable one, same as a diff comment's thread already is.
        final PositionInput position = null;
        this.gitLabApiClient.createMergeRequestDiscussion(
            String.valueOf(this.project.id), this.mergeRequestChanges.iid, comment, position);
      } else {
        this.gitLabApiClient.createMergeRequestNote(
            String.valueOf(this.project.id), this.mergeRequestChanges.iid, comment);
      }
    } catch (final Throwable e) {
      this.violationsLogger.log(SEVERE, "Could create comment " + comment, e);
    }
  }

  /**
   * Set the merge request as "Work in Progress" if configured to do so by the shouldSetWIP flag.
   */
  private void markMergeRequestAsWIP() {
    if (!this.api.getShouldSetWIP()) {
      return;
    }

    final String currentTitle = this.mergeRequestChanges.title;
    final Optional<String> titleOpt = getTitleWithWipPrefix(currentTitle);
    if (!titleOpt.isPresent()) {
      // To avoid setting WIP again on new comments
      return;
    }
    final String title = titleOpt.get();
    try {
      this.mergeRequestChanges.title = title;
      this.gitLabApiClient.updateMergeRequestTitle(
          String.valueOf(this.project.id), this.mergeRequestChanges.iid, title);
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
    final DiffRefDto diffRefs = this.mergeRequest.diffRefs;
    Objects.requireNonNull(
        diffRefs,
        "diffRefs is null for MR with Iid "
            + this.mergeRequest.iid
            + " in projectId "
            + this.mergeRequest.projectId);
    PositionInput position = null;
    try {
      final String patchString = file.getSpecifics().get(0);
      final String oldPath = file.getSpecifics().get(1);
      final String newPath = file.getSpecifics().get(2);
      final Integer oldLine =
          new PatchParserUtil(patchString) //
              .findOldLine(newLine) //
              .orElse(null);
      position =
          new PositionInput(
              diffRefs.baseSha,
              diffRefs.startSha,
              diffRefs.headSha,
              oldPath,
              newPath,
              oldLine,
              newLine);
      if (this.api.getUseDraftNotes()) {
        this.gitLabApiClient.createDraftNote(
            String.valueOf(this.project.id), this.mergeRequestChanges.iid, content, position);
        this.hasPendingDraftNotes = true;
      } else {
        this.gitLabApiClient.createMergeRequestDiscussion(
            String.valueOf(this.project.id), this.mergeRequestChanges.iid, content, position);
      }
    } catch (final Throwable e) {
      final String lineSeparator = System.lineSeparator();
      this.violationsLogger.log(
          SEVERE,
          "Could not create diff discussion!"
              + lineSeparator
              + "ProjectID: "
              + this.project.id
              + lineSeparator
              + "Violation: "
              + content
              + lineSeparator
              + ", position "
              + position,
          e);
    }
  }

  /**
   * Publishes any draft notes buffered by {@link #createSingleFileComment} (when {@link
   * ViolationCommentsToGitLabApi#getUseDraftNotes()} is {@code true}) as a single GitLab review,
   * instead of one immediately-visible discussion per call. Must be called once after all comments
   * have been created.
   */
  public void flushPendingDraftNotes() {
    if (!this.hasPendingDraftNotes) {
      return;
    }
    try {
      this.gitLabApiClient.bulkPublishDraftNotes(
          String.valueOf(this.project.id), this.mergeRequestChanges.iid);
    } catch (final Throwable e) {
      this.violationsLogger.log(SEVERE, e.getMessage(), e);
    } finally {
      this.hasPendingDraftNotes = false;
    }
  }

  /** Index in {@link Comment#getSpecifics()} of the discussion the comment/note belongs to. */
  static final int SPECIFIC_DISCUSSION_ID = 0;

  /**
   * Index in {@link Comment#getSpecifics()} of whether that discussion is a resolvable one (a
   * diff/single-file discussion), as opposed to a plain top-level merge request note, which GitLab
   * doesn't allow resolving.
   */
  static final int SPECIFIC_RESOLVABLE = 1;

  @Override
  @SuppressFBWarnings("NP_UNWRITTEN_PUBLIC_OR_PROTECTED_FIELD")
  public List<Comment> getComments() {
    final List<Comment> found = new ArrayList<>();
    try {
      // Fetched via discussions, rather than the flat notes list, because resolving a comment
      // (see removeComments()) needs the id of the discussion it belongs to - which only the
      // Discussions API exposes.
      final List<DiscussionDto> discussions =
          this.gitLabApiClient.getMergeRequestDiscussions(
              String.valueOf(this.project.id), this.mergeRequestChanges.iid);

      for (final DiscussionDto discussion : discussions) {
        for (final NoteDto note : discussion.notes) {
          final String identifier = Long.toString(note.id);
          final String content = note.body;
          final String type = "PR";
          final List<String> specifics = new ArrayList<>();
          specifics.add(SPECIFIC_DISCUSSION_ID, discussion.id);
          specifics.add(SPECIFIC_RESOLVABLE, String.valueOf(Boolean.TRUE.equals(note.resolvable)));
          final Comment comment = new Comment(identifier, content, type, specifics);
          found.add(comment);
        }
      }
    } catch (final Throwable e) {
      this.violationsLogger.log(SEVERE, "Could not get comments", e);
    }
    return found;
  }

  @Override
  @SuppressFBWarnings("NP_UNWRITTEN_PUBLIC_OR_PROTECTED_FIELD")
  public List<ChangedFile> getFiles() {
    final List<ChangedFile> changedFiles = new ArrayList<>();
    for (final var change : this.mergeRequestChanges.changes) {
      final String filename = change.newPath;
      final List<String> specifics = new ArrayList<>();
      specifics.add(change.diff);
      specifics.add(change.oldPath);
      specifics.add(change.newPath);
      specifics.add(Boolean.toString(change.newFile));
      specifics.add(Boolean.toString(change.renamedFile));
      specifics.add(Boolean.toString(change.deletedFile));
      final ChangedFile changedFile = new ChangedFile(filename, specifics);
      changedFiles.add(changedFile);
    }

    return changedFiles;
  }

  /**
   * Resolves a comment's discussion instead of deleting the note, when that discussion is
   * resolvable (a diff/single-file discussion). Deleting only the note leaves the discussion itself
   * behind as an orphaned thread - GitLab doesn't allow deleting a resolvable discussion outright,
   * only resolving it or removing every note in it one by one, neither of which makes the thread
   * disappear. Plain top-level notes aren't part of a resolvable discussion and are still just
   * deleted.
   */
  @Override
  public void removeComments(final List<Comment> comments) {
    for (final Comment comment : comments) {
      try {
        if (isResolvable(comment)) {
          final String discussionId = comment.getSpecifics().get(SPECIFIC_DISCUSSION_ID);
          this.gitLabApiClient.resolveMergeRequestDiscussion(
              String.valueOf(this.project.id), this.mergeRequestChanges.iid, discussionId);
        } else {
          final long noteId = Long.parseLong(comment.getIdentifier());
          this.gitLabApiClient.deleteMergeRequestNote(
              String.valueOf(this.project.id), this.mergeRequestChanges.iid, noteId);
        }
      } catch (final Throwable e) {
        this.violationsLogger.log(SEVERE, "Could not remove/resolve comment " + comment, e);
      }
    }
  }

  /**
   * Whether the comment belongs to a resolvable (diff/single-file) discussion, as recorded by
   * {@link #getComments()} - as opposed to a plain top-level merge request note, which GitLab
   * doesn't allow resolving.
   */
  static boolean isResolvable(final Comment comment) {
    return Boolean.parseBoolean(comment.getSpecifics().get(SPECIFIC_RESOLVABLE));
  }

  @Override
  public boolean shouldComment(final ChangedFile changedFile, final Integer line) {
    if (!this.api.getCommentOnlyChangedContent()) {
      return true;
    }
    final String patchString = changedFile.getSpecifics().get(0);
    if (patchString.isEmpty() && Boolean.parseBoolean(changedFile.getSpecifics().get(3))) {
      return true;
    }
    final int contextLines = this.api.getCommentOnlyChangedContentContext();
    final PatchParserUtil patch = new PatchParserUtil(patchString);
    return IntStream.rangeClosed(-contextLines, contextLines)
        .filter(i -> patch.isLineInDiff(line + i) && !patch.findOldLine(line + i).isPresent())
        .findAny()
        .isPresent();
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
