package sqlcheck.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CheckResult {
    public static final String RESULT_TYPE_FILE = "FILE";
    public static final String RESULT_TYPE_SCAN_ERROR = "SCAN_ERROR";

    private final String fileName;
    private final String filePath;
    private final String fileType;
    private final String resultType;
    private final List<Issue> issues = new ArrayList<>();

    public CheckResult(String fileName, String filePath, String fileType) {
        this(fileName, filePath, fileType, RESULT_TYPE_FILE);
    }

    public CheckResult(String fileName, String filePath, String fileType, String resultType) {
        this.fileName = fileName;
        this.filePath = filePath;
        this.fileType = fileType;
        this.resultType = resultType;
    }

    public static CheckResult scanError(String fileName, String filePath, String fileType, String message) {
        CheckResult result = new CheckResult(fileName, filePath, fileType, RESULT_TYPE_SCAN_ERROR);
        result.addIssue(new Issue(0, "SCAN_ERROR", message, "ERROR"));
        return result;
    }

    public void addIssue(Issue issue) {
        this.issues.add(issue);
    }

    public String getFileName() {
        return fileName;
    }

    public String getFilePath() {
        return filePath;
    }

    public String getFileType() {
        return fileType;
    }

    public String getResultType() {
        return resultType;
    }

    public List<Issue> getIssues() {
        return Collections.unmodifiableList(issues);
    }

    public boolean isPassed() {
        return getErrorCount() == 0;
    }

    public boolean hasIssues() {
        return !issues.isEmpty();
    }

    public boolean isScanError() {
        return RESULT_TYPE_SCAN_ERROR.equals(resultType);
    }

    public int getErrorCount() {
        return countSeverity("ERROR");
    }

    public int getWarningCount() {
        return countSeverity("WARNING");
    }

    public int getInfoCount() {
        return countSeverity("INFO");
    }

    public boolean hasWarningsOnly() {
        return getErrorCount() == 0 && getWarningCount() > 0;
    }

    private int countSeverity(String severity) {
        int count = 0;
        for (Issue issue : issues) {
            if (severity.equalsIgnoreCase(issue.getSeverity())) {
                count++;
            }
        }
        return count;
    }

    public static class Issue {
        private final int line;
        private final String type;
        private final String message;
        private final String severity;
        private final String sourceLineText;

        public Issue(int line, String type, String message, String severity) {
            this(line, type, message, severity, null);
        }

        public Issue(int line, String type, String message, String severity, String sourceLineText) {
            this.line = line;
            this.type = type;
            this.message = message;
            this.severity = severity;
            this.sourceLineText = sourceLineText;
        }

        public int getLine() {
            return line;
        }

        public String getType() {
            return type;
        }

        public String getMessage() {
            return message;
        }

        public String getSeverity() {
            return severity;
        }

        public String getSourceLineText() {
            return sourceLineText;
        }
    }
}
