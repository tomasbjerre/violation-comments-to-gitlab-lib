package se.bjurr.violations.comments.gitlab.lib.client.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

@JsonIgnoreProperties(ignoreUnknown = true)
@SuppressFBWarnings(
    value = "UWF_UNWRITTEN_PUBLIC_OR_PROTECTED_FIELD",
    justification = "Populated by Jackson via reflection when deserializing GitLab API responses")
public class DiffDto {
  public String diff;

  @JsonProperty("old_path")
  public String oldPath;

  @JsonProperty("new_path")
  public String newPath;

  @JsonProperty("new_file")
  public boolean newFile;

  @JsonProperty("renamed_file")
  public boolean renamedFile;

  @JsonProperty("deleted_file")
  public boolean deletedFile;
}
