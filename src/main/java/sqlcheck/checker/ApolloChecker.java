package sqlcheck.checker;

import sqlcheck.model.CheckResult;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ApolloChecker {

    private static final Pattern KEY_PATTERN = Pattern.compile("KEY\\s*:\\s*(\\S+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern VALUE_PATTERN = Pattern.compile("VALUE\\s*:\\s*(.+?)(?:\\s+COMMENT|$)", Pattern.CASE_INSENSITIVE);

    private static final Pattern CHINESE_PUNCTUATION = Pattern.compile("[，。；：、‘’“”【】《》（）]");
    private static final Pattern COLON_PATTERN = Pattern.compile("：");

    public List<CheckResult> checkFile(File file) {
        List<CheckResult> results = new ArrayList<>();
        CheckResult result = new CheckResult(file.getName(), file.getAbsolutePath(), "APOLLO");

        String content = readFile(file);
        String fileName = file.getName().toLowerCase();

        if (fileName.endsWith(".sql")) {
            checkApolloScript(result, content);
        } else if (fileName.endsWith(".json")) {
            checkJson(result, content);
        } else if (fileName.endsWith(".yaml") || fileName.endsWith(".yml")) {
            checkYaml(result, content);
        } else {
            checkApolloScript(result, content);
        }

        results.add(result);
        return results;
    }

    private void checkApolloScript(CheckResult result, String content) {
        String[] lines = content.split("\n");
        Set<String> keys = new LinkedHashSet<>();
        Map<String, Integer> keyLines = new HashMap<>();

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            int lineNum = i + 1;
            String trimmedLine = line.trim();

            if (trimmedLine.isEmpty() || trimmedLine.startsWith("--") || trimmedLine.startsWith("#") || trimmedLine.startsWith("//")) {
                continue;
            }

            checkChinesePunctuation(result, line, lineNum);
            checkApolloLine(result, line, lineNum, keys, keyLines);
            checkYmlIndent(result, line, lineNum);
        }
    }

    private void checkApolloLine(CheckResult result, String rawLine, int lineNum, Set<String> keys, Map<String, Integer> keyLines) {
        String line = rawLine.trim();
        String upperLine = line.toUpperCase();

        if (upperLine.contains("INSERT") || upperLine.contains("UPDATE") || upperLine.contains("DELETE") || upperLine.contains("REPLACE")) {

            Matcher keyMatcher = KEY_PATTERN.matcher(line);
            if (keyMatcher.find()) {
                String key = keyMatcher.group(1).trim();

                if (key.isEmpty()) {
                    addIssue(result, lineNum, "SYNTAX_ERROR", "KEY不能为空", "ERROR", rawLine);
                    return;
                }

                if (keys.contains(key)) {
                    addIssue(result, keyLines.get(key), "DUPLICATE_KEY", "发现重复的KEY: " + key + " (首次出现)", "ERROR", rawLine);
                    addIssue(result, lineNum, "DUPLICATE_KEY", "发现重复的KEY: " + key + " (重复出现)", "ERROR", rawLine);
                } else {
                    keys.add(key);
                    keyLines.put(key, lineNum);
                }

                if (!Pattern.matches("^[a-zA-Z][a-zA-Z0-9_.-]*$", key)) {
                    addIssue(result, lineNum, "FORMAT", "KEY格式不规范，应以字母开头，仅包含字母、数字、下划线、点、连字符: " + key, "WARNING", rawLine);
                }
            } else {
                addIssue(result, lineNum, "SYNTAX_ERROR", "缺少KEY定义，格式应为: INSERT KEY:xxx VALUE:yyy", "ERROR", rawLine);
            }

            if (upperLine.contains("INSERT") || upperLine.contains("UPDATE") || upperLine.contains("REPLACE")) {
                if (!line.toUpperCase().contains("VALUE")) {
                    addIssue(result, lineNum, "SYNTAX_ERROR", "缺少VALUE定义，格式应为: INSERT KEY:xxx VALUE:yyy", "ERROR", rawLine);
                }
            }

            if (!line.contains(";")) {
                addIssue(result, lineNum, "MISSING_SEMICOLON", "语句缺少结束分号", "ERROR", rawLine);
            }

            if (!Pattern.compile("VALUE\\s*:\\s*['\"]").matcher(line).find()) {
                Matcher valueMatcher = VALUE_PATTERN.matcher(line);
                if (valueMatcher.find()) {
                    String value = valueMatcher.group(1).trim();
                    if (value.contains(";") || value.contains("--")) {
                        addIssue(result, lineNum, "FORMAT", "VALUE中可能包含非法字符，请检查", "WARNING", rawLine);
                    }
                }
            }
        } else if (!line.isEmpty() && !line.startsWith("#")) {
            addIssue(result, lineNum, "FORMAT", "未识别的语句格式，应以INSERT/UPDATE/DELETE/REPLACE开头", "WARNING", rawLine);
        }
    }

    private void checkChinesePunctuation(CheckResult result, String line, int lineNum) {
        if (COLON_PATTERN.matcher(line).find()) {
            addIssue(result, lineNum, "CHINESE_PUNCTUATION", "检测到中文冒号(：)，应使用英文冒号(:)", "ERROR", line);
        }

        Matcher matcher = CHINESE_PUNCTUATION.matcher(line);
        if (matcher.find()) {
            addIssue(result, lineNum, "CHINESE_PUNCTUATION", "检测到中文符号: " + matcher.group(), "ERROR", line);
        }
    }

    private void checkYmlIndent(CheckResult result, String line, int lineNum) {
        if (line.contains("\t")) {
            addIssue(result, lineNum, "INDENT", "不应使用Tab缩进，请使用空格", "ERROR", line);
        }

        if (line.startsWith("  ") && line.trim().startsWith("-")) {
            addIssue(result, lineNum, "INDENT", "YAML列表项格式检查: 减号后应有一个空格", "WARNING", line);
        }
    }

    private void checkJson(CheckResult result, String content) {
        checkCommonIssues(result, content);
        checkJsonDuplicateKeysText(result, content);
    }

    private void checkJsonDuplicateKeysText(CheckResult result, String content) {
        int lineNum = 1;
        Map<String, Integer> keyFirstLine = new LinkedHashMap<>();
        String[] lines = content.split("\n", -1);

        for (int i = 0; i < content.length(); i++) {
            if (content.charAt(i) == '\n') {
                lineNum++;
                continue;
            }
            if (content.charAt(i) == '"') {
                int end = findStringEnd(content, i + 1);
                if (end > i + 1) {
                    int colonPos = findColonAfter(content, end);
                    if (colonPos > 0) {
                        String key = content.substring(i + 1, end);
                        if (!key.isEmpty()) {
                            if (keyFirstLine.containsKey(key)) {
                                addIssue(result, keyFirstLine.get(key), "DUPLICATE_KEY", "JSON中发现重复key: " + key, "ERROR", getLineText(lines, keyFirstLine.get(key)));
                            } else {
                                keyFirstLine.put(key, lineNum);
                            }
                        }
                        i = colonPos - 1;
                    }
                }
            }
        }
    }

    private int findStringEnd(String content, int start) {
        for (int i = start; i < content.length(); i++) {
            char c = content.charAt(i);
            if (c == '"') {
                return i;
            }
            if (c == '\\' && i + 1 < content.length()) {
                i++;
            }
        }
        return -1;
    }

    private int findColonAfter(String content, int after) {
        for (int i = after; i < content.length(); i++) {
            char c = content.charAt(i);
            if (c == ':') {
                return i;
            }
            if (!Character.isWhitespace(c) && c != ',') {
                break;
            }
        }
        return -1;
    }

    private void checkYaml(CheckResult result, String content) {
        checkCommonIssues(result, content);

        String[] lines = content.split("\n", -1);
        int prevIndent = -1;
        Map<String, KeyOccurrence> canonicalKeys = new LinkedHashMap<>();
        Map<String, KeyOccurrence> scalarPaths = new LinkedHashMap<>();
        Map<String, KeyOccurrence> containerPaths = new LinkedHashMap<>();
        Deque<YamlPathEntry> pathStack = new ArrayDeque<>();
        int blockScalarIndent = -1;

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            int lineNum = i + 1;
            String trimmed = line.trim();

            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }

            int indent = countIndent(line);

            if (line.contains("\t")) {
                addIssue(result, lineNum, "INDENT", "YAML不应使用Tab缩进，请使用空格", "ERROR", line);
            }

            if (prevIndent >= 0) {
                if (indent % 2 != 0) {
                    addIssue(result, lineNum, "INDENT", "YAML缩进应为2的倍数（推荐2个空格），当前为 " + indent + " 个", "WARNING", line);
                }

                if (indent > prevIndent + 4 && prevIndent > 0) {
                    addIssue(result, lineNum, "INDENT", "缩进跳跃过大（前一行 " + prevIndent + " 个空格，当前 " + indent + " 个）", "WARNING", line);
                }
            }

            prevIndent = indent;

            if (blockScalarIndent >= 0) {
                if (indent > blockScalarIndent) {
                    continue;
                }
                blockScalarIndent = -1;
            }

            String uncommented = stripYamlComment(line);
            if (uncommented.trim().isEmpty()) {
                continue;
            }

            if (looksLikeYamlSequence(uncommented)) {
                continue;
            }

            YamlKeyParseResult keyParseResult = parseYamlKey(uncommented);
            if (keyParseResult == null) {
                continue;
            }

            while (!pathStack.isEmpty() && indent <= pathStack.peek().indent) {
                pathStack.pop();
            }

            String parentPath = pathStack.isEmpty() ? "" : pathStack.peek().canonicalPath;
            String canonicalPath = buildCanonicalPath(parentPath, keyParseResult.key);
            if (canonicalPath.isEmpty()) {
                continue;
            }

            validateYamlPathSegments(result, canonicalPath, lineNum, line);

            boolean container = keyParseResult.value.isEmpty();
            boolean hasPathConflict = registerYamlPathConflicts(result, scalarPaths, containerPaths, canonicalPath, lineNum, line, container);
            if (!hasPathConflict) {
                registerDuplicateKey(result, canonicalKeys, canonicalPath, lineNum, line);
            }

            KeyOccurrence occurrence = new KeyOccurrence(lineNum, line);
            if (container) {
                if (!containerPaths.containsKey(canonicalPath)) {
                    containerPaths.put(canonicalPath, occurrence);
                }
                pathStack.push(new YamlPathEntry(indent, canonicalPath));
            } else {
                if (!scalarPaths.containsKey(canonicalPath)) {
                    scalarPaths.put(canonicalPath, occurrence);
                }
                if (isBlockScalarValue(keyParseResult.value)) {
                    blockScalarIndent = indent;
                }
            }
        }

        checkYamlSyntax(result, content);
    }

    private void registerDuplicateKey(CheckResult result, Map<String, KeyOccurrence> canonicalKeys, String canonicalPath,
                                      int lineNum, String rawLine) {
        KeyOccurrence firstOccurrence = canonicalKeys.get(canonicalPath);
        if (firstOccurrence != null) {
            addIssue(result, firstOccurrence.line, "DUPLICATE_KEY", "发现重复的KEY路径: " + canonicalPath + " (首次出现)", "ERROR", firstOccurrence.sourceLineText);
            addIssue(result, lineNum, "DUPLICATE_KEY", "发现重复的KEY路径: " + canonicalPath + " (重复出现)", "ERROR", rawLine);
            return;
        }
        canonicalKeys.put(canonicalPath, new KeyOccurrence(lineNum, rawLine));
    }

    private boolean registerYamlPathConflicts(CheckResult result, Map<String, KeyOccurrence> scalarPaths,
                                               Map<String, KeyOccurrence> containerPaths, String canonicalPath,
                                               int lineNum, String rawLine, boolean container) {
        if (container) {
            KeyOccurrence scalarConflict = scalarPaths.get(canonicalPath);
            if (scalarConflict != null) {
                addPathConflictIssues(result, scalarConflict, canonicalPath, lineNum, rawLine);
                return true;
            }
            for (Map.Entry<String, KeyOccurrence> entry : scalarPaths.entrySet()) {
                if (isParentChildPath(canonicalPath, entry.getKey())) {
                    addPathConflictIssues(result, entry.getValue(), canonicalPath, lineNum, rawLine);
                    return true;
                }
            }
            return false;
        }

        KeyOccurrence containerConflict = containerPaths.get(canonicalPath);
        if (containerConflict != null) {
            addPathConflictIssues(result, containerConflict, canonicalPath, lineNum, rawLine);
            return true;
        }

        String parentPath = canonicalPath;
        while (parentPath.contains(".")) {
            parentPath = parentPath.substring(0, parentPath.lastIndexOf('.'));
            KeyOccurrence scalarConflict = scalarPaths.get(parentPath);
            if (scalarConflict != null) {
                addPathConflictIssues(result, scalarConflict, canonicalPath, lineNum, rawLine);
                return true;
            }
        }

        for (Map.Entry<String, KeyOccurrence> entry : containerPaths.entrySet()) {
            if (isParentChildPath(canonicalPath, entry.getKey())) {
                addPathConflictIssues(result, entry.getValue(), canonicalPath, lineNum, rawLine);
                return true;
            }
        }
        return false;
    }

    private void addPathConflictIssues(CheckResult result, KeyOccurrence firstOccurrence, String canonicalPath,
                                       int lineNum, String rawLine) {
        addIssue(result, firstOccurrence.line, "KEY_PATH_CONFLICT", "KEY路径存在父子冲突: " + canonicalPath + " (首次出现)", "ERROR", firstOccurrence.sourceLineText);
        addIssue(result, lineNum, "KEY_PATH_CONFLICT", "KEY路径存在父子冲突: " + canonicalPath + " (冲突出现)", "ERROR", rawLine);
    }

    private void validateYamlPathSegments(CheckResult result, String canonicalPath, int lineNum, String rawLine) {
        String[] segments = canonicalPath.split("\\.");
        for (String segment : segments) {
            if (!segment.matches("^[a-zA-Z][a-zA-Z0-9_-]*$")) {
                addIssue(result, lineNum, "FORMAT", "KEY路径格式不规范，应按段以字母开头，仅包含字母、数字、下划线、连字符: " + canonicalPath, "WARNING", rawLine);
                return;
            }
        }
    }

    private boolean isBlockScalarValue(String value) {
        String trimmed = value.trim();
        return "|".equals(trimmed) || ">".equals(trimmed) || trimmed.startsWith("|-") || trimmed.startsWith("|+")
            || trimmed.startsWith(">-") || trimmed.startsWith(">+");
    }

    private boolean looksLikeYamlSequence(String line) {
        String trimmed = line.trim();
        return trimmed.startsWith("-") && (trimmed.length() == 1 || Character.isWhitespace(trimmed.charAt(1)));
    }

    private int countIndent(String line) {
        int indent = 0;
        while (indent < line.length() && Character.isWhitespace(line.charAt(indent))) {
            indent++;
        }
        return indent;
    }

    private String stripYamlComment(String line) {
        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;
        for (int i = 0; i < line.length(); i++) {
            char current = line.charAt(i);
            if (current == '\'' && !inDoubleQuote) {
                inSingleQuote = !inSingleQuote;
            } else if (current == '"' && !inSingleQuote && !isEscaped(line, i)) {
                inDoubleQuote = !inDoubleQuote;
            } else if (current == '#' && !inSingleQuote && !inDoubleQuote) {
                if (i == 0 || Character.isWhitespace(line.charAt(i - 1))) {
                    return line.substring(0, i);
                }
            }
        }
        return line;
    }

    private boolean isEscaped(String line, int index) {
        int backslashCount = 0;
        for (int i = index - 1; i >= 0 && line.charAt(i) == '\\'; i--) {
            backslashCount++;
        }
        return backslashCount % 2 != 0;
    }

    private YamlKeyParseResult parseYamlKey(String line) {
        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;
        for (int i = 0; i < line.length(); i++) {
            char current = line.charAt(i);
            if (current == '\'' && !inDoubleQuote) {
                inSingleQuote = !inSingleQuote;
            } else if (current == '"' && !inSingleQuote && !isEscaped(line, i)) {
                inDoubleQuote = !inDoubleQuote;
            } else if (current == ':' && !inSingleQuote && !inDoubleQuote) {
                if (i + 1 < line.length() && !Character.isWhitespace(line.charAt(i + 1)) && line.charAt(i + 1) != '"'
                    && line.charAt(i + 1) != '\'' && line.charAt(i + 1) != '|' && line.charAt(i + 1) != '>') {
                    continue;
                }
                String key = line.substring(0, i).trim();
                if (key.isEmpty()) {
                    return null;
                }
                String value = i + 1 >= line.length() ? "" : line.substring(i + 1).trim();
                return new YamlKeyParseResult(key, value);
            }
        }
        return null;
    }

    private String buildCanonicalPath(String parentPath, String key) {
        String normalizedKey = key.trim();
        if (normalizedKey.isEmpty()) {
            return "";
        }
        return parentPath.isEmpty() ? normalizedKey : parentPath + "." + normalizedKey;
    }

    private boolean isParentChildPath(String parentPath, String childPath) {
        return childPath.startsWith(parentPath + ".");
    }

    private void checkYamlSyntax(CheckResult result, String content) {
        String[] lines = content.split("\n");
        Stack<Character> bracketStack = new Stack<>();

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            int lineNum = i + 1;

            for (int j = 0; j < line.length(); j++) {
                char c = line.charAt(j);
                if (c == '{' || c == '[') {
                    bracketStack.push(c);
                } else if (c == '}' || c == ']') {
                    if (bracketStack.isEmpty()) {
                        addIssue(result, lineNum, "YAML_SYNTAX", "未匹配的闭合括号: " + c, "ERROR", line);
                    } else {
                        char top = bracketStack.pop();
                        if ((c == '}' && top != '{') || (c == ']' && top != '[')) {
                            addIssue(result, lineNum, "YAML_SYNTAX", "括号不匹配", "ERROR", line);
                        }
                    }
                }
            }
        }

        if (!bracketStack.isEmpty()) {
            addIssue(result, 0, "YAML_SYNTAX", "存在未闭合的括号", "ERROR", null);
        }
    }

    private void checkCommonIssues(CheckResult result, String content) {
        String[] lines = content.split("\n");

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            int lineNum = i + 1;

            if (line.contains("'") && line.contains("\"")) {
                addIssue(result, lineNum, "QUOTE", "混用单引号和双引号可能导致问题", "WARNING", line);
            }
        }
    }

    private void addIssue(CheckResult result, int lineNum, String type, String message, String severity, String rawLine) {
        result.addIssue(new CheckResult.Issue(lineNum, type, message, severity, rawLine == null ? null : rawLine.trim()));
    }

    private static class KeyOccurrence {
        private final int line;
        private final String sourceLineText;

        private KeyOccurrence(int line, String sourceLineText) {
            this.line = line;
            this.sourceLineText = sourceLineText == null ? null : sourceLineText.trim();
        }
    }

    private static class YamlPathEntry {
        private final int indent;
        private final String canonicalPath;

        private YamlPathEntry(int indent, String canonicalPath) {
            this.indent = indent;
            this.canonicalPath = canonicalPath;
        }
    }

    private static class YamlKeyParseResult {
        private final String key;
        private final String value;

        private YamlKeyParseResult(String key, String value) {
            this.key = key;
            this.value = value;
        }
    }

    private String getLineText(String[] lines, int lineNum) {
        if (lineNum <= 0 || lineNum > lines.length) {
            return null;
        }
        return lines[lineNum - 1].trim();
    }

    private String readFile(File file) {
        StringBuilder content = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return content.toString();
    }
}
