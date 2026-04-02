package sqlcheck.checker;

import org.junit.jupiter.api.Test;
import sqlcheck.model.CheckResult;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApolloCheckerTest {

    private final ApolloChecker checker = new ApolloChecker();

    @Test
    void shouldReportDuplicateKeysInApolloScript() throws IOException {
        CheckResult result = checkApolloSql("INSERT KEY:app.version VALUE:1.0;\n" +
            "INSERT KEY:app.version VALUE:2.0;\n");

        assertEquals(2, countIssues(result, "DUPLICATE_KEY"));
    }

    @Test
    void shouldNotTreatAsciiQuotesAsChinesePunctuation() throws IOException {
        CheckResult result = checkApolloSql("INSERT KEY:app.name VALUE:'demo-app';\n");

        assertFalse(hasIssue(result, "CHINESE_PUNCTUATION"));
    }

    @Test
    void shouldReportChineseColon() throws IOException {
        CheckResult result = checkApolloSql("INSERT KEY：app.name VALUE:test;\n");

        assertTrue(hasIssue(result, "CHINESE_PUNCTUATION"));
    }

    @Test
    void shouldIncludeSourceLineTextWithoutChangingLineNumbers() throws IOException {
        CheckResult result = checkApolloSql("INSERT KEY:app.version VALUE:1.0\n");

        CheckResult.Issue issue = result.getIssues().stream()
            .filter(current -> "MISSING_SEMICOLON".equals(current.getType()))
            .findFirst()
            .orElse(null);

        assertNotNull(issue);
        assertEquals(1, issue.getLine());
        assertEquals("INSERT KEY:app.version VALUE:1.0", issue.getSourceLineText());
    }

    @Test
    void shouldSupportFlatApolloYamlKeys() throws IOException {
        CheckResult result = checkApolloYaml("app.id: demo\nspring.datasource.url: jdbc:mysql://localhost/test\n");

        assertFalse(hasIssue(result, "DUPLICATE_KEY"));
        assertFalse(hasIssue(result, "KEY_PATH_CONFLICT"));
    }

    @Test
    void shouldTreatNestedAndFlatYamlKeysAsDuplicate() throws IOException {
        CheckResult result = checkApolloYaml("app:\n  id: demo\napp.id: prod\n");

        assertEquals(2, countIssues(result, "DUPLICATE_KEY"));
        CheckResult.Issue duplicateRepeat = result.getIssues().stream()
            .filter(issue -> "DUPLICATE_KEY".equals(issue.getType()) && issue.getLine() == 3)
            .findFirst()
            .orElse(null);
        assertNotNull(duplicateRepeat);
        assertEquals("app.id: prod", duplicateRepeat.getSourceLineText());
    }

    @Test
    void shouldReportYamlParentChildPathConflict() throws IOException {
        CheckResult result = checkApolloYaml("app: demo\napp.id: prod\n");

        assertEquals(2, countIssues(result, "KEY_PATH_CONFLICT"));
        assertEquals(0, countIssues(result, "DUPLICATE_KEY"));
        CheckResult.Issue firstIssue = result.getIssues().stream()
            .filter(issue -> "KEY_PATH_CONFLICT".equals(issue.getType()) && issue.getLine() == 1)
            .findFirst()
            .orElse(null);
        assertNotNull(firstIssue);
        assertEquals("app: demo", firstIssue.getSourceLineText());
    }

    @Test
    void shouldReportInvalidYamlKeySegmentFormat() throws IOException {
        CheckResult result = checkApolloYaml("app:\n  1name: demo\n");

        assertTrue(hasIssue(result, "FORMAT"));
    }

    @Test
    void shouldPreserveYamlLineAndSourceText() throws IOException {
        CheckResult result = checkApolloYaml("app:\n  id: demo\napp.id: prod\n");

        CheckResult.Issue issue = result.getIssues().stream()
            .filter(current -> "DUPLICATE_KEY".equals(current.getType()) && current.getLine() == 2)
            .findFirst()
            .orElse(null);

        assertNotNull(issue);
        assertEquals("id: demo", issue.getSourceLineText());
    }

    @Test
    void shouldPassCleanApolloScriptYaml() throws IOException {
        String content =
            "insert:\n" +
            "  app.name:\n" +
            "    value: test-app\n" +
            "    comment: 应用名称\n" +
            "update:\n" +
            "  app.version:\n" +
            "    value: \"1.0.1\"\n" +
            "    comment: 更新版本\n" +
            "delete:\n" +
            "  old.key:\n";
        CheckResult result = checkApolloYaml(content);

        assertFalse(hasIssue(result, "DUPLICATE_KEY"));
        assertFalse(hasIssue(result, "SYNTAX_ERROR"));
    }

    @Test
    void shouldReportDuplicateKeyInApolloScriptYaml() throws IOException {
        String content =
            "insert:\n" +
            "  app.name:\n" +
            "    value: test-app\n" +
            "insert:\n" +
            "  app.name:\n" +
            "    value: test-app2\n";
        CheckResult result = checkApolloYaml(content);

        assertEquals(1, countIssues(result, "DUPLICATE_KEY"));
    }

    @Test
    void shouldReportMissingValueInApolloScriptYaml() throws IOException {
        String content =
            "insert:\n" +
            "  app.name:\n" +
            "    comment: 没有value\n";
        CheckResult result = checkApolloYaml(content);

        assertTrue(hasIssue(result, "SYNTAX_ERROR"));
    }

    @Test
    void shouldReportMissingKeyInApolloScriptYaml() throws IOException {
        String content =
            "update:\n";
        CheckResult result = checkApolloYaml(content);

        assertTrue(hasIssue(result, "SYNTAX_ERROR"));
    }

    @Test
    void shouldNotReportErrorForDeleteWithoutValue() throws IOException {
        String content =
            "delete:\n" +
            "  old.key:\n";
        CheckResult result = checkApolloYaml(content);

        assertFalse(hasIssue(result, "SYNTAX_ERROR"));
    }

    private CheckResult checkApolloSql(String content) throws IOException {
        return checkFile(content, ".sql");
    }

    private CheckResult checkApolloYaml(String content) throws IOException {
        return checkFile(content, ".yml");
    }

    private CheckResult checkFile(String content, String suffix) throws IOException {
        Path file = Files.createTempFile("apollo-checker-", suffix);
        Files.write(file, content.getBytes(StandardCharsets.UTF_8));
        List<CheckResult> results = checker.checkFile(file.toFile());
        assertEquals(1, results.size());
        return results.get(0);
    }

    private boolean hasIssue(CheckResult result, String type) {
        return result.getIssues().stream().anyMatch(issue -> type.equals(issue.getType()));
    }

    private long countIssues(CheckResult result, String type) {
        return result.getIssues().stream().filter(issue -> type.equals(issue.getType())).count();
    }
}
