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

class SqlCheckerTest {

    private final SqlChecker checker = new SqlChecker();

    @Test
    void shouldNotReportSqlKeywordsAsReservedIdentifiers() throws IOException {
        CheckResult result = checkSql("CREATE TABLE IF NOT EXISTS test_table (\n" +
            "    id INT PRIMARY KEY AUTO_INCREMENT,\n" +
            "    name VARCHAR(50)\n" +
            ");\n" +
            "INSERT INTO test_table (name) VALUES ('test');\n" +
            "ALTER TABLE test_table ADD COLUMN age INT;\n" +
            "DROP INDEX idx_name;\n" +
            "DROP TABLE my_table;\n");

        assertFalse(hasIssue(result, "RESERVED_WORD", "'CREATE'"));
        assertFalse(hasIssue(result, "RESERVED_WORD", "'ALTER'"));
        assertFalse(hasIssue(result, "RESERVED_WORD", "'DROP'"));
        assertFalse(hasIssue(result, "RESERVED_WORD", "'INSERT'"));
    }

    @Test
    void shouldNotTreatAsciiQuotesAsChinesePunctuation() throws IOException {
        CheckResult result = checkSql("CREATE TABLE test_table (\n" +
            "    description TEXT DEFAULT 'demo'\n" +
            ");\n");

        assertFalse(hasIssue(result, "CHINESE_PUNCTUATION", null));
    }

    @Test
    void shouldOnlyReportCharsetAndEngineSuggestionOnce() throws IOException {
        CheckResult result = checkSql("CREATE TABLE test_table (\n" +
            "    id BIGINT PRIMARY KEY\n" +
            ");\n");

        assertEquals(1, countIssues(result, "BEST_PRACTICE", "字符集 CHARSET=utf8mb4"));
        assertEquals(1, countIssues(result, "BEST_PRACTICE", "存储引擎 ENGINE=InnoDB"));
    }

    @Test
    void shouldLocateIssueOnMatchingSqlLine() throws IOException {
        CheckResult result = checkSql("CREATE TABLE test_table (\n" +
            "    id BIGINT PRIMARY KEY,\n" +
            "    description TEXT DEFAULT 'demo'\n" +
            ") ENGINE=MyISAM;\n");

        CheckResult.Issue textDefaultIssue = findIssue(result, "SYNTAX_ERROR", "TEXT类型不能有DEFAULT值");
        assertNotNull(textDefaultIssue);
        assertEquals(3, textDefaultIssue.getLine());
        assertEquals("description TEXT DEFAULT 'demo'", textDefaultIssue.getSourceLineText());

        CheckResult.Issue engineIssue = findIssue(result, "DEPRECATED", "MYISAM存储引擎已被废弃");
        assertNotNull(engineIssue);
        assertEquals(4, engineIssue.getLine());
        assertEquals(") ENGINE=MyISAM", engineIssue.getSourceLineText());
    }

    private CheckResult checkSql(String content) throws IOException {
        Path file = Files.createTempFile("sql-checker-", ".sql");
        Files.write(file, content.getBytes(StandardCharsets.UTF_8));
        List<CheckResult> results = checker.checkFile(file.toFile());
        assertEquals(1, results.size());
        return results.get(0);
    }

    private boolean hasIssue(CheckResult result, String type, String messagePart) {
        return result.getIssues().stream()
            .anyMatch(issue -> type.equals(issue.getType())
                && (messagePart == null || issue.getMessage().contains(messagePart)));
    }

    private long countIssues(CheckResult result, String type, String messagePart) {
        return result.getIssues().stream()
            .filter(issue -> type.equals(issue.getType()) && issue.getMessage().contains(messagePart))
            .count();
    }

    private CheckResult.Issue findIssue(CheckResult result, String type, String messagePart) {
        return result.getIssues().stream()
            .filter(issue -> type.equals(issue.getType()) && issue.getMessage().contains(messagePart))
            .findFirst()
            .orElse(null);
    }
}
