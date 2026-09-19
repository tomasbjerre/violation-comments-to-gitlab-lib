package se.bjurr.violations.comments.gitlab.lib.client.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

@SuppressFBWarnings(
    value = "URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD",
    justification = "Read by Jackson via reflection when serializing the request body")
public class PositionInput {
  @JsonProperty("position_type")
  public String positionType = "text";

  @JsonProperty("base_sha")
  public String baseSha;

  @JsonProperty("start_sha")
  public String startSha;

  @JsonProperty("head_sha")
  public String headSha;

  @JsonProperty("old_path")
  public String oldPath;

  @JsonProperty("new_path")
  public String newPath;

  @JsonProperty("old_line")
  public Integer oldLine;

  @JsonProperty("new_line")
  public Integer newLine;

  public PositionInput(
      final String baseSha,
      final String startSha,
      final String headSha,
      final String oldPath,
      final String newPath,
      final Integer oldLine,
      final Integer newLine) {
    this.baseSha = baseSha;
    this.startSha = startSha;
    this.headSha = headSha;
    this.oldPath = oldPath;
    this.newPath = newPath;
    this.oldLine = oldLine;
    this.newLine = newLine;
  }
}
