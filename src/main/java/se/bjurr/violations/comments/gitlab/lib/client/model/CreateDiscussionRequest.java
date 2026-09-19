package se.bjurr.violations.comments.gitlab.lib.client.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

@SuppressFBWarnings(
    value = "URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD",
    justification = "Read by Jackson via reflection when serializing the request body")
public class CreateDiscussionRequest {
  public String body;

  /** {@code null} for a general thread not anchored to a diff position. */
  @JsonInclude(Include.NON_NULL)
  public PositionInput position;

  public CreateDiscussionRequest(final String body, final PositionInput position) {
    this.body = body;
    this.position = position;
  }
}
