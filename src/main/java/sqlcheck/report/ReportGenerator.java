package sqlcheck.report;

import sqlcheck.model.CheckResult;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class ReportGenerator {

    public void generateHtmlReport(List<CheckResult> results, String outputPath) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        String timestamp = sdf.format(new Date());
        Summary summary = new Summary(results);
        Set<String> issueTypes = collectIssueTypes(results);

        try (PrintWriter writer = new PrintWriter(new OutputStreamWriter(Files.newOutputStream(Paths.get(outputPath)), StandardCharsets.UTF_8))) {
            writer.println("<!DOCTYPE html>");
            writer.println("<html>");
            writer.println("<head>");
            writer.println("    <meta charset=\"UTF-8\">");
            writer.println("    <title>SQL/Apollo配置检查报告</title>");
            writer.println("    <style>");
            writer.println("        body { font-family: Arial, sans-serif; margin: 20px; background: #f5f5f5; }");
            writer.println("        .container { max-width: 1400px; margin: 0 auto; background: white; padding: 20px; border-radius: 8px; }");
            writer.println("        h1 { color: #333; border-bottom: 2px solid #4CAF50; padding-bottom: 10px; }");
            writer.println("        .timestamp { color: #666; margin-bottom: 20px; }");
            writer.println("        .summary { display: flex; gap: 20px; margin: 20px 0; flex-wrap: wrap; }");
            writer.println("        .stat { padding: 15px 25px; background: #e8f5e9; border-radius: 5px; text-align: center; }");
            writer.println("        .stat.warning { background: #fff8e1; }");
            writer.println("        .stat.failed { background: #ffebee; }");
            writer.println("        .stat-number { font-size: 32px; font-weight: bold; }");
            writer.println("        .stat-label { color: #666; }");
            writer.println("        .filters { margin: 20px 0; padding: 16px; background: #fafafa; border: 1px solid #eee; border-radius: 6px; }");
            writer.println("        .filter-group { margin-bottom: 12px; }");
            writer.println("        .filter-group:last-child { margin-bottom: 0; }");
            writer.println("        .filter-title { font-weight: bold; margin-bottom: 8px; color: #333; }");
            writer.println("        .filter-options { display: flex; flex-wrap: wrap; gap: 12px; }");
            writer.println("        .filter-options label { display: inline-flex; align-items: center; gap: 6px; color: #555; }");
            writer.println("        .file { margin: 20px 0; border: 1px solid #ddd; border-radius: 5px; overflow: hidden; }");
            writer.println("        .file-header { padding: 12px 15px; background: #f5f5f5; }");
            writer.println("        .file-header.passed { background: #e8f5e9; }");
            writer.println("        .file-header.warning { background: #fff8e1; }");
            writer.println("        .file-header.failed { background: #ffebee; }");
            writer.println("        .file-name { font-weight: bold; font-size: 16px; }");
            writer.println("        .file-path { color: #666; font-size: 12px; margin-top: 5px; }");
            writer.println("        .file-meta { color: #666; font-size: 12px; margin-top: 5px; }");
            writer.println("        .issue { padding: 10px 15px; border-bottom: 1px solid #eee; }");
            writer.println("        .issue:last-child { border-bottom: none; }");
            writer.println("        .issue-header { display: flex; gap: 10px; align-items: center; margin-bottom: 5px; flex-wrap: wrap; }");
            writer.println("        .line-num { background: #f0f0f0; padding: 2px 8px; border-radius: 3px; font-size: 12px; color: #666; min-width: 50px; text-align: center; }");
            writer.println("        .issue-type { padding: 2px 8px; border-radius: 3px; font-size: 12px; }");
            writer.println("        .issue-type.ERROR { background: #ffcdd2; color: #c62828; }");
            writer.println("        .issue-type.WARNING { background: #fff3e0; color: #ef6c00; }");
            writer.println("        .issue-type.INFO { background: #e3f2fd; color: #1565c0; }");
            writer.println("        .issue-message { color: #333; }");
            writer.println("        .source-line { margin-top: 8px; background: #f8f8f8; border-left: 3px solid #d0d0d0; padding: 8px 10px; font-family: Menlo, Consolas, monospace; font-size: 12px; color: #444; white-space: pre-wrap; word-break: break-all; }");
            writer.println("        .passed-icon { color: #4CAF50; }");
            writer.println("    </style>");
            writer.println("</head>");
            writer.println("<body>");
            writer.println("    <div class=\"container\">");
            writer.println("        <h1>SQL/Apollo配置检查报告</h1>");
            writer.println("        <p class=\"timestamp\">生成时间: " + timestamp + "</p>");
            writer.println("        <div class=\"summary\">");
            writeStat(writer, "", summary.totalFileResults, "文件检查结果");
            writeStat(writer, "", summary.passedFiles, "无ERROR文件");
            writeStat(writer, "warning", summary.warningOnlyFiles, "仅WARNING文件");
            writeStat(writer, "failed", summary.problemFiles, "存在ERROR文件");
            writeStat(writer, "failed", summary.scanErrors, "扫描错误");
            writeStat(writer, "", summary.totalIssues, "问题总数");
            writer.println("        </div>");
            writeFilters(writer, issueTypes);

            for (CheckResult result : results) {
                writer.println("        <div class=\"file\" data-file-block>");
                writer.println("            <div class=\"file-header " + getHeaderClass(result) + "\">");
                writer.println("                <div class=\"file-name\">" + escapeHtml(result.getFileName()) + " <span style=\"font-weight:normal;\">(" + escapeHtml(result.getFileType()) + ")</span></div>");
                writer.println("                <div class=\"file-path\">" + escapeHtml(result.getFilePath()) + "</div>");
                writer.println("                <div class=\"file-meta\">resultType=" + escapeHtml(result.getResultType()) + ", errors=" + result.getErrorCount() + ", warnings=" + result.getWarningCount() + ", info=" + result.getInfoCount() + "</div>");
                writer.println("            </div>");

                if (result.getIssues().isEmpty()) {
                    writer.println("            <div class=\"issue\">");
                    writer.println("                <span class=\"passed-icon\">✓ 检查通过</span>");
                    writer.println("            </div>");
                } else {
                    for (CheckResult.Issue issue : result.getIssues()) {
                        writer.println("            <div class=\"issue\" data-issue data-severity=\"" + escapeHtml(issue.getSeverity()) + "\" data-type=\"" + escapeHtml(issue.getType()) + "\">");
                        writer.println("                <div class=\"issue-header\">");
                        writer.println("                    <span class=\"line-num\">行 " + (issue.getLine() > 0 ? issue.getLine() : "-") + "</span>");
                        writer.println("                    <span class=\"issue-type " + issue.getSeverity() + "\">" + escapeHtml(issue.getSeverity()) + "</span>");
                        writer.println("                    <span class=\"issue-type\" style=\"background:#eee;\">" + escapeHtml(issue.getType()) + "</span>");
                        writer.println("                </div>");
                        writer.println("                <div class=\"issue-message\">" + escapeHtml(issue.getMessage()) + "</div>");
                        if (issue.getSourceLineText() != null && !issue.getSourceLineText().isEmpty()) {
                            writer.println("                <div class=\"source-line\">" + escapeHtml(issue.getSourceLineText()) + "</div>");
                        }
                        writer.println("            </div>");
                    }
                }
                writer.println("        </div>");
            }

            writer.println("    </div>");
            writeFilterScript(writer);
            writer.println("</body>");
            writer.println("</html>");

            System.out.println("HTML报告已生成: " + outputPath);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void writeStat(PrintWriter writer, String cssClass, int value, String label) {
        writer.println("            <div class=\"stat " + cssClass + "\">");
        writer.println("                <div class=\"stat-number\">" + value + "</div>");
        writer.println("                <div class=\"stat-label\">" + label + "</div>");
        writer.println("            </div>");
    }

    private void writeFilters(PrintWriter writer, Set<String> issueTypes) {
        writer.println("        <div class=\"filters\">");
        writer.println("            <div class=\"filter-group\">");
        writer.println("                <div class=\"filter-title\">按严重级别过滤</div>");
        writer.println("                <div class=\"filter-options\">");
        writeFilterOption(writer, "severity", "ERROR", true);
        writeFilterOption(writer, "severity", "WARNING", false);
        writeFilterOption(writer, "severity", "INFO", false);
        writer.println("                </div>");
        writer.println("            </div>");
        writer.println("            <div class=\"filter-group\">");
        writer.println("                <div class=\"filter-title\">按问题类型过滤</div>");
        writer.println("                <div class=\"filter-options\">");
        for (String issueType : issueTypes) {
            writeFilterOption(writer, "type", issueType, true);
        }
        writer.println("                </div>");
        writer.println("            </div>");
        writer.println("        </div>");
    }

    private void writeFilterOption(PrintWriter writer, String filterName, String value, boolean checked) {
        writer.println("                    <label><input type=\"checkbox\" class=\"filter-checkbox\" data-filter-name=\"" + filterName + "\" value=\"" + escapeHtml(value) + "\"" + (checked ? " checked" : "") + ">" + escapeHtml(value) + "</label>");
    }

    private void writeFilterScript(PrintWriter writer) {
        writer.println("    <script>");
        writer.println("        (function() {");
        writer.println("            const checkboxes = Array.from(document.querySelectorAll('.filter-checkbox')); ");
        writer.println("            const fileBlocks = Array.from(document.querySelectorAll('[data-file-block]')); ");
        writer.println("            function selectedValues(name) {");
        writer.println("                return new Set(checkboxes.filter(function(box) { return box.dataset.filterName === name && box.checked; }).map(function(box) { return box.value; }));");
        writer.println("            }");
        writer.println("            function applyFilters() {");
        writer.println("                const severities = selectedValues('severity');");
        writer.println("                const types = selectedValues('type');");
        writer.println("                fileBlocks.forEach(function(fileBlock) {");
        writer.println("                    const issues = Array.from(fileBlock.querySelectorAll('[data-issue]')); ");
        writer.println("                    if (issues.length === 0) {");
        writer.println("                        fileBlock.style.display = ''; ");
        writer.println("                        return; ");
        writer.println("                    }");
        writer.println("                    let visibleCount = 0;");
        writer.println("                    issues.forEach(function(issue) {");
        writer.println("                        const matchesSeverity = severities.has(issue.dataset.severity);");
        writer.println("                        const matchesType = types.has(issue.dataset.type);");
        writer.println("                        const visible = matchesSeverity && matchesType;");
        writer.println("                        issue.style.display = visible ? '' : 'none';");
        writer.println("                        if (visible) { visibleCount++; }");
        writer.println("                    });");
        writer.println("                    fileBlock.style.display = visibleCount > 0 ? '' : 'none';");
        writer.println("                });");
        writer.println("            }");
        writer.println("            checkboxes.forEach(function(box) { box.addEventListener('change', applyFilters); });");
        writer.println("            applyFilters();");
        writer.println("        })();");
        writer.println("    </script>");
    }

    private Set<String> collectIssueTypes(List<CheckResult> results) {
        Set<String> issueTypes = new LinkedHashSet<>();
        for (CheckResult result : results) {
            for (CheckResult.Issue issue : result.getIssues()) {
                issueTypes.add(issue.getType());
            }
        }
        return issueTypes;
    }

    private String getHeaderClass(CheckResult result) {
        if (result.isScanError() || !result.isPassed()) {
            return "failed";
        }
        if (result.hasWarningsOnly()) {
            return "warning";
        }
        return "passed";
    }

    private String escapeHtml(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    public void generateJsonReport(List<CheckResult> results, String outputPath) {
        Summary summary = new Summary(results);
        try (PrintWriter writer = new PrintWriter(new OutputStreamWriter(Files.newOutputStream(Paths.get(outputPath)), StandardCharsets.UTF_8))) {
            writer.println("{");
            writer.println("  \"timestamp\": \"" + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()) + "\",");
            writer.println("  \"summary\": {");
            writer.println("    \"totalFileResults\": " + summary.totalFileResults + ",");
            writer.println("    \"passedFiles\": " + summary.passedFiles + ",");
            writer.println("    \"warningOnlyFiles\": " + summary.warningOnlyFiles + ",");
            writer.println("    \"problemFiles\": " + summary.problemFiles + ",");
            writer.println("    \"scanErrors\": " + summary.scanErrors + ",");
            writer.println("    \"totalIssues\": " + summary.totalIssues);
            writer.println("  },");
            writer.println("  \"results\": [");

            for (int i = 0; i < results.size(); i++) {
                CheckResult r = results.get(i);
                writer.println("    {");
                writer.println("      \"fileName\": \"" + escapeJson(r.getFileName()) + "\",");
                writer.println("      \"filePath\": \"" + escapeJson(r.getFilePath()) + "\",");
                writer.println("      \"fileType\": \"" + escapeJson(r.getFileType()) + "\",");
                writer.println("      \"resultType\": \"" + escapeJson(r.getResultType()) + "\",");
                writer.println("      \"passed\": " + r.isPassed() + ",");
                writer.println("      \"errorCount\": " + r.getErrorCount() + ",");
                writer.println("      \"warningCount\": " + r.getWarningCount() + ",");
                writer.println("      \"infoCount\": " + r.getInfoCount() + ",");
                writer.println("      \"issues\": [");

                for (int j = 0; j < r.getIssues().size(); j++) {
                    CheckResult.Issue issue = r.getIssues().get(j);
                    writer.println("        {");
                    writer.println("          \"line\": " + issue.getLine() + ",");
                    writer.println("          \"type\": \"" + escapeJson(issue.getType()) + "\",");
                    writer.println("          \"severity\": \"" + escapeJson(issue.getSeverity()) + "\",");
                    writer.println("          \"message\": \"" + escapeJson(issue.getMessage()) + "\",");
                    if (issue.getSourceLineText() == null) {
                        writer.println("          \"sourceLineText\": null");
                    } else {
                        writer.println("          \"sourceLineText\": \"" + escapeJson(issue.getSourceLineText()) + "\"");
                    }
                    writer.println("        }" + (j < r.getIssues().size() - 1 ? "," : ""));
                }

                writer.println("      ]");
                writer.println("    }" + (i < results.size() - 1 ? "," : ""));
            }

            writer.println("  ]");
            writer.println("}");

            System.out.println("JSON报告已生成: " + outputPath);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private String escapeJson(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private static class Summary {
        private final int totalFileResults;
        private final int passedFiles;
        private final int warningOnlyFiles;
        private final int problemFiles;
        private final int scanErrors;
        private final int totalIssues;

        private Summary(List<CheckResult> results) {
            int totalFileResults = 0;
            int passedFiles = 0;
            int warningOnlyFiles = 0;
            int problemFiles = 0;
            int scanErrors = 0;
            int totalIssues = 0;
            for (CheckResult result : results) {
                totalIssues += result.getIssues().size();
                if (result.isScanError()) {
                    scanErrors++;
                    continue;
                }
                totalFileResults++;
                if (!result.isPassed()) {
                    problemFiles++;
                } else if (result.hasWarningsOnly()) {
                    warningOnlyFiles++;
                } else {
                    passedFiles++;
                }
            }
            this.totalFileResults = totalFileResults;
            this.passedFiles = passedFiles;
            this.warningOnlyFiles = warningOnlyFiles;
            this.problemFiles = problemFiles;
            this.scanErrors = scanErrors;
            this.totalIssues = totalIssues;
        }
    }
}
