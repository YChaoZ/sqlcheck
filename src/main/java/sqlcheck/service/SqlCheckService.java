package sqlcheck.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import sqlcheck.aggregate.SqlScriptAggregator;
import sqlcheck.checker.ApolloChecker;
import sqlcheck.checker.SqlChecker;
import sqlcheck.config.SqlCheckProperties;
import sqlcheck.model.CheckResult;
import sqlcheck.report.ReportGenerator;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

@Service
public class SqlCheckService {

    private static final List<String> SQL_EXTENSIONS = Collections.singletonList(".sql");
    private static final List<String> APOLLO_EXTENSIONS = Arrays.asList(".sql", ".json", ".yaml", ".yml");

    @Autowired
    private SqlCheckProperties properties;

    public void run() {
        System.out.println("\n=== SQL/Apollo配置检查工具 ===\n");
        printResolvedConfig();

        cleanDirectory(properties.resolveOutputDir());
        cleanDirectory(properties.resolveAggregationOutputDir());

        List<CheckResult> allResults = new ArrayList<>();
        checkSqlFiles(allResults);
        checkApolloFiles(allResults);
        generateReports(allResults);
        aggregateScriptsIfClean(allResults);
        printSummary(allResults);
    }

    private void printResolvedConfig() {
        System.out.println("当前配置:");
        printResolvedPath("SQL目录", properties.getSqlDir(), properties.resolveSqlDir());
        printResolvedPath("Apollo目录", properties.getApolloDir(), properties.resolveApolloDir());
        printResolvedPath("报告目录", "(自动推导)", properties.resolveOutputDir());
        printResolvedPath("整合目录", "(自动推导)", properties.resolveAggregationOutputDir());
        System.out.println();
    }

    private void printResolvedPath(String label, String configured, Path resolved) {
        System.out.println("  " + label + ": " + configured + " -> " + resolved);
    }

    private void checkSqlFiles(List<CheckResult> allResults) {
        Path sqlDir = properties.resolveSqlDir();
        System.out.println("检查SQL目录: " + sqlDir);
        List<File> files = collectFiles(sqlDir, "SQL", SQL_EXTENSIONS, allResults);
        SqlChecker sqlChecker = new SqlChecker(properties);
        if (files.isEmpty()) {
            System.out.println("  (无SQL文件或扫描失败)");
        }
        for (File file : files) {
            System.out.println("  检查: " + file.getAbsolutePath());
            allResults.addAll(sqlChecker.checkFile(file));
        }
    }

    private void checkApolloFiles(List<CheckResult> allResults) {
        Path apolloDir = properties.resolveApolloDir();
        System.out.println("\n检查Apollo配置目录: " + apolloDir);
        List<File> files = collectFiles(apolloDir, "APOLLO", APOLLO_EXTENSIONS, allResults);
        ApolloChecker apolloChecker = new ApolloChecker();
        if (files.isEmpty()) {
            System.out.println("  (无配置文件或扫描失败)");
        }
        for (File file : files) {
            System.out.println("  检查: " + file.getAbsolutePath());
            allResults.addAll(apolloChecker.checkFile(file));
        }
    }

    private List<File> collectFiles(Path rootDir, String fileType, List<String> extensions, List<CheckResult> allResults) {
        CheckResult rootValidation = validateDirectory(rootDir, fileType);
        if (rootValidation != null) {
            allResults.add(rootValidation);
            System.out.println("  扫描失败: " + rootValidation.getIssues().get(0).getMessage());
            return Collections.emptyList();
        }

        List<File> files = new ArrayList<>();
        collectFilesRecursively(rootDir, rootDir, fileType, extensions, allResults, files);
        files.sort(Comparator.comparing(file -> file.getAbsoluteFile().toPath().normalize().toString()));
        return files;
    }

    private void collectFilesRecursively(Path rootDir, Path currentDir, String fileType, List<String> extensions,
                                         List<CheckResult> allResults, List<File> files) {
        CheckResult validation = validateDirectory(currentDir, fileType);
        if (validation != null) {
            if (!rootDir.equals(currentDir)) {
                allResults.add(validation);
                System.out.println("  子目录扫描失败: " + validation.getFilePath() + " - " + validation.getIssues().get(0).getMessage());
            }
            return;
        }

        File[] children = currentDir.toFile().listFiles();
        if (children == null) {
            CheckResult result = CheckResult.scanError(currentDir.getFileName() == null ? currentDir.toString() : currentDir.getFileName().toString(),
                    currentDir.toAbsolutePath().normalize().toString(), fileType,
                    "目录无法列举，可能无读取权限或I/O异常");
            allResults.add(result);
            System.out.println("  子目录扫描失败: " + result.getFilePath() + " - " + result.getIssues().get(0).getMessage());
            return;
        }

        Arrays.sort(children, Comparator.comparing(file -> file.getAbsoluteFile().toPath().normalize().toString()));
        for (File child : children) {
            Path childPath = child.toPath().toAbsolutePath().normalize();
            if (child.isDirectory()) {
                collectFilesRecursively(rootDir, childPath, fileType, extensions, allResults, files);
            } else if (matchesExtension(child.getName(), extensions)) {
                files.add(childPath.toFile());
            }
        }
    }

    private CheckResult validateDirectory(Path dir, String fileType) {
        Path normalized = dir.toAbsolutePath().normalize();
        File directory = normalized.toFile();
        if (!directory.exists()) {
            return CheckResult.scanError(directory.getName().isEmpty() ? normalized.toString() : directory.getName(), normalized.toString(), fileType,
                    "目录不存在");
        }
        if (!directory.isDirectory()) {
            return CheckResult.scanError(directory.getName().isEmpty() ? normalized.toString() : directory.getName(), normalized.toString(), fileType,
                    "路径不是目录");
        }
        if (!Files.isReadable(normalized) || !directory.canRead()) {
            return CheckResult.scanError(directory.getName().isEmpty() ? normalized.toString() : directory.getName(), normalized.toString(), fileType,
                    "目录不可读");
        }
        try {
            normalized.toRealPath();
        } catch (IOException e) {
            return CheckResult.scanError(directory.getName().isEmpty() ? normalized.toString() : directory.getName(), normalized.toString(), fileType,
                    "目录解析失败: " + e.getMessage());
        }
        return null;
    }

    private boolean matchesExtension(String fileName, List<String> extensions) {
        String lowerName = fileName.toLowerCase();
        for (String extension : extensions) {
            if (lowerName.endsWith(extension)) {
                return true;
            }
        }
        return false;
    }

    private void generateReports(List<CheckResult> allResults) {
        Path outputDir = properties.resolveOutputDir();
        try {
            Files.createDirectories(outputDir);
        } catch (IOException e) {
            throw new IllegalStateException("无法创建报告目录: " + outputDir, e);
        }
        ReportGenerator reporter = new ReportGenerator();
        String htmlPath = outputDir.resolve("report.html").toString();
        String jsonPath = outputDir.resolve("report.json").toString();
        reporter.generateHtmlReport(allResults, htmlPath);
        reporter.generateJsonReport(allResults, jsonPath);
    }

    private void aggregateScriptsIfClean(List<CheckResult> allResults) {
        boolean hasErrors = allResults.stream()
            .filter(result -> "SQL".equalsIgnoreCase(result.getFileType()))
            .anyMatch(result -> result.isScanError() || !result.isPassed());
        if (hasErrors) {
            System.out.println("\n检测到SQL ERROR，跳过脚本整合。");
            return;
        }
        try {
            SqlScriptAggregator aggregator = new SqlScriptAggregator(properties);
            List<Path> outputs = aggregator.aggregate();
            if (outputs.isEmpty()) {
                System.out.println("\n未发现可整合脚本。");
                return;
            }
            System.out.println("\n脚本整合结果:");
            for (Path output : outputs) {
                System.out.println("  " + output);
            }
        } catch (IllegalStateException e) {
            System.out.println("\n脚本整合失败: " + e.getMessage());
        }
    }

    private void cleanDirectory(Path dir) {
        if (!Files.isDirectory(dir)) {
            return;
        }
        try (Stream<Path> stream = Files.walk(dir)) {
            stream.sorted(Comparator.reverseOrder())
                .filter(p -> !p.equals(dir))
                .forEach(p -> {
                    try {
                        Files.delete(p);
                    } catch (IOException ignored) {
                    }
                });
        } catch (IOException ignored) {
        }
    }

    private void printSummary(List<CheckResult> allResults) {
        long scanErrors = allResults.stream().filter(CheckResult::isScanError).count();
        long fileResults = allResults.stream().filter(result -> !result.isScanError()).count();
        long passed = allResults.stream().filter(result -> !result.isScanError() && result.isPassed()).count();
        long failed = allResults.stream().filter(result -> !result.isScanError() && !result.isPassed()).count();
        long issues = allResults.stream().mapToInt(result -> result.getIssues().size()).sum();

        System.out.println("\n=== 检查完成 ===");
        System.out.println("文件结果: " + fileResults + ", 无ERROR: " + passed + ", 存在ERROR: " + failed + ", 扫描错误: " + scanErrors + ", 问题总数: " + issues);
        System.out.println("报告已生成:");
        System.out.println("  HTML: " + properties.resolveOutputDir().resolve("report.html"));
        System.out.println("  JSON: " + properties.resolveOutputDir().resolve("report.json"));
        System.out.println("  整合输出: " + properties.resolveAggregationOutputDir());    }
}
