package se.bjurr.violations.comments.gitlab.lib.client.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

@JsonIgnoreProperties(ignoreUnknown = true)
@SuppressFBWarnings(
    value = "UWF_UNWRITTEN_PUBLIC_OR_PROTECTED_FIELD",
    justification = "Populated by Jackson via reflection when deserializing GitLab API responses")
public class ProjectDto {
  public long id;
}
