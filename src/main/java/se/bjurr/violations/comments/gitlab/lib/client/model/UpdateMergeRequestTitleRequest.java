package se.bjurr.violations.comments.gitlab.lib.client.model;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

@SuppressFBWarnings(
    value = "URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD",
    justification = "Read by Jackson via reflection when serializing the request body")
public class UpdateMergeRequestTitleRequest {
  public String title;

  public UpdateMergeRequestTitleRequest(final String title) {
    this.title = title;
  }
}
