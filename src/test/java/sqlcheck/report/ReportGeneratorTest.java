package sqlcheck.report;

import org.junit.jupiter.api.Test;
import sqlcheck.model.CheckResult;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ReportGeneratorTest {

    private final ReportGenerator generator = new ReportGenerator();

    @Test
    void shouldWriteHtmlReportInUtf8() throws IOException {
        CheckResult result = new CheckResult("测试.sql", "/tmp/测试.sql", "SQL");
        result.addIssue(new CheckResult.Issue(1, "SYNTAX_ERROR", "建议指定字符集 CHARSET=utf8mb4", "INFO", "SELECT * FROM dual"));

        Path file = Files.createTempFile("report-", ".html");
        generator.generateHtmlReport(Collections.singletonList(result), file.toString());

        String html = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
        assertTrue(html.contains("<meta charset=\"UTF-8\">"));
        assertTrue(html.contains("SQL/Apollo配置检查报告"));
        assertTrue(html.contains("建议指定字符集 CHARSET=utf8mb4"));
        assertTrue(html.contains("测试.sql"));
        assertTrue(html.contains("按严重级别过滤"));
        assertTrue(html.contains("data-severity=\"INFO\""));
        assertTrue(html.contains("data-type=\"SYNTAX_ERROR\""));
        assertTrue(html.contains("source-line"));
        assertTrue(html.contains("SELECT * FROM dual"));
    }

    @Test
    void shouldWriteSourceLineTextToJsonReport() throws IOException {
        CheckResult result = new CheckResult("demo.sql", "/tmp/demo.sql", "SQL");
        result.addIssue(new CheckResult.Issue(3, "BEST_PRACTICE", "SELECT * 会查询所有字段，建议指定具体字段名", "WARNING", "SELECT *"));

        Path file = Files.createTempFile("report-", ".json");
        generator.generateJsonReport(Collections.singletonList(result), file.toString());

        String json = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
        assertTrue(json.contains("\"sourceLineText\": \"SELECT *\""));
        assertTrue(json.contains("\"severity\": \"WARNING\""));
    }
}
