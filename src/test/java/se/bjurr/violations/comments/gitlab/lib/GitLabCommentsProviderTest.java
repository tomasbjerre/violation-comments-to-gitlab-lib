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
    ChangedFile change =
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
    ViolationCommentsToGitLabApi api =
        new ViolationCommentsToGitLabApi().setCommentOnlyChangedContent(false);
    CommentsChecker checker = new CommentsChecker(api, change);
    checker.shouldComment(1, 100).forEach(BooleanAssert::isTrue);
    checker.assertAll();
  }

  @Test
  public void shouldCommentOnlyChangedLines() {
    ChangedFile change =
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
    ViolationCommentsToGitLabApi api =
        new ViolationCommentsToGitLabApi().setCommentOnlyChangedContent(true);
    CommentsChecker checker = new CommentsChecker(api, change);
    checker.shouldComment(1, 6).forEach(BooleanAssert::isTrue);
    checker.shouldComment(7, 100).forEach(BooleanAssert::isFalse);
    checker.assertAll();
  }

  @Test
  public void shouldNotCommentLargeChanges() {
    ChangedFile change =
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
    CommentsChecker checker = new CommentsChecker(api, change);
    checker.shouldComment(1, 10).forEach(BooleanAssert::isFalse);
    checker.assertAll();
  }

  @Test
  public void shouldCommentLargeChangesIfNewFile() {
    ChangedFile change =
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
    CommentsChecker checker = new CommentsChecker(api, change);
    checker.shouldComment(1, 10).forEach(BooleanAssert::isTrue);
    checker.assertAll();
  }

  @Test
  public void shouldNotCommentContextLines() {
    ChangedFile change =
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
    ViolationCommentsToGitLabApi api =
        new ViolationCommentsToGitLabApi()
            .setCommentOnlyChangedContent(true)
            .setCommentOnlyChangedContentContext(0);
    CommentsChecker checker = new CommentsChecker(api, change);
    checker.shouldComment(1, 11).forEach(BooleanAssert::isFalse);
    checker.shouldComment(12, 14).forEach(BooleanAssert::isTrue);
    checker.shouldComment(15, 20).forEach(BooleanAssert::isFalse);
    checker.assertAll();
  }

  @Test
  public void shouldCommentGivenContextLines() {
    ChangedFile change =
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
    ViolationCommentsToGitLabApi api =
        new ViolationCommentsToGitLabApi()
            .setCommentOnlyChangedContent(true)
            .setCommentOnlyChangedContentContext(2);
    CommentsChecker checker = new CommentsChecker(api, change);
    checker.shouldComment(1, 9).forEach(BooleanAssert::isFalse);
    checker.shouldComment(10, 16).forEach(BooleanAssert::isTrue);
    checker.shouldComment(17, 20).forEach(BooleanAssert::isFalse);
    checker.assertAll();
  }

  @Test
  public void shouldCommentGivenContextLinesAcrossHunks() {
    ChangedFile change =
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
    ViolationCommentsToGitLabApi api =
        new ViolationCommentsToGitLabApi()
            .setCommentOnlyChangedContent(true)
            .setCommentOnlyChangedContentContext(2);
    CommentsChecker checker = new CommentsChecker(api, change);
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

    private CommentsChecker(ViolationCommentsToGitLabApi api, ChangedFile file) {
      this.provider =
          new GitLabCommentsProvider(
              new ViolationsLogger() {
                @Override
                public void log(Level level, String string) {}

                @Override
                public void log(Level level, String string, Throwable t) {}
              },
              api,
              null,
              null,
              null,
              null);
      this.file = file;
    }

    public BooleanAssert shouldComment(int line) {
      return soft.assertThat(provider.shouldComment(file, line)).as(Integer.toString(line));
    }

    public Stream<BooleanAssert> shouldComment(int from, int to) {
      return IntStream.rangeClosed(from, to).mapToObj(this::shouldComment);
    }

    public void assertAll() {
      soft.assertAll();
    }
  }
}
