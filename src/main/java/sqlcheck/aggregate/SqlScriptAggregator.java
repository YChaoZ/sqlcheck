package sqlcheck.aggregate;

import sqlcheck.config.SqlCheckProperties;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class SqlScriptAggregator {

    private final SqlCheckProperties properties;
    private final Pattern fileNamePattern;

    public SqlScriptAggregator(SqlCheckProperties properties) {
        this.properties = properties;
        this.fileNamePattern = Pattern.compile(properties.getAggregationFileNamePattern(), Pattern.CASE_INSENSITIVE);
    }

    public List<Path> aggregate() {
        Path inputDir = properties.resolveAggregationInputDir();
        Path outputDir = properties.resolveAggregationOutputDir();
        Path apolloDir = properties.resolveApolloDir();

        if (!Files.isDirectory(inputDir)) {
            System.out.println("整合输入目录不存在，跳过整合: " + inputDir);
            return new ArrayList<>();
        }
        createDirectoryIfNeeded(outputDir);

        List<ScriptFile> files = scanFiles(inputDir, outputDir);
        Map<GroupKey, List<ScriptFile>> grouped = groupFiles(files);

        List<Path> outputs = new ArrayList<>();
        for (Map.Entry<GroupKey, List<ScriptFile>> entry : grouped.entrySet()) {
            GroupKey key = entry.getKey();
            Path subOutputDir = key.subDir.isEmpty() ? outputDir : outputDir.resolve(key.subDir);
            createDirectoryIfNeeded(subOutputDir);
            Path outputFile = subOutputDir.resolve(key.database + "_" + key.type + ".sql");
            writeOutput(outputFile, entry.getValue());
            outputs.add(outputFile);
        }

        copyApolloDirectory(apolloDir, outputDir);
        return outputs;
    }

    private List<ScriptFile> scanFiles(Path inputDir, Path outputDir) {
        List<ScriptFile> files = new ArrayList<>();
        try {
            Files.walk(inputDir)
                .filter(Files::isRegularFile)
                .filter(path -> path.getFileName().toString().toLowerCase().endsWith(".sql"))
                .sorted(Comparator.comparing(path -> path.toAbsolutePath().normalize().toString()))
                .forEach(path -> {
                    if (shouldSkip(path)) {
                        copySkippedFile(path, inputDir, outputDir);
                        return;
                    }
                    Matcher matcher = fileNamePattern.matcher(path.getFileName().toString());
                    if (!matcher.matches()) {
                        throw new IllegalStateException("文件名不符合整合规范: " + path.getFileName());
                    }
                    Path relativePath = inputDir.relativize(path);
                    String subDir = relativePath.getParent() == null ? "" : relativePath.getParent().toString();
                    files.add(new ScriptFile(path, matcher.group("database"), matcher.group("type").toLowerCase(), matcher.group("submitter"), subDir));
                });
        } catch (IOException e) {
            throw new IllegalStateException("扫描脚本目录失败: " + inputDir, e);
        }
        return files;
    }

    private Map<GroupKey, List<ScriptFile>> groupFiles(List<ScriptFile> files) {
        Map<GroupKey, List<ScriptFile>> grouped = new LinkedHashMap<>();
        for (ScriptFile file : files) {
            GroupKey key = new GroupKey(file.subDir, file.database, file.type);
            grouped.computeIfAbsent(key, ignored -> new ArrayList<>()).add(file);
        }
        return grouped;
    }

    private void writeOutput(Path outputFile, List<ScriptFile> files) {
        LinkedHashSet<String> submitterOrder = new LinkedHashSet<>();
        for (ScriptFile file : files) {
            submitterOrder.add(file.submitter);
        }

        Map<String, List<ScriptFile>> bySubmitter = new LinkedHashMap<>();
        for (String submitter : submitterOrder) {
            bySubmitter.put(submitter, new ArrayList<>());
        }
        for (ScriptFile file : files) {
            bySubmitter.get(file.submitter).add(file);
        }

        List<String> lines = new ArrayList<>();
        for (Map.Entry<String, List<ScriptFile>> entry : bySubmitter.entrySet()) {
            lines.add("-- " + entry.getKey());
            for (ScriptFile file : entry.getValue()) {
                try {
                    String content = new String(Files.readAllBytes(file.path), StandardCharsets.UTF_8).trim();
                    if (!content.isEmpty()) {
                        lines.add(content);
                        if (!content.endsWith(";")) {
                            lines.add(";");
                        }
                    }
                } catch (IOException e) {
                    throw new IllegalStateException("读取脚本失败: " + file.path, e);
                }
            }
        }

        try {
            Files.write(outputFile, String.join(System.lineSeparator(), lines).concat(System.lineSeparator()).getBytes(StandardCharsets.UTF_8),
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        } catch (IOException e) {
            throw new IllegalStateException("写入整合结果失败: " + outputFile, e);
        }
    }

    private void copySkippedFile(Path source, Path inputDir, Path outputDir) {
        Path relative = inputDir.relativize(source);
        Path target = outputDir.resolve(relative);
        try {
            Files.createDirectories(target.getParent());
            Files.copy(source, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new IllegalStateException("复制跳过文件失败: " + source, e);
        }
    }

    private boolean shouldSkip(Path path) {
        String fileName = path.getFileName().toString();
        for (String prefix : properties.getAggregationSkipPrefixes()) {
            if (prefix != null && !prefix.isEmpty() && fileName.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private void validateDirectory(Path dir) {
        if (!Files.isDirectory(dir)) {
            throw new IllegalStateException("整合输入目录不存在或不是目录: " + dir);
        }
        if (!Files.isReadable(dir)) {
            throw new IllegalStateException("整合输入目录不可读: " + dir);
        }
    }

    private void createDirectoryIfNeeded(Path dir) {
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new IllegalStateException("无法创建整合输出目录: " + dir, e);
        }
    }

    private void copyApolloDirectory(Path apolloDir, Path outputDir) {
        if (!Files.isDirectory(apolloDir)) {
            return;
        }
        Path targetDir = outputDir.resolve("apollo");
        try (Stream<Path> stream = Files.walk(apolloDir)) {
            stream.forEach(source -> {
                Path target = targetDir.resolve(apolloDir.relativize(source).toString());
                try {
                    if (Files.isDirectory(source)) {
                        Files.createDirectories(target);
                    } else {
                        Files.createDirectories(target.getParent());
                        Files.copy(source, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    }
                } catch (IOException e) {
                    throw new IllegalStateException("复制Apollo目录失败: " + source, e);
                }
            });
        } catch (IOException e) {
            throw new IllegalStateException("读取Apollo目录失败: " + apolloDir, e);
        }
    }

    private static class ScriptFile {
        private final Path path;
        private final String database;
        private final String type;
        private final String submitter;
        private final String subDir;

        private ScriptFile(Path path, String database, String type, String submitter, String subDir) {
            this.path = path;
            this.database = database;
            this.type = type;
            this.submitter = submitter;
            this.subDir = subDir;
        }
    }

    private static class GroupKey {
        private final String subDir;
        private final String database;
        private final String type;

        private GroupKey(String subDir, String database, String type) {
            this.subDir = subDir;
            this.database = database;
            this.type = type;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof GroupKey)) {
                return false;
            }
            GroupKey groupKey = (GroupKey) o;
            return Objects.equals(subDir, groupKey.subDir) && Objects.equals(database, groupKey.database) && Objects.equals(type, groupKey.type);
        }

        @Override
        public int hashCode() {
            return Objects.hash(subDir, database, type);
        }
    }
}
