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

class SqlCheckerTest {

    private final SqlChecker checker = new SqlChecker();
    private final SqlChecker databaseAwareChecker = new SqlChecker(databaseProperties("biz_db", true));

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
    void shouldEnforceDatabasePrefixWhenEnabled() throws IOException {
        CheckResult okResult = checkSql(databaseAwareChecker, "CREATE TABLE biz_db.user_account (id BIGINT);\n");
        CheckResult missingPrefixResult = checkSql(databaseAwareChecker, "CREATE TABLE user_account (id BIGINT);\n");
        CheckResult useResult = checkSql(databaseAwareChecker, "USE biz_db;\nCREATE TABLE biz_db.user_account (id BIGINT);\n");
        CheckResult apolloResult = checkApolloLikeSql("USE biz_db;\nCREATE TABLE user_account (id BIGINT);\n");

        assertFalse(hasIssue(okResult, "SYNTAX_ERROR", "数据库名.表名"));
        assertTrue(hasIssue(missingPrefixResult, "SYNTAX_ERROR", "数据库名.表名"));
        assertTrue(hasIssue(useResult, "DANGEROUS", "禁止使用 USE 切库语句"));
        assertFalse(hasIssue(apolloResult, "SYNTAX_ERROR", "数据库名.表名"));
    }

    @Test
    void shouldNotCheckDatabasePrefixForComments() throws IOException {
        SqlChecker dbChecker = new SqlChecker(databaseProperties("biz_db", true));
        CheckResult result = checkSql(dbChecker, "-- 这是一个注释\nCREATE TABLE biz_db.user_account (id BIGINT);\n");

        assertFalse(hasIssue(result, "SYNTAX_ERROR", "数据库名.表名"));
    }

    @Test
    void shouldExtractDatabaseNameFromFileName() throws IOException {
        SqlCheckProperties props = new SqlCheckProperties();
        props.setDatabaseCheckEnabled(true);
        SqlChecker dbChecker = new SqlChecker(props);

        Path file = Files.createTempFile("biz_ddl_alice", ".sql");
        Files.write(file, "CREATE TABLE user_account (id BIGINT);".getBytes(StandardCharsets.UTF_8));

        List<CheckResult> results = dbChecker.checkFile(file.toFile());
        CheckResult result = results.get(0);

        assertTrue(hasIssue(result, "SYNTAX_ERROR", "表引用必须写成 数据库名.表名"));

        Files.delete(file);
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

        CheckResult mb4Result = checkSql("CREATE TABLE test_table (\n" +
            "    id BIGINT PRIMARY KEY\n" +
            ") ENGINE=InnoDB CHARSET=utf8mb4;\n");

        assertFalse(hasIssue(mb4Result, "DEPRECATED", "字符集建议使用 utf8mb4"));
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

    private CheckResult checkSql(String content) throws IOException {
        return checkSql(checker, content);
    }

    private CheckResult checkSql(SqlChecker sqlChecker, String content) throws IOException {
        Path file = Files.createTempFile("sql-checker-", ".sql");
        Files.write(file, content.getBytes(StandardCharsets.UTF_8));
        List<CheckResult> results = sqlChecker.checkFile(file.toFile());
        assertEquals(1, results.size());
        return results.get(0);
    }

    private CheckResult checkApolloLikeSql(String content) throws IOException {
        Path file = Files.createTempFile("apollo-like-", ".sql");
        Files.write(file, content.getBytes(StandardCharsets.UTF_8));
        List<CheckResult> results = new ApolloChecker().checkFile(file.toFile());
        assertEquals(1, results.size());
        return results.get(0);
    }

    private SqlCheckProperties databaseProperties(String databaseName, boolean enabled) {
        SqlCheckProperties properties = new SqlCheckProperties();
        properties.setDatabaseName(databaseName);
        properties.setDatabaseCheckEnabled(enabled);
        return properties;
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
        StringBuilder sb = new StringBuilder(count);
        for (int i = 0; i < count; i++) {
            sb.append(ch);
        }
        return sb.toString();
    }
}
