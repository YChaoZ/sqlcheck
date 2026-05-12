package sqlcheck.sql;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

public final class SqlStatementSplitter {

    private static final Pattern TOP_LEVEL_STATEMENT_START = Pattern.compile(
        "^(?:CREATE\\b|ALTER\\b|DROP\\b|RENAME\\b|TRUNCATE\\b|"
            + "INSERT(?:\\s+IGNORE)?\\s+INTO\\b|REPLACE\\s+INTO\\b|"
            + "UPDATE\\b.+\\bSET\\b|DELETE\\s+FROM\\b|SELECT\\b|USE\\b)",
        Pattern.CASE_INSENSITIVE);

    private SqlStatementSplitter() {
    }

    public static List<SqlStatement> split(String content) {
        List<SqlStatement> statements = new ArrayList<>();
        StringBuilder original = new StringBuilder();
        StringBuilder analysis = new StringBuilder();
        int statementStartLine = -1;
        int lineNumber = 1;
        boolean inSingleQuote = false;
        boolean inLineComment = false;
        boolean inBlockComment = false;

        for (int i = 0; i < content.length(); i++) {
            char current = content.charAt(i);
            char next = i + 1 < content.length() ? content.charAt(i + 1) : '\0';

            if (!inSingleQuote && !inLineComment && !inBlockComment && original.length() == 0) {
                if (Character.isWhitespace(current)) {
                    if (current == '\n') {
                        lineNumber++;
                    }
                    continue;
                }
                statementStartLine = lineNumber;
            }

            if (inSingleQuote) {
                original.append(current);
                analysis.append(maskChar(current));
                if (current == '\n') {
                    lineNumber++;
                }
                if (current == '\'' && next == '\'') {
                    original.append(next);
                    analysis.append(maskChar(next));
                    i++;
                } else if (current == '\'') {
                    inSingleQuote = false;
                }
                continue;
            }

            if (inLineComment) {
                original.append(current);
                analysis.append(maskChar(current));
                if (current == '\n') {
                    inLineComment = false;
                    lineNumber++;
                }
                continue;
            }

            if (inBlockComment) {
                original.append(current);
                analysis.append(maskChar(current));
                if (current == '*' && next == '/') {
                    original.append(next);
                    analysis.append(maskChar(next));
                    i++;
                    inBlockComment = false;
                }
                if (current == '\n') {
                    lineNumber++;
                }
                continue;
            }

            if (current == '\'') {
                inSingleQuote = true;
                original.append(current);
                analysis.append(maskChar(current));
            } else if (current == '-' && next == '-') {
                char followingChar = i + 2 < content.length() ? content.charAt(i + 2) : '\0';
                if (Character.isWhitespace(followingChar) || followingChar == '\0' || followingChar == '\n') {
                    inLineComment = true;
                    original.append(current).append(next);
                    analysis.append(maskChar(current)).append(maskChar(next));
                    i++;
                } else {
                    original.append(current);
                    analysis.append(current);
                }
            } else if (current == '#') {
                inLineComment = true;
                original.append(current);
                analysis.append(maskChar(current));
            } else if (current == '/' && next == '*') {
                inBlockComment = true;
                original.append(current).append(next);
                analysis.append(maskChar(current)).append(maskChar(next));
                i++;
            } else if (current == ';') {
                addStatement(statements, original, analysis, statementStartLine, true);
                statementStartLine = -1;
            } else {
                original.append(current);
                analysis.append(current);
                if (current == '\n') {
                    lineNumber++;
                }
            }
        }

        addStatement(statements, original, analysis, statementStartLine, false);
        return statements;
    }

    private static void addStatement(List<SqlStatement> statements, StringBuilder original, StringBuilder analysis,
                                     int startLine, boolean terminatedBySemicolon) {
        String originalStatement = trimTrailingWhitespace(original.toString());
        String analysisStatement = trimTrailingWhitespace(analysis.toString());
        if (!originalStatement.trim().isEmpty()) {
            statements.add(buildStatement(originalStatement, analysisStatement, startLine > 0 ? startLine : 1, terminatedBySemicolon));
        }
        original.setLength(0);
        analysis.setLength(0);
    }

    private static SqlStatement buildStatement(String originalStatement, String analysisStatement, int startLine,
                                               boolean terminatedBySemicolon) {
        List<StatementLine> lines = new ArrayList<>();
        String[] originalLines = originalStatement.split("\\n", -1);
        String[] analysisLines = analysisStatement.split("\\n", -1);
        int firstMeaningfulLine = 0;
        int lastMeaningfulLine = 0;

        for (int i = 0; i < originalLines.length; i++) {
            String analysisLine = i < analysisLines.length ? analysisLines[i] : "";
            int lineNumber = startLine + i;
            lines.add(new StatementLine(lineNumber, originalLines[i], analysisLine));
            if (!analysisLine.trim().isEmpty()) {
                if (firstMeaningfulLine == 0) {
                    firstMeaningfulLine = lineNumber;
                }
                lastMeaningfulLine = lineNumber;
            }
        }

        List<StatementStart> statementStarts = new ArrayList<>();
        List<SuspiciousBoundary> suspiciousBoundaries = new ArrayList<>();
        int nestingDepth = 0;
        int previousMeaningfulLine = 0;
        String previousMeaningfulAnalysis = null;

        for (StatementLine line : lines) {
            String trimmedAnalysis = line.getAnalysisText().trim();
            if (!trimmedAnalysis.isEmpty()) {
                String keyword = extractLeadingKeyword(trimmedAnalysis);
                if (nestingDepth == 0 && TOP_LEVEL_STATEMENT_START.matcher(trimmedAnalysis).find()
                    && !isContinuationLine(trimmedAnalysis, previousMeaningfulAnalysis, statementStarts)) {
                    statementStarts.add(new StatementStart(keyword, line.getNumber()));
                    if (firstMeaningfulLine != 0 && line.getNumber() != firstMeaningfulLine && previousMeaningfulLine > 0) {
                        suspiciousBoundaries.add(new SuspiciousBoundary(keyword, line.getNumber(), previousMeaningfulLine));
                    }
                }
                previousMeaningfulLine = line.getNumber();
                previousMeaningfulAnalysis = trimmedAnalysis;
            }
            nestingDepth = updateNestingDepth(nestingDepth, line.getAnalysisText());
        }

        int endLine = lines.isEmpty() ? startLine : lines.get(lines.size() - 1).getNumber();
        return new SqlStatement(
            originalStatement,
            analysisStatement,
            startLine,
            endLine,
            terminatedBySemicolon,
            firstMeaningfulLine > 0 ? firstMeaningfulLine : startLine,
            lastMeaningfulLine > 0 ? lastMeaningfulLine : startLine,
            lines,
            statementStarts,
            suspiciousBoundaries
        );
    }

    private static boolean isContinuationLine(String currentLine, String previousMeaningfulAnalysis,
                                              List<StatementStart> statementStarts) {
        if (previousMeaningfulAnalysis == null) {
            return false;
        }
        String upperCurrent = currentLine.toUpperCase();
        String upperPrevious = previousMeaningfulAnalysis.toUpperCase();
        String currentKeyword = extractLeadingKeyword(currentLine);
        String rootKeyword = statementStarts.isEmpty() ? "" : statementStarts.get(0).getKeyword();

        if (upperCurrent.startsWith("UPDATE") && upperPrevious.contains("ON DUPLICATE KEY")) {
            return true;
        }
        if (upperCurrent.startsWith("SELECT") && upperPrevious.endsWith(" AS")) {
            return true;
        }
        if (upperCurrent.startsWith("DELETE") && upperPrevious.endsWith(" ON")) {
            return true;
        }
        if ("SELECT".equals(currentKeyword) && ("INSERT".equals(rootKeyword) || "REPLACE".equals(rootKeyword))) {
            return true;
        }
        return false;
    }

    private static String extractLeadingKeyword(String analysisLine) {
        int end = 0;
        while (end < analysisLine.length() && Character.isLetter(analysisLine.charAt(end))) {
            end++;
        }
        return end > 0 ? analysisLine.substring(0, end).toUpperCase() : "";
    }

    private static int updateNestingDepth(int currentDepth, String analysisLine) {
        int depth = currentDepth;
        for (int i = 0; i < analysisLine.length(); i++) {
            char ch = analysisLine.charAt(i);
            if (ch == '(' || ch == '[' || ch == '{') {
                depth++;
            } else if ((ch == ')' || ch == ']' || ch == '}') && depth > 0) {
                depth--;
            }
        }
        return depth;
    }

    private static String trimTrailingWhitespace(String text) {
        int end = text.length();
        while (end > 0 && Character.isWhitespace(text.charAt(end - 1))) {
            end--;
        }
        return text.substring(0, end);
    }

    private static char maskChar(char ch) {
        return ch == '\n' ? '\n' : ' ';
    }

    public static final class SqlStatement {
        private final String originalStatement;
        private final String analysisStatement;
        private final int startLine;
        private final int endLine;
        private final boolean terminatedBySemicolon;
        private final int firstMeaningfulLine;
        private final int lastMeaningfulLine;
        private final List<StatementLine> lines;
        private final List<StatementStart> statementStarts;
        private final List<SuspiciousBoundary> suspiciousBoundaries;

        private SqlStatement(String originalStatement, String analysisStatement, int startLine, int endLine,
                             boolean terminatedBySemicolon, int firstMeaningfulLine, int lastMeaningfulLine,
                             List<StatementLine> lines, List<StatementStart> statementStarts,
                             List<SuspiciousBoundary> suspiciousBoundaries) {
            this.originalStatement = originalStatement;
            this.analysisStatement = analysisStatement;
            this.startLine = startLine;
            this.endLine = endLine;
            this.terminatedBySemicolon = terminatedBySemicolon;
            this.firstMeaningfulLine = firstMeaningfulLine;
            this.lastMeaningfulLine = lastMeaningfulLine;
            this.lines = Collections.unmodifiableList(new ArrayList<>(lines));
            this.statementStarts = Collections.unmodifiableList(new ArrayList<>(statementStarts));
            this.suspiciousBoundaries = Collections.unmodifiableList(new ArrayList<>(suspiciousBoundaries));
        }

        public String getOriginalStatement() {
            return originalStatement;
        }

        public String getAnalysisStatement() {
            return analysisStatement;
        }

        public int getStartLine() {
            return startLine;
        }

        public int getEndLine() {
            return endLine;
        }

        public boolean isTerminatedBySemicolon() {
            return terminatedBySemicolon;
        }

        public int getFirstMeaningfulLine() {
            return firstMeaningfulLine;
        }

        public int getLastMeaningfulLine() {
            return lastMeaningfulLine;
        }

        public List<StatementLine> getLines() {
            return lines;
        }

        public List<StatementStart> getStatementStarts() {
            return statementStarts;
        }

        public List<SuspiciousBoundary> getSuspiciousBoundaries() {
            return suspiciousBoundaries;
        }

        public boolean isCommentOnly() {
            for (StatementLine line : lines) {
                if (!line.getAnalysisText().trim().isEmpty()) {
                    return false;
                }
            }
            return true;
        }
    }

    public static final class StatementLine {
        private final int number;
        private final String originalText;
        private final String analysisText;

        private StatementLine(int number, String originalText, String analysisText) {
            this.number = number;
            this.originalText = originalText;
            this.analysisText = analysisText;
        }

        public int getNumber() {
            return number;
        }

        public String getOriginalText() {
            return originalText;
        }

        public String getAnalysisText() {
            return analysisText;
        }
    }

    public static final class StatementStart {
        private final String keyword;
        private final int line;

        private StatementStart(String keyword, int line) {
            this.keyword = keyword;
            this.line = line;
        }

        public String getKeyword() {
            return keyword;
        }

        public int getLine() {
            return line;
        }
    }

    public static final class SuspiciousBoundary {
        private final String keyword;
        private final int line;
        private final int previousStatementEndLine;

        private SuspiciousBoundary(String keyword, int line, int previousStatementEndLine) {
            this.keyword = keyword;
            this.line = line;
            this.previousStatementEndLine = previousStatementEndLine;
        }

        public String getKeyword() {
            return keyword;
        }

        public int getLine() {
            return line;
        }

        public int getPreviousStatementEndLine() {
            return previousStatementEndLine;
        }
    }
}
