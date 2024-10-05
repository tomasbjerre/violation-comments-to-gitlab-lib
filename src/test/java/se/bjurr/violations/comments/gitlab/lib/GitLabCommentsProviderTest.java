package se.bjurr.violations.comments.gitlab.lib;

import static se.bjurr.violations.comments.gitlab.lib.GitLabCommentsProvider.START_TITLE;

import java.util.Arrays;
import java.util.logging.Level;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.assertj.core.api.BooleanAssert;
import org.assertj.core.api.SoftAssertions;
import org.junit.Test;
import se.bjurr.violations.comments.lib.model.ChangedFile;
import se.bjurr.violations.lib.ViolationsLogger;

public class GitLabCommentsProviderTest {

  @Test
  public void testTitlePrefix() {
    final SoftAssertions soft = new SoftAssertions();
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
    final ChangedFile change =
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
    final ViolationCommentsToGitLabApi api =
        new ViolationCommentsToGitLabApi().setCommentOnlyChangedContent(false);
    final CommentsChecker checker = new CommentsChecker(api, change);
    checker.shouldComment(1, 100).forEach(BooleanAssert::isTrue);
    checker.assertAll();
  }

  @Test
  public void shouldCommentOnlyChangedLines() {
    final ChangedFile change =
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
    final ViolationCommentsToGitLabApi api =
        new ViolationCommentsToGitLabApi().setCommentOnlyChangedContent(true);
    final CommentsChecker checker = new CommentsChecker(api, change);
    checker.shouldComment(1, 6).forEach(BooleanAssert::isTrue);
    checker.shouldComment(7, 100).forEach(BooleanAssert::isFalse);
    checker.assertAll();
  }

  @Test
  public void shouldNotCommentLargeChanges() {
    final ChangedFile change =
        new ChangedFile(
            "src/main/java/com/test/SomeClass.java",
            Arrays.asList(
                "",
                "src/main/java/com/test/SomeClass.java",
                "src/main/java/com/test/SomeClass.java",
                "false",
                "false",
                "false"));
    final ViolationCommentsToGitLabApi api =
        new ViolationCommentsToGitLabApi().setCommentOnlyChangedContent(true);
    final CommentsChecker checker = new CommentsChecker(api, change);
    checker.shouldComment(1, 10).forEach(BooleanAssert::isFalse);
    checker.assertAll();
  }

  @Test
  public void shouldCommentLargeChangesIfNewFile() {
    final ChangedFile change =
        new ChangedFile(
            "src/main/java/com/test/SomeClass.java",
            Arrays.asList(
                "",
                "src/main/java/com/test/SomeClass.java",
                "src/main/java/com/test/SomeClass.java",
                "true",
                "false",
                "false"));
    final ViolationCommentsToGitLabApi api =
        new ViolationCommentsToGitLabApi().setCommentOnlyChangedContent(true);
    final CommentsChecker checker = new CommentsChecker(api, change);
    checker.shouldComment(1, 10).forEach(BooleanAssert::isTrue);
    checker.assertAll();
  }

  @Test
  public void shouldNotCommentContextLines() {
    final ChangedFile change =
        new ChangedFile(
            "src/main/java/com/test/SomeClass.java",
            Arrays.asList(
                "@@ -9,6 +9,9 @@\n"
                    + "     public void someMethod2() {\n"
                    + "     }\n"
                    + " \n"
                    + "+    public void someMethod2_1() {\n"
                    + "+    }\n"
                    + "+\n"
                    + "     public void someMethod3() {\n"
                    + "     }\n"
                    + " }",
                "src/main/java/com/test/SomeClass.java",
                "src/main/java/com/test/SomeClass.java"));
    final ViolationCommentsToGitLabApi api =
        new ViolationCommentsToGitLabApi()
            .setCommentOnlyChangedContent(true)
            .setCommentOnlyChangedContentContext(0);
    final CommentsChecker checker = new CommentsChecker(api, change);
    checker.shouldComment(1, 11).forEach(BooleanAssert::isFalse);
    checker.shouldComment(12, 14).forEach(BooleanAssert::isTrue);
    checker.shouldComment(15, 20).forEach(BooleanAssert::isFalse);
    checker.assertAll();
  }

  @Test
  public void shouldCommentGivenContextLines() {
    final ChangedFile change =
        new ChangedFile(
            "src/main/java/com/test/SomeClass.java",
            Arrays.asList(
                "@@ -9,6 +9,9 @@\n"
                    + "     public void someMethod2() {\n"
                    + "     }\n"
                    + " \n"
                    + "+    public void someMethod2_1() {\n"
                    + "+    }\n"
                    + "+\n"
                    + "     public void someMethod3() {\n"
                    + "     }\n"
                    + " }",
                "src/main/java/com/test/SomeClass.java",
                "src/main/java/com/test/SomeClass.java"));
    final ViolationCommentsToGitLabApi api =
        new ViolationCommentsToGitLabApi()
            .setCommentOnlyChangedContent(true)
            .setCommentOnlyChangedContentContext(2);
    final CommentsChecker checker = new CommentsChecker(api, change);
    checker.shouldComment(1, 9).forEach(BooleanAssert::isFalse);
    checker.shouldComment(10, 16).forEach(BooleanAssert::isTrue);
    checker.shouldComment(17, 20).forEach(BooleanAssert::isFalse);
    checker.assertAll();
  }

  @Test
  public void shouldCommentGivenContextLinesAcrossHunks() {
    final ChangedFile change =
        new ChangedFile(
            "src/main/java/com/test/SomeClass.java",
            Arrays.asList(
                "@@ -9,6 +9,9 @@\n"
                    + "   public void someMethod2() {\n"
                    + "   }\n"
                    + " \n"
                    + "+  public void someMethod2_1() {\n"
                    + "+  }\n"
                    + "+\n"
                    + "   public void someMethod3() {\n"
                    + "   }\n"
                    + " \n"
                    + "@@ -18,6 +21,6 @@\n"
                    + "   public void someMethod5() {\n"
                    + "   }\n"
                    + " \n"
                    + "-  public void someMethod6() {\n"
                    + "+  public void someMethod7() {\n"
                    + "   }\n"
                    + " }",
                "src/main/java/com/test/SomeClass.java",
                "src/main/java/com/test/SomeClass.java"));
    final ViolationCommentsToGitLabApi api =
        new ViolationCommentsToGitLabApi()
            .setCommentOnlyChangedContent(true)
            .setCommentOnlyChangedContentContext(2);
    final CommentsChecker checker = new CommentsChecker(api, change);
    checker.shouldComment(1, 9).forEach(BooleanAssert::isFalse);
    checker.shouldComment(10, 16).forEach(BooleanAssert::isTrue);
    checker.shouldComment(17, 21).forEach(BooleanAssert::isFalse);
    checker.shouldComment(22, 26).forEach(BooleanAssert::isTrue);
    checker.shouldComment(27, 30).forEach(BooleanAssert::isFalse);
    checker.assertAll();
  }

  private static class CommentsChecker {

    private final GitLabCommentsProvider provider;

    private final ChangedFile file;

    private final SoftAssertions soft = new SoftAssertions();

    private CommentsChecker(final ViolationCommentsToGitLabApi api, final ChangedFile file) {
      this.provider =
          new GitLabCommentsProvider(
              new ViolationsLogger() {
                @Override
                public void log(final Level level, final String string) {}

                @Override
                public void log(final Level level, final String string, final Throwable t) {}
              },
              api,
              null,
              null,
              null,
              null);
      this.file = file;
    }

    public BooleanAssert shouldComment(final int line) {
      return this.soft
          .assertThat(this.provider.shouldComment(this.file, line))
          .as(Integer.toString(line));
    }

    public Stream<BooleanAssert> shouldComment(final int from, final int to) {
      return IntStream.rangeClosed(from, to).mapToObj(this::shouldComment);
    }

    public void assertAll() {
      this.soft.assertAll();
    }
  }
}
