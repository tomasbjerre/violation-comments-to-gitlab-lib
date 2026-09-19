package se.bjurr.violations.comments.gitlab.lib.client.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.List;

/**
 * Both the plain "get single MR" response (which has {@link #diffRefs} but no {@link #changes}) and
 * the "get MR changes" response (which has both) map to this same shape.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@SuppressFBWarnings(
    value = "UWF_UNWRITTEN_PUBLIC_OR_PROTECTED_FIELD",
    justification = "Populated by Jackson via reflection when deserializing GitLab API responses")
public class MergeRequestDto {
  public long id;
  public long iid;
  public String title;

  @JsonProperty("project_id")
  public long projectId;

  @JsonProperty("diff_refs")
  public DiffRefDto diffRefs;

  public List<DiffDto> changes;
}
