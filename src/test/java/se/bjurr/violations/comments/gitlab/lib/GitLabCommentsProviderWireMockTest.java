package se.bjurr.violations.comments.gitlab.lib;

import static com.github.tomakehurst.wiremock.client.WireMock.delete;
import static com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.noContent;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.put;
import static com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static se.bjurr.violations.comments.gitlab.lib.GitLabCommentsProvider.SPECIFIC_DISCUSSION_ID;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.Level;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import se.bjurr.violations.comments.lib.model.ChangedFile;
import se.bjurr.violations.comments.lib.model.Comment;
import se.bjurr.violations.lib.ViolationsLogger;

/**
 * Integration tests that replay, via WireMock, real request/response pairs captured from
 * https://gitlab.com/tomas.bjerre85/violations-test/-/merge_requests/2 (project id 2732496, MR iid
 * 2). Unlike {@link GitLabCommentsProviderTest}, which exercises the pure decision logic in
 * isolation, these tests drive the provider through its public constructor - the one production
 * code uses - so the real HTTP wiring (URLs, request payload shapes, response parsing) done by the
 * first-party {@link se.bjurr.violations.comments.gitlab.lib.client.GitLabApiClient} is covered
 * too, not just the {@code isResolvable}/{@code shouldComment} helpers.
 *
 * <p>The fixtures under {@code src/test/resources/gitlab} are the unmodified bodies GitLab returned
 * for these exact calls; only {@code discussions.json} and {@code create_draft_note_response.json}
 * are assembled/reconstructed to exercise more than one call's worth of shapes in a single fixture.
 */
class GitLabCommentsProviderWireMockTest {

  private static final String PROJECT_ID = "2732496";
  private static final Long MR_IID = 2L;
  private static final String PROJECT_PATH = "/api/v4/projects/" + PROJECT_ID;
  private static final String MR_PATH = PROJECT_PATH + "/merge_requests/" + MR_IID;

  @RegisterExtension static WireMockExtension wireMock = WireMockExtension.newInstance().build();

  private ViolationsLogger violationsLogger;

  @BeforeEach
  void setUp() {
    this.wireMock.resetAll();
    this.violationsLogger =
        new ViolationsLogger() {
          @Override
          public void log(final Level level, final String string) {}

          @Override
          public void log(final Level level, final String string, final Throwable t) {}
        };
  }

  private ViolationCommentsToGitLabApi newApi() {
    return ViolationCommentsToGitLabApi.violationCommentsToGitLabApi()
        .setHostUrl(this.wireMock.baseUrl())
        .setApiToken("test-token")
        .setTokenType(TokenType.PRIVATE)
        .setProjectId(PROJECT_ID)
        .setMergeRequestIid(MR_IID);
  }

  /** Stubs the three GET calls every {@link GitLabCommentsProvider} construction makes. */
  private void stubProjectAndMergeRequest() {
    this.wireMock.stubFor(
        get(urlPathEqualTo(PROJECT_PATH)) //
            .willReturn(okJson(fixture("project.json"))));
    this.wireMock.stubFor(
        get(urlPathEqualTo(MR_PATH)) //
            .willReturn(okJson(fixture("merge_request.json"))));
    this.wireMock.stubFor(
        get(urlPathEqualTo(MR_PATH + "/changes")) //
            .willReturn(okJson(fixture("merge_request_changes.json"))));
  }

  private GitLabCommentsProvider newProvider(final ViolationCommentsToGitLabApi api) {
    return new GitLabCommentsProvider(this.violationsLogger, api);
  }

  private static String fixture(final String name) {
    try {
      return Files.readString(Path.of("src/test/resources/gitlab", name));
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private String lastRequestBody(final RequestPatternBuilder pattern) {
    final List<LoggedRequest> requests = this.wireMock.findAll(pattern);
    assertThat(requests).isNotEmpty();
    return requests.get(requests.size() - 1).getBodyAsString();
  }

  @Test
  void getCommentsReturnsBothResolvableAndPlainCommentsFromRealDiscussions() {
    this.stubProjectAndMergeRequest();
    this.wireMock.stubFor(
        get(urlPathEqualTo(MR_PATH + "/discussions")) //
            .willReturn(okJson(fixture("discussions.json"))));

    final GitLabCommentsProvider provider = this.newProvider(this.newApi());

    final List<Comment> comments = provider.getComments();

    assertThat(comments).hasSize(2);

    final Comment resolvableComment = findByIdentifier(comments, "106882047");
    assertThat(resolvableComment.getSpecifics().get(SPECIFIC_DISCUSSION_ID))
        .isEqualTo("169264f08dbab9c202a9ce51db576e14b248ccf1");
    assertThat(GitLabCommentsProvider.isResolvable(resolvableComment)).isTrue();

    final Comment plainComment = findByIdentifier(comments, "3855893232");
    assertThat(plainComment.getSpecifics().get(SPECIFIC_DISCUSSION_ID))
        .isEqualTo("85578c0b4791f52169f1a1c5618afc9bbf644edc");
    assertThat(GitLabCommentsProvider.isResolvable(plainComment)).isFalse();
  }

  private static Comment findByIdentifier(final List<Comment> comments, final String identifier) {
    return comments.stream()
        .filter(c -> c.getIdentifier().equals(identifier))
        .findFirst()
        .orElseThrow(() -> new AssertionError("No comment with identifier " + identifier));
  }

  @Test
  void removeCommentsResolvesTheDiscussionInsteadOfDeletingWhenResolvable() {
    this.stubProjectAndMergeRequest();
    final String discussionId = "169264f08dbab9c202a9ce51db576e14b248ccf1";
    this.wireMock.stubFor(
        put(urlPathEqualTo(MR_PATH + "/discussions/" + discussionId))
            .willReturn(okJson(fixture("resolve_discussion_response.json"))));

    final GitLabCommentsProvider provider = this.newProvider(this.newApi());
    final Comment comment =
        new Comment("106882047", "zxcvsdfsdfdsf", "PR", List.of(discussionId, "true"));

    provider.removeComments(List.of(comment));

    final String resolveBody =
        this.lastRequestBody(
            putRequestedFor(urlPathEqualTo(MR_PATH + "/discussions/" + discussionId)));
    assertThat(resolveBody).contains("\"resolved\":true");
    this.wireMock.verify(0, deleteRequestedFor(urlPathEqualTo(MR_PATH + "/notes/106882047")));
  }

  @Test
  void removeCommentsDeletesTheNoteWhenItsDiscussionIsNotResolvable() {
    this.stubProjectAndMergeRequest();
    final String noteId = "3855893232";
    this.wireMock.stubFor(
        delete(urlPathEqualTo(MR_PATH + "/notes/" + noteId)) //
            .willReturn(noContent()));

    final GitLabCommentsProvider provider = this.newProvider(this.newApi());
    final Comment comment =
        new Comment(
            noteId,
            "Integration test top-level note",
            "PR",
            List.of("85578c0b4791f52169f1a1c5618afc9bbf644edc", "false"));

    provider.removeComments(List.of(comment));

    this.wireMock.verify(deleteRequestedFor(urlPathEqualTo(MR_PATH + "/notes/" + noteId)));
    this.wireMock.verify(
        0,
        putRequestedFor(
            urlPathEqualTo(MR_PATH + "/discussions/85578c0b4791f52169f1a1c5618afc9bbf644edc")));
  }

  @Test
  void createCommentSetsWipTitleAndPostsTheNote() {
    this.stubProjectAndMergeRequest();
    this.wireMock.stubFor(
        put(urlPathEqualTo(MR_PATH)) //
            .willReturn(okJson(fixture("update_title_response.json"))));
    this.wireMock.stubFor(
        post(urlPathEqualTo(MR_PATH + "/notes")) //
            .willReturn(okJson(fixture("create_note_response.json"))));

    final ViolationCommentsToGitLabApi api = this.newApi().setShouldSetWIP(true);
    final GitLabCommentsProvider provider = this.newProvider(api);

    provider.createComment("Integration test top-level note");

    final String noteBody =
        this.lastRequestBody(postRequestedFor(urlPathEqualTo(MR_PATH + "/notes")));
    assertThat(noteBody).contains("\"body\":\"Integration test top-level note\"");

    final String titleBody = this.lastRequestBody(putRequestedFor(urlPathEqualTo(MR_PATH)));
    assertThat(titleBody).contains("\"title\":\"" + GitLabCommentsProvider.START_TITLE);
  }

  @Test
  void createSingleFileCommentPostsADiffDiscussionUsingTheRealDiffRefs() {
    this.stubProjectAndMergeRequest();
    this.wireMock.stubFor(
        put(urlPathEqualTo(MR_PATH)) //
            .willReturn(okJson(fixture("update_title_response.json"))));
    this.wireMock.stubFor(
        post(urlPathEqualTo(MR_PATH + "/discussions")) //
            .willReturn(okJson(fixture("create_discussion_response.json"))));

    final ViolationCommentsToGitLabApi api = this.newApi().setShouldSetWIP(true);
    final GitLabCommentsProvider provider = this.newProvider(api);

    final ChangedFile file =
        new ChangedFile(
            "hej", List.of("@@ -0,0 +1 @@\n+asdasd\n", "hej", "hej", "false", "false", "false"));

    provider.createSingleFileComment(file, 1, "Integration test diff discussion");

    final String discussionBody =
        this.lastRequestBody(postRequestedFor(urlPathEqualTo(MR_PATH + "/discussions")));
    assertThat(discussionBody)
        .contains("\"body\":\"Integration test diff discussion\"")
        .contains("\"base_sha\":\"b563d040b08e991134454c56683c707b6759a7b6\"")
        .contains("\"start_sha\":\"b563d040b08e991134454c56683c707b6759a7b6\"")
        .contains("\"head_sha\":\"47b31bfe93f1a743b7fb8d3a88cbc6b116e30cb8\"")
        .contains("\"new_path\":\"hej\"")
        .contains("\"old_path\":\"hej\"")
        .contains("\"new_line\":1");
  }

  @Test
  void createCommentPostsAResolvableGeneralDiscussionWhenConfiguredTo() {
    this.stubProjectAndMergeRequest();
    this.wireMock.stubFor(
        post(urlPathEqualTo(MR_PATH + "/discussions")) //
            .willReturn(okJson(fixture("create_general_discussion_response.json"))));

    final ViolationCommentsToGitLabApi api =
        this.newApi().withCreateCommentsAsResolvableThreads(true);
    final GitLabCommentsProvider provider = this.newProvider(api);

    provider.createComment("Integration test general resolvable thread");

    final String discussionBody =
        this.lastRequestBody(postRequestedFor(urlPathEqualTo(MR_PATH + "/discussions")));
    assertThat(discussionBody)
        .contains("\"body\":\"Integration test general resolvable thread\"")
        .doesNotContain("\"position\"");
  }

  @Test
  void createCommentPostsAPlainNoteByDefault() {
    this.stubProjectAndMergeRequest();
    this.wireMock.stubFor(
        post(urlPathEqualTo(MR_PATH + "/notes")) //
            .willReturn(okJson(fixture("create_note_response.json"))));

    final GitLabCommentsProvider provider = this.newProvider(this.newApi());

    provider.createComment("Integration test default note");

    this.wireMock.verify(0, postRequestedFor(urlPathEqualTo(MR_PATH + "/discussions")));
    final String noteBody =
        this.lastRequestBody(postRequestedFor(urlPathEqualTo(MR_PATH + "/notes")));
    assertThat(noteBody).contains("\"body\":\"Integration test default note\"");
  }

  @Test
  void createSingleFileCommentCreatesADraftNoteInsteadOfADiscussionWhenUseDraftNotesIsEnabled() {
    this.stubProjectAndMergeRequest();
    this.wireMock.stubFor(
        post(urlPathEqualTo(MR_PATH + "/draft_notes")) //
            .willReturn(okJson(fixture("create_draft_note_response.json"))));

    final ViolationCommentsToGitLabApi api = this.newApi().withUseDraftNotes(true);
    final GitLabCommentsProvider provider = this.newProvider(api);

    final ChangedFile file =
        new ChangedFile(
            "hej", List.of("@@ -0,0 +1 @@\n+asdasd\n", "hej", "hej", "false", "false", "false"));

    provider.createSingleFileComment(file, 1, "Integration test draft note");

    final String draftNoteBody =
        this.lastRequestBody(postRequestedFor(urlPathEqualTo(MR_PATH + "/draft_notes")));
    assertThat(draftNoteBody)
        .contains("\"note\":\"Integration test draft note\"")
        .contains("\"new_line\":1");
    this.wireMock.verify(0, postRequestedFor(urlPathEqualTo(MR_PATH + "/discussions")));
  }

  @Test
  void flushPendingDraftNotesPublishesBufferedDraftNotesAsASingleReview() {
    this.stubProjectAndMergeRequest();
    this.wireMock.stubFor(
        post(urlPathEqualTo(MR_PATH + "/draft_notes")) //
            .willReturn(okJson(fixture("create_draft_note_response.json"))));
    this.wireMock.stubFor(
        post(urlPathEqualTo(MR_PATH + "/draft_notes/bulk_publish")) //
            .willReturn(noContent()));

    final ViolationCommentsToGitLabApi api = this.newApi().withUseDraftNotes(true);
    final GitLabCommentsProvider provider = this.newProvider(api);

    final ChangedFile file =
        new ChangedFile(
            "hej", List.of("@@ -0,0 +1 @@\n+asdasd\n", "hej", "hej", "false", "false", "false"));
    provider.createSingleFileComment(file, 1, "First");
    provider.createSingleFileComment(file, 1, "Second");

    this.wireMock.verify(
        0, postRequestedFor(urlPathEqualTo(MR_PATH + "/draft_notes/bulk_publish")));

    provider.flushPendingDraftNotes();

    this.wireMock.verify(
        1, postRequestedFor(urlPathEqualTo(MR_PATH + "/draft_notes/bulk_publish")));
    this.wireMock.verify(2, postRequestedFor(urlPathEqualTo(MR_PATH + "/draft_notes")));
  }

  @Test
  void flushPendingDraftNotesDoesNothingWhenNoDraftNotesWereCreated() {
    final GitLabCommentsProvider provider =
        new GitLabCommentsProvider(this.violationsLogger, this.newApi(), null, null, null, null);

    provider.flushPendingDraftNotes();

    this.wireMock.verify(
        0, postRequestedFor(urlPathEqualTo(MR_PATH + "/draft_notes/bulk_publish")));
  }
}
