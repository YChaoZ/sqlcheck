package sqlcheck.packaging;

import sqlcheck.config.SqlCheckProperties;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;

public class PackageHandler {

    private final SqlCheckProperties properties;
    private static final Pattern TABLE_PATTERN = Pattern.compile(
        "\\b(INSERT\\s+INTO|REPLACE\\s+INTO|UPDATE|DELETE\\s+FROM|TRUNCATE\\s+TABLE)\\s+`?([a-zA-Z_][a-zA-Z0-9_$]*\\.)?([a-zA-Z_][a-zA-Z0-9_$]*)`?",
        Pattern.CASE_INSENSITIVE
    );
    private static final Pattern FILE_NAME_DB_PATTERN = Pattern.compile("^([a-zA-Z0-9_]+)_(ddl|dml)", Pattern.CASE_INSENSITIVE);

    public PackageHandler(SqlCheckProperties properties) {
        this.properties = properties;
    }

    public void packageResults() {
        System.out.println("\n=== 打包配置 ===");
        System.out.println("  enabled: " + properties.getPackageConfig().isEnabled());
        System.out.println("  zipName: " + properties.getPackageConfig().getZipName());
        System.out.println("  tarName: " + properties.getPackageConfig().getTarName());
        System.out.println("  sourceDir: " + properties.getPackageConfig().getSourceDir());
        
        if (!properties.getPackageConfig().isEnabled()) {
            System.out.println("\n打包功能未启用，跳过打包。");
            return;
        }

        SqlCheckProperties.PackageConfig config = properties.getPackageConfig();
        Path aggregationDir = properties.resolveAggregationOutputDir();
        Path apolloDir = properties.resolveApolloDir();
        Path sourceDir = properties.resolvePackageSourceDir();

        if (!Files.isDirectory(aggregationDir)) {
            System.out.println("\n聚合目录不存在，跳过打包: " + aggregationDir);
            return;
        }

        String tarName = config.getTarName();
        String zipName = config.getZipName();
        String timestamp = extractTimestamp(tarName);

        try {
            Path workDir = Files.createTempDirectory("sqlcheck-package-");
            System.out.println("\n打包工作目录: " + workDir);

            Path tarDir = workDir.resolve(tarName + ".tar");
            Path tarContentDir = workDir.resolve(timestamp);
            Path updDir = tarContentDir.resolve("upd");
            Path bakDir = tarContentDir.resolve("bak");

            Files.createDirectories(updDir);
            Files.createDirectories(bakDir);

            copyAggregationFiles(aggregationDir, updDir);
            generateUpdSqlList(aggregationDir, updDir.resolve("upd_sql_list.txt"));
            generateTableList(aggregationDir, bakDir.resolve("table_list.txt"));

            if (sourceDir != null && Files.isDirectory(sourceDir)) {
                copyScriptFile(sourceDir, updDir, "upd.sh");
                copyScriptFile(sourceDir, bakDir, "bak.sh");
            }

            createTarArchive(tarContentDir, tarDir);

            Path zipFileName = Paths.get(zipName + ".zip");
            Path outputFolder = aggregationDir.resolve(zipName);
            Files.createDirectories(outputFolder);
            Path zipFile = outputFolder.resolve(zipFileName);

            try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFile.toFile()))) {
                addToZip(zos, apolloDir, "apollo");
                addToZip(zos, tarDir, tarName + ".tar");
            }

            String md5 = calculateMD5(zipFile);
            Path md5File = outputFolder.resolve(zipName + ".zip_md5");
            Files.write(md5File, md5.getBytes(StandardCharsets.UTF_8));

            System.out.println("\n打包完成: " + zipFile);
            System.out.println("MD5: " + md5);

            deleteDirectory(workDir);

        } catch (Exception e) {
            System.out.println("\n打包失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private String extractTimestamp(String tarName) {
        Matcher m = Pattern.compile("(\\d{8,})").matcher(tarName);
        return m.find() ? m.group(1) : "00000000";
    }

    private void generateUpdSqlList(Path aggregationDir, Path outputFile) throws IOException {
        List<String> ddlFiles = new ArrayList<>();
        List<String> dmlFiles = new ArrayList<>();

        try (Stream<Path> stream = Files.walk(aggregationDir)) {
            stream.filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith(".sql"))
                .filter(p -> !p.getFileName().toString().equals("apollo"))
                .forEach(path -> {
                    String fileName = path.getFileName().toString();
                    if (fileName.toLowerCase().contains("_ddl")) {
                        ddlFiles.add(fileName.replace(".sql", ""));
                    } else if (fileName.toLowerCase().contains("_dml")) {
                        dmlFiles.add(fileName.replace(".sql", ""));
                    }
                });
        }

        List<String> allFiles = new ArrayList<>();
        allFiles.addAll(ddlFiles);
        allFiles.addAll(dmlFiles);

        String content = String.join("\n", allFiles) + "\n";
        Files.write(outputFile, content.getBytes(StandardCharsets.UTF_8));
        System.out.println("生成 upd_sql.list.txt: " + outputFile);
    }

    private void generateTableList(Path aggregationDir, Path outputFile) throws IOException {
        Set<String> tables = new LinkedHashSet<>();

        try (Stream<Path> stream = Files.walk(aggregationDir)) {
            stream.filter(Files::isRegularFile)
                .filter(p -> p.toString().toLowerCase().endsWith(".sql"))
                .filter(p -> p.getFileName().toString().toLowerCase().contains("_dml"))
                .forEach(path -> {
                    try {
                        String content = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
                        String fileName = path.getFileName().toString();
                        Matcher nameMatcher = FILE_NAME_DB_PATTERN.matcher(fileName);
                        String defaultDatabase = "";
                        if (nameMatcher.find()) {
                            defaultDatabase = nameMatcher.group(1);
                        }

                        Matcher matcher = TABLE_PATTERN.matcher(content);
                        while (matcher.find()) {
                            String dbInSql = matcher.group(2);
                            String table = matcher.group(3);
                            if (table != null && !table.isEmpty()) {
                                String fullName;
                                if (dbInSql != null && !dbInSql.isEmpty()) {
                                    fullName = dbInSql + "." + table;
                                } else {
                                    fullName = defaultDatabase.isEmpty() ? table : defaultDatabase + "." + table;
                                }
                                tables.add(fullName);
                            }
                        }
                    } catch (IOException e) {
                        System.out.println("读取文件失败: " + path + " - " + e.getMessage());
                    }
                });
        }

        String content = String.join("\n", tables) + "\n";
        Files.write(outputFile, content.getBytes(StandardCharsets.UTF_8));
        System.out.println("生成 table_list.txt: " + outputFile);
    }

    private void copyAggregationFiles(Path aggregationDir, Path updDir) throws IOException {
        try (Stream<Path> stream = Files.walk(aggregationDir)) {
            stream.filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith(".sql"))
                .forEach(sourceFile -> {
                    try {
                        String content = new String(Files.readAllBytes(sourceFile), StandardCharsets.UTF_8);
                        content = content.replace("\r\n", "\n").replace("\r", "\n");
                        Path targetFile = updDir.resolve(sourceFile.getFileName().toString());
                        Files.write(targetFile, content.getBytes(StandardCharsets.UTF_8));
                        System.out.println("复制 SQL 文件: " + sourceFile.getFileName() + " 到 upd");
                    } catch (IOException e) {
                        System.out.println("复制文件失败: " + sourceFile + " - " + e.getMessage());
                    }
                });
        }
    }

    private void copyScriptFile(Path sourceDir, Path targetDir, String fileName) {
        Path sourceFile = sourceDir.resolve(fileName);
        if (Files.exists(sourceFile)) {
            try {
                String content = new String(Files.readAllBytes(sourceFile), StandardCharsets.UTF_8);
                content = content.replace("\r\n", "\n").replace("\r", "\n");
                Files.write(targetDir.resolve(fileName), content.getBytes(StandardCharsets.UTF_8));
                System.out.println("复制 " + fileName + " 到 " + targetDir);
            } catch (IOException e) {
                System.out.println("复制 " + fileName + " 失败: " + e.getMessage());
            }
        } else {
            System.out.println("源文件不存在: " + sourceFile);
        }
    }

    private void createTarArchive(Path sourceDir, Path targetFile) throws IOException {
        try (TarArchiveOutputStream tarOut = new TarArchiveOutputStream(new FileOutputStream(targetFile.toFile()))) {
            tarOut.setLongFileMode(TarArchiveOutputStream.LONGFILE_GNU);
            addToTar(tarOut, sourceDir, sourceDir.getFileName().toString());
            tarOut.finish();
        }
    }

    private void addToTar(TarArchiveOutputStream tarOut, Path source, String basePath) throws IOException {
        if (Files.isDirectory(source)) {
            try (Stream<Path> stream = Files.list(source)) {
                List<Path> paths = stream.collect(java.util.stream.Collectors.toList());
                for (Path path : paths) {
                    String name = basePath + "/" + path.getFileName().toString();
                    addToTar(tarOut, path, name);
                }
            }
        } else {
            TarArchiveEntry entry = new TarArchiveEntry(source.toFile(), basePath);
            tarOut.putArchiveEntry(entry);
            Files.copy(source, tarOut);
            tarOut.closeArchiveEntry();
        }
    }

    private void addToZip(ZipOutputStream zos, Path source, String entryName) throws IOException {
        if (!Files.exists(source)) {
            return;
        }
        if (Files.isDirectory(source)) {
            try (Stream<Path> stream = Files.walk(source)) {
                stream.forEach(path -> {
                    try {
                        Path relative = source.relativize(path);
                        String zipPath = entryName + "/" + relative.toString().replace("\\", "/");
                        if (Files.isDirectory(path)) {
                            zipPath = zipPath.endsWith("/") ? zipPath : zipPath + "/";
                            zos.putNextEntry(new ZipEntry(zipPath));
                        } else {
                            zos.putNextEntry(new ZipEntry(zipPath));
                            Files.copy(path, zos);
                        }
                        zos.closeEntry();
                    } catch (IOException e) {
                        throw new RuntimeException("添加文件到ZIP失败: " + path, e);
                    }
                });
            }
        } else {
            zos.putNextEntry(new ZipEntry(entryName));
            Files.copy(source, zos);
            zos.closeEntry();
        }
    }

    private void deleteDirectory(Path dir) {
        try (Stream<Path> stream = Files.walk(dir)) {
            stream.sorted(Comparator.reverseOrder())
                .forEach(p -> {
                    try {
                        Files.delete(p);
                    } catch (IOException ignored) {
                    }
                });
        } catch (IOException ignored) {
        }
    }

    private String calculateMD5(Path file) throws IOException {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] fileBytes = Files.readAllBytes(file);
            byte[] digest = md.digest(fileBytes);
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IOException("计算MD5失败: " + e.getMessage());
        }
    }
}