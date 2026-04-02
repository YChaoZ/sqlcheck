package sqlcheck.checker;

import org.junit.jupiter.api.Test;
import sqlcheck.config.SqlCheckProperties;
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

class SqlCheckerNewFeaturesTest {

    private final SqlChecker checker = new SqlChecker();

    @Test
    void shouldHandleAutoIncrementZeroAsWarning() throws IOException {
        CheckResult result = checkSql("CREATE TABLE test_table (\n" +
            "    id BIGINT AUTO_INCREMENT=0 PRIMARY KEY\n" +
            ");\n");

        CheckResult.Issue issue = findIssue(result, "SYNTAX_ERROR", "AUTO_INCREMENT起始值0");
        assertNotNull(issue);
        assertEquals("WARNING", issue.getSeverity());
    }

    @Test
    void shouldHandleAutoIncrementInvalidValueAsError() throws IOException {
        CheckResult result = checkSql("CREATE TABLE test_table (\n" +
            "    id BIGINT AUTO_INCREMENT=100 PRIMARY KEY\n" +
            ");\n");

        CheckResult.Issue issue = findIssue(result, "SYNTAX_ERROR", "AUTO_INCREMENT起始值只能为0或1");
        assertNotNull(issue);
        assertEquals("ERROR", issue.getSeverity());
    }

    @Test
    void shouldHandleAutoIncrementValueOneAsOk() throws IOException {
        CheckResult result = checkSql("CREATE TABLE test_table (\n" +
            "    id BIGINT AUTO_INCREMENT=1 PRIMARY KEY\n" +
            ");\n");

        assertFalse(hasIssue(result, "SYNTAX_ERROR", "AUTO_INCREMENT"));
    }

    @Test
    void shouldHandleCharsetNotUtf8mb4AsWarning() throws IOException {
        CheckResult utf8Result = checkSql("CREATE TABLE test_table (\n" +
            "    id BIGINT PRIMARY KEY\n" +
            ") ENGINE=InnoDB CHARSET=utf8;\n");

        CheckResult.Issue utf8Issue = findIssue(utf8Result, "DEPRECATED", "字符集建议使用 utf8mb4");
        assertNotNull(utf8Issue);
        assertEquals("WARNING", utf8Issue.getSeverity());

        CheckResult gbkResult = checkSql("CREATE TABLE test_table (\n" +
            "    id BIGINT PRIMARY KEY\n" +
            ") ENGINE=InnoDB CHARSET=gbk;\n");

        CheckResult.Issue gbkIssue = findIssue(gbkResult, "DEPRECATED", "字符集建议使用 utf8mb4");
        assertNotNull(gbkIssue);
        assertEquals("WARNING", gbkIssue.getSeverity());

        CheckResult latin1Result = checkSql("CREATE TABLE test_table (\n" +
            "    id BIGINT PRIMARY KEY\n" +
            ") ENGINE=InnoDB CHARSET=latin1;\n");

        CheckResult.Issue latin1Issue = findIssue(latin1Result, "DEPRECATED", "字符集建议使用 utf8mb4");
        assertNotNull(latin1Issue);
        assertEquals("WARNING", latin1Issue.getSeverity());
    }

    @Test
    void shouldAcceptUtf8mb4Charset() throws IOException {
        CheckResult result = checkSql("CREATE TABLE test_table (\n" +
            "    id BIGINT PRIMARY KEY\n" +
            ") ENGINE=InnoDB CHARSET=utf8mb4;\n");

        assertFalse(hasIssue(result, "DEPRECATED", "字符集建议使用 utf8mb4"));
    }

    @Test
    void shouldHandleDecimalDefaultNullAsWarning() throws IOException {
        CheckResult result = checkSql("CREATE TABLE test_table (\n" +
            "    amount DECIMAL(10,2) DEFAULT NULL,\n" +
            "    price DECIMAL DEFAULT NULL\n" +
            ");\n");

        CheckResult.Issue issue = findIssue(result, "SYNTAX_ERROR", "DECIMAL类型不建议使用DEFAULT NULL");
        assertNotNull(issue);
        assertEquals("WARNING", issue.getSeverity());
    }

    @Test
    void shouldDetectNonUtf8FileEncoding() throws IOException {
        byte[] gbkContent = "CREATE TABLE 测试表 (\n    id BIGINT PRIMARY KEY\n);".getBytes("GBK");
        Path gbkFile = Files.createTempFile("test-", ".sql");
        Files.write(gbkFile, gbkContent);

        List<CheckResult> results = checker.checkFile(gbkFile.toFile());
        assertEquals(1, results.size());
        CheckResult result = results.get(0);

        CheckResult.Issue issue = findIssue(result, "ENCODING", "文件编码不是UTF-8");
        assertNotNull(issue);
        assertEquals("ERROR", issue.getSeverity());

        Files.delete(gbkFile);
    }

    @Test
    void shouldAcceptUtf8FileEncoding() throws IOException {
        Path utf8File = Files.createTempFile("test-utf8-", ".sql");
        Files.write(utf8File, "CREATE TABLE test_table (\n    id BIGINT PRIMARY KEY\n);".getBytes(StandardCharsets.UTF_8));

        List<CheckResult> results = checker.checkFile(utf8File.toFile());
        assertEquals(1, results.size());
        CheckResult result = results.get(0);

        assertFalse(hasIssue(result, "ENCODING", "文件编码不是UTF-8"));

        Files.delete(utf8File);
    }

    private CheckResult checkSql(String content) throws IOException {
        Path file = Files.createTempFile("sql-check-", ".sql");
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

    private CheckResult.Issue findIssue(CheckResult result, String type, String messagePart) {
        return result.getIssues().stream()
            .filter(issue -> type.equals(issue.getType())
                && (messagePart == null || issue.getMessage().contains(messagePart)))
            .findFirst()
            .orElse(null);
    }
}
