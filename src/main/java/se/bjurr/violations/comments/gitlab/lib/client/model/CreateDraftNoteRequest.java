package se.bjurr.violations.comments.gitlab.lib.client.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

@SuppressFBWarnings(
    value = "URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD",
    justification = "Read by Jackson via reflection when serializing the request body")
public class CreateDraftNoteRequest {
  public String note;

  /** {@code null} for a draft note not anchored to a diff position. */
  @JsonInclude(Include.NON_NULL)
  public PositionInput position;

  public CreateDraftNoteRequest(final String note, final PositionInput position) {
    this.note = note;
    this.position = position;
  }
}
