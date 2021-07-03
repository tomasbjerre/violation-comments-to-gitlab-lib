package se.bjurr.violations.comments.gitlab.lib;

import static se.bjurr.violations.comments.gitlab.lib.GitLabCommentsProvider.START_TITLE;

import org.assertj.core.api.SoftAssertions;
import org.junit.Test;
import se.bjurr.violations.comments.lib.model.ChangedFile;
import se.bjurr.violations.lib.ViolationsLogger;

import java.util.Arrays;
import java.util.logging.Level;
import java.util.stream.IntStream;

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

  @Test
  public void shouldCommentAllLines() {
    ChangedFile changedFile =
        new ChangedFile(
            "src/main/java/com/test/SomeClass.java",
            Arrays.asList(
                "@@ -0,0 +1,6 @@\n"
                    + "+package com.test;\n"
                    + "+\n"
                    + "+public class SomeClass {\n"
                    + "+\n"
                    + "+\n"
                    + "+}\n",
                "src/main/java/com/test/SomeClass.java",
                "src/main/java/com/test/SomeClass.java"));
    ViolationCommentsToGitLabApi api = new ViolationCommentsToGitLabApi()
        .setCommentOnlyChangedContent(false);
    GitLabCommentsProvider gitlab = createGitLabCommentsProvider(api);
    SoftAssertions soft = new SoftAssertions();
    IntStream.rangeClosed(1, 6).forEach(line ->
        soft.assertThat(gitlab.shouldComment(changedFile, line))
            .as(Integer.toString(line))
            .isTrue());
    IntStream.rangeClosed(7, 100).forEach(line ->
        soft.assertThat(gitlab.shouldComment(changedFile, line))
            .as(Integer.toString(line))
            .isTrue());
    soft.assertAll();
  }

  @Test
  public void shouldCommentOnlyChangedLines() {
    ChangedFile changedFile =
        new ChangedFile(
            "src/main/java/com/test/SomeClass.java",
            Arrays.asList(
                "@@ -0,0 +1,6 @@\n"
                    + "+package com.test;\n"
                    + "+\n"
                    + "+public class SomeClass {\n"
                    + "+\n"
                    + "+\n"
                    + "+}\n",
                "src/main/java/com/test/SomeClass.java",
                "src/main/java/com/test/SomeClass.java"));
    ViolationCommentsToGitLabApi api = new ViolationCommentsToGitLabApi().setCommentOnlyChangedContent(true);
    GitLabCommentsProvider gitLabCommentsProvider = createGitLabCommentsProvider(api);
    SoftAssertions soft = new SoftAssertions();
    IntStream.rangeClosed(1, 6).forEach(line ->
        soft.assertThat(gitLabCommentsProvider.shouldComment(changedFile, line))
            .as(Integer.toString(line))
            .isTrue());
    IntStream.rangeClosed(7, 100).forEach(line ->
        soft.assertThat(gitLabCommentsProvider.shouldComment(changedFile, line))
            .as(Integer.toString(line))
            .isFalse());
    soft.assertAll();
  }

  @Test
  public void shouldNotCommentLargeChanges() {
    ChangedFile changedFile =
        new ChangedFile(
            "src/main/java/com/test/SomeClass.java",
            Arrays.asList(
                "",
                "src/main/java/com/test/SomeClass.java",
                "src/main/java/com/test/SomeClass.java",
                "false",
                "false",
                "false"));
    ViolationCommentsToGitLabApi api =
        new ViolationCommentsToGitLabApi().setCommentOnlyChangedContent(true);
    GitLabCommentsProvider gitLabCommentsProvider = createGitLabCommentsProvider(api);
    SoftAssertions soft = new SoftAssertions();
    IntStream.rangeClosed(1, 10).forEach(line ->
        soft.assertThat(gitLabCommentsProvider.shouldComment(changedFile, line))
            .as(Integer.toString(line))
            .isFalse());
    soft.assertAll();
  }

  @Test
  public void shouldCommentLargeChangesIfNewFile() {
    ChangedFile changedFile =
        new ChangedFile(
            "src/main/java/com/test/SomeClass.java",
            Arrays.asList(
                "",
                "src/main/java/com/test/SomeClass.java",
                "src/main/java/com/test/SomeClass.java",
                "true",
                "false",
                "false"));
    ViolationCommentsToGitLabApi api =
        new ViolationCommentsToGitLabApi().setCommentOnlyChangedContent(true);
    GitLabCommentsProvider gitLabCommentsProvider = createGitLabCommentsProvider(api);
    SoftAssertions soft = new SoftAssertions();
    IntStream.rangeClosed(1, 10).forEach(line ->
        soft.assertThat(gitLabCommentsProvider.shouldComment(changedFile, line))
            .as(Integer.toString(line))
            .isTrue());
    soft.assertAll();
  }

  private static GitLabCommentsProvider createGitLabCommentsProvider(ViolationCommentsToGitLabApi api) {
    ViolationsLogger logger = new ViolationsLogger() {
      @Override
      public void log(Level level, String string) {
      }

      @Override
      public void log(Level level, String string, Throwable t) {
      }
    };
    return new GitLabCommentsProvider(logger, api, null, null, null, null);
  }
}
