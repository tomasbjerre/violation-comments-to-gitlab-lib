package se.bjurr.violations.comments.gitlab.lib.client;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import se.bjurr.violations.comments.gitlab.lib.TokenType;
import se.bjurr.violations.comments.gitlab.lib.client.GitLabInvoker.Method;
import se.bjurr.violations.comments.gitlab.lib.client.model.CreateDiscussionRequest;
import se.bjurr.violations.comments.gitlab.lib.client.model.CreateDraftNoteRequest;
import se.bjurr.violations.comments.gitlab.lib.client.model.CreateNoteRequest;
import se.bjurr.violations.comments.gitlab.lib.client.model.DiscussionDto;
import se.bjurr.violations.comments.gitlab.lib.client.model.MergeRequestDto;
import se.bjurr.violations.comments.gitlab.lib.client.model.NoteDto;
import se.bjurr.violations.comments.gitlab.lib.client.model.PositionInput;
import se.bjurr.violations.comments.gitlab.lib.client.model.ProjectDto;
import se.bjurr.violations.comments.gitlab.lib.client.model.ResolveDiscussionRequest;
import se.bjurr.violations.comments.gitlab.lib.client.model.UpdateMergeRequestTitleRequest;
import se.bjurr.violations.lib.ViolationsLogger;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * A small first-party client for the subset of the GitLab REST API this library needs, replacing
 * the {@code gitlab4j-api} dependency (which has no support for GitLab's Draft Notes endpoints -
 * see https://github.com/tomasbjerre/violation-comments-to-gitlab-lib/issues/28).
 */
public class GitLabApiClient {
  private static final String SEGMENT_API = "/api/v4";
  private static final int PAGE_SIZE = 100;

  private static final JsonMapper JSON_MAPPER =
      JsonMapper.builder()
          .changeDefaultVisibility(vc -> vc.withFieldVisibility(Visibility.ANY))
          .build();

  private final String baseUri;
  private final GitLabInvoker gitLabInvoker;
  private final ViolationsLogger violationsLogger;

  public GitLabApiClient(
      final ViolationsLogger violationsLogger,
      final String hostUrl,
      final TokenType tokenType,
      final String apiToken,
      final boolean ignoreCertificateErrors,
      final String proxyServer,
      final String proxyUser,
      final String proxyPassword,
      final boolean logRequestResponse) {
    this.violationsLogger = violationsLogger;
    this.baseUri = stripTrailingSlash(hostUrl) + SEGMENT_API;
    this.gitLabInvoker =
        new GitLabInvoker(
            tokenType,
            apiToken,
            ignoreCertificateErrors,
            proxyServer,
            proxyUser,
            proxyPassword,
            logRequestResponse);
  }

  private static String stripTrailingSlash(final String url) {
    return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
  }

  private static String encode(final String pathSegment) {
    return URLEncoder.encode(pathSegment, UTF_8);
  }

  private String mrPath(final String projectId, final long mergeRequestIid) {
    return "/projects/" + encode(projectId) + "/merge_requests/" + mergeRequestIid;
  }

  private GitLabResponse invokeChecked(
      final String path, final Method method, final String jsonBody) {
    final String url = this.baseUri + path;
    final GitLabResponse response =
        this.gitLabInvoker.invoke(this.violationsLogger, url, method, jsonBody);
    if (!response.isSuccessful()) {
      throw new GitLabApiException(
          method.name(), url, response.getStatusCode(), response.getBody());
    }
    return response;
  }

  private <T> List<T> getAllPages(final String path, final TypeReference<List<T>> type) {
    final List<T> all = new ArrayList<>();
    int page = 1;
    while (true) {
      final String pagedPath =
          path + (path.contains("?") ? "&" : "?") + "per_page=" + PAGE_SIZE + "&page=" + page;
      final GitLabResponse response = this.invokeChecked(pagedPath, Method.GET, null);
      final List<T> pageResult = JSON_MAPPER.readValue(response.getBody(), type);
      all.addAll(pageResult);
      if (pageResult.size() < PAGE_SIZE) {
        break;
      }
      page++;
    }
    return all;
  }

  public ProjectDto getProject(final String projectId) {
    final GitLabResponse response =
        this.invokeChecked("/projects/" + encode(projectId), Method.GET, null);
    return JSON_MAPPER.readValue(response.getBody(), ProjectDto.class);
  }

  /**
   * Populates {@code diff_refs}, https://docs.gitlab.com/ee/api/merge_requests.html#get-single-mr
   */
  public MergeRequestDto getMergeRequest(final String projectId, final long mergeRequestIid) {
    final GitLabResponse response =
        this.invokeChecked(this.mrPath(projectId, mergeRequestIid), Method.GET, null);
    return JSON_MAPPER.readValue(response.getBody(), MergeRequestDto.class);
  }

  public MergeRequestDto getMergeRequestChanges(
      final String projectId, final long mergeRequestIid) {
    final GitLabResponse response =
        this.invokeChecked(this.mrPath(projectId, mergeRequestIid) + "/changes", Method.GET, null);
    return JSON_MAPPER.readValue(response.getBody(), MergeRequestDto.class);
  }

  public void updateMergeRequestTitle(
      final String projectId, final long mergeRequestIid, final String title) {
    final String jsonBody =
        JSON_MAPPER.writeValueAsString(new UpdateMergeRequestTitleRequest(title));
    this.invokeChecked(this.mrPath(projectId, mergeRequestIid), Method.PUT, jsonBody);
  }

  public NoteDto createMergeRequestNote(
      final String projectId, final long mergeRequestIid, final String body) {
    final String jsonBody = JSON_MAPPER.writeValueAsString(new CreateNoteRequest(body));
    final GitLabResponse response =
        this.invokeChecked(
            this.mrPath(projectId, mergeRequestIid) + "/notes", Method.POST, jsonBody);
    return JSON_MAPPER.readValue(response.getBody(), NoteDto.class);
  }

  /**
   * Creates a discussion. When {@code position} is {@code null}, this is a general thread not
   * anchored to a diff position - still resolvable, unlike a plain note.
   */
  public void createMergeRequestDiscussion(
      final String projectId,
      final long mergeRequestIid,
      final String body,
      final PositionInput position) {
    final String jsonBody =
        JSON_MAPPER.writeValueAsString(new CreateDiscussionRequest(body, position));
    this.invokeChecked(
        this.mrPath(projectId, mergeRequestIid) + "/discussions", Method.POST, jsonBody);
  }

  /**
   * Fetched via discussions, rather than the flat notes list, because resolving a comment needs the
   * id of the discussion it belongs to - which only the Discussions API exposes.
   */
  public List<DiscussionDto> getMergeRequestDiscussions(
      final String projectId, final long mergeRequestIid) {
    return this.getAllPages(
        this.mrPath(projectId, mergeRequestIid) + "/discussions",
        new TypeReference<List<DiscussionDto>>() {});
  }

  public void resolveMergeRequestDiscussion(
      final String projectId, final long mergeRequestIid, final String discussionId) {
    final String jsonBody = JSON_MAPPER.writeValueAsString(new ResolveDiscussionRequest(true));
    this.invokeChecked(
        this.mrPath(projectId, mergeRequestIid) + "/discussions/" + encode(discussionId),
        Method.PUT,
        jsonBody);
  }

  public void deleteMergeRequestNote(
      final String projectId, final long mergeRequestIid, final long noteId) {
    this.invokeChecked(
        this.mrPath(projectId, mergeRequestIid) + "/notes/" + noteId, Method.DELETE, null);
  }

  /**
   * Creates a draft note - invisible on the merge request until {@link
   * #bulkPublishDraftNotes(String, long)} is called. See
   * https://docs.gitlab.com/ee/api/draft_notes.html
   */
  public void createDraftNote(
      final String projectId,
      final long mergeRequestIid,
      final String note,
      final PositionInput position) {
    final String jsonBody =
        JSON_MAPPER.writeValueAsString(new CreateDraftNoteRequest(note, position));
    this.invokeChecked(
        this.mrPath(projectId, mergeRequestIid) + "/draft_notes", Method.POST, jsonBody);
  }

  /** Publishes every draft note created by the current user on this MR, as a single review. */
  public void bulkPublishDraftNotes(final String projectId, final long mergeRequestIid) {
    this.invokeChecked(
        this.mrPath(projectId, mergeRequestIid) + "/draft_notes/bulk_publish", Method.POST, null);
  }
}
