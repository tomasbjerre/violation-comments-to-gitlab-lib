import org.junit.Test;
import se.bjurr.violations.comments.gitlab.lib.GitLabCommentsProvider;
import se.bjurr.violations.comments.gitlab.lib.ViolationCommentsToGitLabApi;

import java.util.Map;

public class GitLabCommentsProviderTest {
    @Test
    public void testDiffLineTranslation() {
        String diff = "--- a/src/main/java/se/bjurr/violations/lib/example/OtherClass.java\n+++ b/src/main/java/se/bjurr/violations/lib/example/OtherClass.java\n@@ -4,12 +4,15 @@ package se.bjurr.violations.lib.example;\n  * No ending dot\n  */\n public class OtherClass {\n- public static String CoNstANT = \"yes\";\n+ public static String CoNstANT = \"yes\"; \n \n  public void myMethod() {\n   if (CoNstANT.equals(\"abc\")) {\n \n   }\n+  if (CoNstANT.equals(\"abc\")) {\n+\n+  }\n  }\n \n  @Override\n";
        final Map<Integer, Integer> map = GitLabCommentsProvider.getLineTranslation(diff, 18);
        for (Map.Entry<Integer,Integer> e : map.entrySet()) {
            System.out.println(e.getKey()+" : "+e.getValue());
        }
    }

}
