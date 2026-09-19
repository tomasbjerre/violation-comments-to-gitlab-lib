package se.bjurr.violations.comments.gitlab.lib.client.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class DiffRefDto {
  @JsonProperty("base_sha")
  public String baseSha;

  @JsonProperty("start_sha")
  public String startSha;

  @JsonProperty("head_sha")
  public String headSha;
}
