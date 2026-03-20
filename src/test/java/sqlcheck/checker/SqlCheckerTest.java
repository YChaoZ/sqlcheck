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

    @Test
    void shouldIgnoreChinesePunctuationInsideStringLiterals() throws IOException {
        CheckResult insertResult = checkSql("INSERT INTO t(name) VALUES ('中文');\n");
        CheckResult updateResult = checkSql("UPDATE t SET remark = '你好，世界' WHERE id = 1;\n");
        CheckResult selectResult = checkSql("SELECT * FROM t WHERE name = '张三';\n");
        CheckResult escapedQuoteResult = checkSql("INSERT INTO t(txt) VALUES ('it''s 中文，ok');\n");

        assertFalse(hasIssue(insertResult, "CHINESE_PUNCTUATION", null));
        assertFalse(hasIssue(updateResult, "CHINESE_PUNCTUATION", null));
        assertFalse(hasIssue(selectResult, "CHINESE_PUNCTUATION", null));
        assertFalse(hasIssue(escapedQuoteResult, "CHINESE_PUNCTUATION", null));
    }

    @Test
    void shouldStillReportChinesePunctuationOutsideStringLiterals() throws IOException {
        CheckResult result = checkSql("UPDATE t SET remark = 你好，世界 WHERE id = 1;\n");

        CheckResult.Issue issue = findIssue(result, "CHINESE_PUNCTUATION", null);
        assertNotNull(issue);
        assertEquals(1, issue.getLine());
        assertEquals("UPDATE t SET remark = 你好，世界 WHERE id = 1", issue.getSourceLineText());
    }

    @Test
    void shouldIgnoreCommentsDuringAnalysis() throws IOException {
        CheckResult result = checkSql("-- 中文，注释\n" +
            "INSERT INTO t(name) VALUES ('value -- not comment'); -- 中文，注释\n" +
            "# 中文，注释\n" +
            "/* 中文；comment; */\n" +
            "UPDATE t SET remark = 'still ok' WHERE id = 1;\n");

        assertFalse(hasIssue(result, "CHINESE_PUNCTUATION", null));
        assertFalse(hasIssue(result, "DANGEROUS", "UPDATE语句缺少WHERE条件"));
    }

    @Test
    void shouldNotSplitStatementsOnSemicolonsInsideStringsOrComments() throws IOException {
        CheckResult result = checkSql("INSERT INTO t(txt) VALUES ('a;b');\n" +
            "/* comment ; ; */\n" +
            "DELETE FROM t WHERE id = 1;\n");

        assertFalse(hasIssue(result, "REPEATABLE", "存在 INSERT 语句但无对应 DELETE"));
        assertFalse(hasIssue(result, "DANGEROUS", "DELETE语句缺少WHERE条件"));
    }

    @Test
    void shouldApplyIdentifierLengthRuleOnlyToRealIdentifiers() throws IOException {
        String len64 = repeated('a', 64);
        String len65 = repeated('a', 65);
        String col65 = repeated('c', 65);
        String idx65 = repeated('i', 65);

        CheckResult table64Result = checkSql("CREATE TABLE `" + len64 + "` (id BIGINT);\n");
        CheckResult table65Result = checkSql("CREATE TABLE `" + len65 + "` (id BIGINT);\n");
        CheckResult column65Result = checkSql("CREATE TABLE test_table (\n" +
            "    `" + col65 + "` BIGINT\n" +
            ");\n");
        CheckResult index65Result = checkSql("CREATE TABLE test_table (\n" +
            "    id BIGINT,\n" +
            "    KEY `" + idx65 + "` (id)\n" +
            ");\n");
        CheckResult literalAndCommentResult = checkSql("INSERT INTO t(txt) VALUES ('" + len65 + "');\n" +
            "-- `" + len65 + "`\n");

        assertFalse(hasIssue(table64Result, "SYNTAX_ERROR", "长度不能超过64字符"));

        CheckResult.Issue tableIssue = findIssue(table65Result, "SYNTAX_ERROR", "表名长度不能超过64字符");
        assertNotNull(tableIssue);
        assertEquals(1, tableIssue.getLine());

        CheckResult.Issue columnIssue = findIssue(column65Result, "SYNTAX_ERROR", "列名长度不能超过64字符");
        assertNotNull(columnIssue);
        assertEquals(2, columnIssue.getLine());
        assertEquals("`" + col65 + "` BIGINT", columnIssue.getSourceLineText());

        CheckResult.Issue indexIssue = findIssue(index65Result, "SYNTAX_ERROR", "索引名长度不能超过64字符");
        assertNotNull(indexIssue);
        assertEquals(3, indexIssue.getLine());
        assertEquals("KEY `" + idx65 + "` (id)", indexIssue.getSourceLineText());

        assertFalse(hasIssue(literalAndCommentResult, "SYNTAX_ERROR", "长度不能超过64字符"));
    }

    @Test
    void shouldKeepOriginalSourceLineWhenAnalysisMasksCommentOrString() throws IOException {
        String longColumn = repeated('c', 65);
        CheckResult result = checkSql("CREATE TABLE test_table (\n" +
            "    `" + longColumn + "` BIGINT COMMENT '中文，注释'\n" +
            ");\n");

        CheckResult.Issue issue = findIssue(result, "SYNTAX_ERROR", "列名长度不能超过64字符");
        assertNotNull(issue);
        assertEquals(2, issue.getLine());
        assertEquals("`" + longColumn + "` BIGINT COMMENT '中文，注释'", issue.getSourceLineText());
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
            .filter(issue -> type.equals(issue.getType())
                && (messagePart == null || issue.getMessage().contains(messagePart)))
            .findFirst()
            .orElse(null);
    }

    private String repeated(char ch, int count) {
        return String.valueOf(ch).repeat(count);
    }
}
