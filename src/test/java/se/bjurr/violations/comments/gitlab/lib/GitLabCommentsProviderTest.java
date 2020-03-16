package se.bjurr.violations.comments.gitlab.lib;

import static se.bjurr.violations.comments.gitlab.lib.GitLabCommentsProvider.START_TITLE;

import org.assertj.core.api.SoftAssertions;
import org.junit.Test;

public class GitLabCommentsProviderTest {

  @Test
  public void testTitlePrefix() {
    SoftAssertions soft = new SoftAssertions();
    soft.assertThat(GitLabCommentsProvider.getTitleWithWipPrefix("asdas").orElse(null)) //
        .isEqualTo(START_TITLE + " asdas");
    soft.assertThat(GitLabCommentsProvider.getTitleWithWipPrefix("WIP asdas").orElse(null)) //
        .isEqualTo(START_TITLE + " asdas");
    soft.assertThat(GitLabCommentsProvider.getTitleWithWipPrefix("WIP: asdas").orElse(null)) //
        .isEqualTo(START_TITLE + " asdas");
    soft.assertThat(GitLabCommentsProvider.getTitleWithWipPrefix("WIP").orElse(null)) //
        .isEqualTo(START_TITLE);
    soft.assertThat(GitLabCommentsProvider.getTitleWithWipPrefix("WIP:").orElse(null)) //
        .isEqualTo(START_TITLE);
    soft.assertThat(GitLabCommentsProvider.getTitleWithWipPrefix(START_TITLE).orElse(null)) //
        .isNull();
    soft.assertThat(
            GitLabCommentsProvider.getTitleWithWipPrefix(START_TITLE + " asdas").orElse(null)) //
        .isNull();
    soft.assertThat(
            GitLabCommentsProvider.getTitleWithWipPrefix(START_TITLE + "asdasd").orElse(null)) //
        .isNull();
    soft.assertAll();
  }
}
