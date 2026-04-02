package sqlcheck.checker;

import sqlcheck.config.SqlCheckProperties;
import sqlcheck.model.CheckResult;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SqlChecker {
    private static final Pattern USE_DATABASE = Pattern.compile("\\bUSE\\s+`?([a-zA-Z_][a-zA-Z0-9_$]*)`?\\s*;?", Pattern.CASE_INSENSITIVE);
    private static final Pattern TABLE_REFERENCE = Pattern.compile(
        "\\b(?:FROM|JOIN|INTO|UPDATE|TRUNCATE\\s+TABLE|ALTER\\s+TABLE(?:\\s+IF\\s+EXISTS)?|DROP\\s+TABLE(?:\\s+IF\\s+EXISTS)?|CREATE\\s+TABLE(?:\\s+IF\\s+NOT\\s+EXISTS)?|REPLACE\\s+INTO|DELETE\\s+FROM)\\s+((?:`?[a-zA-Z_][a-zA-Z0-9_$]*`?\\.)?`?[a-zA-Z_][a-zA-Z0-9_$]*`?)",
        Pattern.CASE_INSENSITIVE);

    private final SqlCheckProperties properties;

    public SqlChecker() {
        this(new SqlCheckProperties());
    }

    public SqlChecker(SqlCheckProperties properties) {
        this.properties = properties;
    }
    private static final Pattern CHINESE_PUNCTUATION = Pattern.compile("[，。；：、‘’“”【】《》（）]");
    private static final Pattern CREATE_TABLE = Pattern.compile("CREATE\\s+TABLE", Pattern.CASE_INSENSITIVE);
    private static final Pattern ALTER_TABLE = Pattern.compile("ALTER\\s+TABLE", Pattern.CASE_INSENSITIVE);
    private static final Pattern INSERT_INTO = Pattern.compile("INSERT\\s+INTO", Pattern.CASE_INSENSITIVE);
    private static final Pattern INSERT_IGNORE = Pattern.compile("INSERT\\s+IGNORE", Pattern.CASE_INSENSITIVE);
    private static final Pattern REPLACE_INTO = Pattern.compile("REPLACE\\s+INTO", Pattern.CASE_INSENSITIVE);
    private static final Pattern INSERT_DUPLICATE = Pattern.compile("INSERT\\s+.*\\s+ON\\s+DUPLICATE\\s+KEY\\s+UPDATE", Pattern.CASE_INSENSITIVE);
    private static final Pattern DELETE_FROM = Pattern.compile("DELETE\\s+FROM", Pattern.CASE_INSENSITIVE);
    private static final Pattern IF_NOT_EXISTS = Pattern.compile("IF\\s+NOT\\s+EXISTS", Pattern.CASE_INSENSITIVE);
    private static final Pattern IF_EXISTS = Pattern.compile("IF\\s+EXISTS", Pattern.CASE_INSENSITIVE);
    private static final Pattern CHARSET = Pattern.compile("CHARSET\\s*=", Pattern.CASE_INSENSITIVE);
    private static final Pattern CHARSET_NOT_MB4 = Pattern.compile("\\bCHARSET\\s*=\\s*(?!utf8mb4)\\w+", Pattern.CASE_INSENSITIVE);
    private static final Pattern ENGINE = Pattern.compile("ENGINE\\s*=", Pattern.CASE_INSENSITIVE);

    private static final Pattern FLOAT_DEFAULT_NULL = Pattern.compile("\\bFLOAT\\b.*\\bDEFAULT\\s+NULL\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern DOUBLE_DEFAULT_NULL = Pattern.compile("\\bDOUBLE\\b.*\\bDEFAULT\\s+NULL\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern DECIMAL_DEFAULT_NULL = Pattern.compile("\\bDECIMAL?\\b.*\\bDEFAULT\\s+NULL\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern VARCHAR_ZERO = Pattern.compile("\\bVARCHAR\\s*\\(\\s*0\\s*\\)", Pattern.CASE_INSENSITIVE);
    private static final Pattern CHAR_ZERO = Pattern.compile("\\bCHAR\\s*\\(\\s*0\\s*\\)", Pattern.CASE_INSENSITIVE);
    private static final Pattern INT_ZERO = Pattern.compile("\\bINT\\s*\\(\\s*0\\s*\\)", Pattern.CASE_INSENSITIVE);
    private static final Pattern TEXT_DEFAULT = Pattern.compile("\\bTEXT\\b.*\\bDEFAULT\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern TINYTEXT_DEFAULT = Pattern.compile("\\bTINYTEXT\\b.*\\bDEFAULT\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern MEDIUMTEXT_DEFAULT = Pattern.compile("\\bMEDIUMTEXT\\b.*\\bDEFAULT\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern LONGTEXT_DEFAULT = Pattern.compile("\\bLONGTEXT\\b.*\\bDEFAULT\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern BLOB_DEFAULT = Pattern.compile("\\bBLOB\\b.*\\bDEFAULT\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern TINYBLOB_DEFAULT = Pattern.compile("\\bTINYBLOB\\b.*\\bDEFAULT\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern MEDIUMBLOB_DEFAULT = Pattern.compile("\\bMEDIUMBLOB\\b.*\\bDEFAULT\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern LONGBLOB_DEFAULT = Pattern.compile("\\bLONGBLOB\\b.*\\bDEFAULT\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern JSON_DEFAULT = Pattern.compile("\\bJSON\\b.*\\bDEFAULT\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern GEOMETRY_DEFAULT = Pattern.compile("\\bGEOMETRY\\b.*\\bDEFAULT\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern UNIQUE_KEY_NO_NAME = Pattern.compile("\\bUNIQUE\\s+KEY\\s*\\(", Pattern.CASE_INSENSITIVE);
    private static final Pattern FULLTEXT_KEY_NO_NAME = Pattern.compile("\\bFULLTEXT\\s+KEY\\s*\\(", Pattern.CASE_INSENSITIVE);
    private static final Pattern SPATIAL_KEY_NO_NAME = Pattern.compile("\\bSPATIAL\\s+KEY\\s*\\(", Pattern.CASE_INSENSITIVE);
    private static final Pattern AUTO_INCREMENT_ZERO = Pattern.compile("\\bAUTO_INCREMENT\\s*=\\s*0\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern AUTO_INCREMENT_INVALID = Pattern.compile("\\bAUTO_INCREMENT\\s*=\\s*([2-9]|[1-9]\\d+)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern INT_AUTO_INCREMENT = Pattern.compile("\\bINT\\b.*\\bAUTO_INCREMENT\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern SMALLINT_AUTO_INCREMENT = Pattern.compile("\\bSMALLINT\\b.*\\bAUTO_INCREMENT\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern TINYINT_AUTO_INCREMENT = Pattern.compile("\\bTINYINT\\b.*\\bAUTO_INCREMENT\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern VARCHAR_LENGTH = Pattern.compile("\\bVARCHAR\\s*\\(\\s*(\\d+)\\s*\\)", Pattern.CASE_INSENSITIVE);
    private static final Pattern CHAR_LENGTH = Pattern.compile("\\bCHAR\\s*\\(\\s*(\\d+)\\s*\\)", Pattern.CASE_INSENSITIVE);
    private static final Pattern VARCHAR_KEY = Pattern.compile("\\bVARCHAR\\s*\\(\\s*(\\d+)\\s*\\)\\s+KEY\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern TEXT_KEY = Pattern.compile("\\bTEXT\\s+KEY\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern TEXT_INDEX = Pattern.compile("\\bTEXT\\s+INDEX\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern BLOB_KEY = Pattern.compile("\\bBLOB\\s+KEY\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern BLOB_INDEX = Pattern.compile("\\bBLOB\\s+INDEX\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern SELECT_STAR = Pattern.compile("\\bSELECT\\s+\\*\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern UPDATE_WHERE_ALL = Pattern.compile("\\bUPDATE\\s+\\w+\\s+SET\\s+\\w+\\s*=\\s*\\w+\\s+WHERE\\s+1=1\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern DELETE_WHERE_ALL = Pattern.compile("\\bDELETE\\s+FROM\\s+\\w+\\s+WHERE\\s+1=1\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern UPDATE_WITHOUT_WHERE = Pattern.compile("\\bUPDATE\\s+\\w+\\s+SET\\s+\\w+\\s*=\\w+\\s*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern DELETE_WITHOUT_WHERE = Pattern.compile("\\bDELETE\\s+FROM\\s+\\w+\\s*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern ENGINE_MYISAM = Pattern.compile("\\bENGINE\\s*=\\s*MYISAM\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern ENGINE_MEMORY = Pattern.compile("\\bENGINE\\s*=\\s*MEMORY\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern COLLATE_UTF8 = Pattern.compile("\\bCOLLATE\\s*=\\s*utf8", Pattern.CASE_INSENSITIVE);
    private static final Pattern COLLATE_UTF8MB4 = Pattern.compile("\\bCOLLATE\\s*=\\s*utf8mb4", Pattern.CASE_INSENSITIVE);
    private static final Pattern ZEROFILL = Pattern.compile("\\bZEROFILL\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern NO_UNSIGNED = Pattern.compile("\\bNO\\s+UNSIGNED\\b", Pattern.CASE_INSENSITIVE);

    private static final String[] MYSQL_RESERVED_WORDS = {
        "SELECT", "INSERT", "UPDATE", "DELETE", "CREATE", "DROP", "ALTER", "TABLE", "INDEX",
        "KEY", "DATABASE", "SCHEMA", "VIEW", "TRIGGER", "PROCEDURE", "FUNCTION", "ORDER",
        "GROUP", "WHERE", "FROM", "JOIN", "LEFT", "RIGHT", "INNER", "OUTER", "BY", "AS",
        "ON", "AND", "OR", "NOT", "IN", "IS", "NULL", "TRUE", "FALSE", "LIKE", "BETWEEN",
        "CASE", "WHEN", "THEN", "ELSE", "END", "EXISTS", "HAVING", "LIMIT", "OFFSET",
        "UNION", "ALL", "DISTINCT", "INTO", "VALUES", "SET", "PRIMARY", "FOREIGN",
        "REFERENCES", "CONSTRAINT", "UNIQUE", "CHECK", "DEFAULT", "AUTO_INCREMENT",
        "UNSIGNED", "ZEROFILL", "SERIAL", "BIGINT", "INT", "INTEGER", "SMALLINT", "TINYINT",
        "MEDIUMINT", "FLOAT", "DOUBLE", "DECIMAL", "NUMERIC", "DATE", "TIME", "DATETIME",
        "TIMESTAMP", "YEAR", "CHAR", "VARCHAR", "TEXT", "TINYTEXT", "MEDIUMTEXT", "LONGTEXT",
        "BLOB", "TINYBLOB", "MEDIUMBLOB", "LONGBLOB", "ENUM", "SET", "JSON", "GEOMETRY",
        "POINT", "LINESTRING", "POLYGON", "MULTIPOINT", "MULTILINESTRING", "MULTIPOLYGON",
        "GEOMETRYCOLLECTION", "INDEX", "FULLTEXT", "SPATIAL", "COMMENT", "COLUMN", "STORAGE",
        "VIRTUAL", "PERSISTENT", "generated", "sequence", "cycle", "cache", "nocache", "using"
    };

    private static final Set<String> RESERVED_WORD_SET = new HashSet<>(Arrays.asList(MYSQL_RESERVED_WORDS));
    private static final Pattern TABLE_NAME_PATTERN = Pattern.compile("\\b(?:CREATE\\s+TABLE(?:\\s+IF\\s+NOT\\s+EXISTS)?|DROP\\s+TABLE(?:\\s+IF\\s+EXISTS)?|ALTER\\s+TABLE)\\s+(`?)([a-zA-Z_][a-zA-Z0-9_$]*|\\d[a-zA-Z0-9_$]*)\\1", Pattern.CASE_INSENSITIVE);
    private static final Pattern INDEX_NAME_PATTERN = Pattern.compile("\\b(?:DROP\\s+INDEX(?:\\s+IF\\s+EXISTS)?|INDEX|KEY|UNIQUE\\s+KEY|FULLTEXT\\s+KEY|SPATIAL\\s+KEY)\\s+(`?)([a-zA-Z_][a-zA-Z0-9_$]*|\\d[a-zA-Z0-9_$]*)\\1\\s*(?=\\(|\\bON\\b|$)", Pattern.CASE_INSENSITIVE);
    private static final Pattern COLUMN_DEFINITION_PATTERN = Pattern.compile("^\\s*(`?)([a-zA-Z_][a-zA-Z0-9_$]*|\\d[a-zA-Z0-9_$]*)\\1\\s+", Pattern.CASE_INSENSITIVE);

    public List<CheckResult> checkFile(File file) {
        List<CheckResult> results = new ArrayList<>();
        List<StatementContext> statements = new ArrayList<>();

        CheckResult result = new CheckResult(file.getName(), file.getAbsolutePath(), "SQL");
        validateSqlFileName(file, result);
        detectFileEncoding(result, file);

        String declaredType = extractDeclaredType(file.getName());

        try {
            String content = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
            statements = splitStatements(content);
        } catch (IOException e) {
            e.printStackTrace();
        }


        boolean hasInsert = false;
        boolean hasDelete = false;

        for (StatementContext context : statements) {
            String upperStmt = context.analysisStatement.toUpperCase();

            checkChinesePunctuationInStatement(result, context);
            checkDatabaseRules(result, context);
            checkDDLRepeatable(result, context);
            checkDDLSyntax(result, context);
            checkDDLBestPractice(result, context, upperStmt);
            if (declaredType != null) {
                checkStatementMatchesDeclaredType(result, context, declaredType);
            }

            if (INSERT_INTO.matcher(context.analysisStatement).find()) {
                hasInsert = true;
            }
            if (DELETE_FROM.matcher(context.analysisStatement).find()) {
                hasDelete = true;
            }
        }

        if (hasInsert && !hasDelete) {
            result.addIssue(new CheckResult.Issue(0, "REPEATABLE",
                "存在 INSERT 语句但无对应 DELETE，建议添加 DELETE 或使用 INSERT IGNORE / REPLACE INTO / ON DUPLICATE KEY UPDATE", "WARNING"));
        }

        results.add(result);
        return results;
    }

    private String extractDeclaredType(String fileName) {
        Matcher m = Pattern.compile("^[a-zA-Z0-9_]+_(ddl|dml)_[a-zA-Z0-9_]+\\.sql$", Pattern.CASE_INSENSITIVE).matcher(fileName);
        return m.matches() ? m.group(1).toLowerCase() : null;
    }

    private void checkStatementMatchesDeclaredType(CheckResult result, StatementContext context, String declaredType) {
        String upper = context.analysisStatement.trim().toUpperCase();
        boolean isDdl = upper.startsWith("CREATE") || upper.startsWith("ALTER")
            || upper.startsWith("DROP") || upper.startsWith("RENAME") || upper.startsWith("TRUNCATE");
        boolean isDml = upper.startsWith("INSERT") || upper.startsWith("REPLACE")
            || upper.startsWith("UPDATE") || upper.startsWith("DELETE");

        if (!isDdl && !isDml) {
            return;
        }

        if ("ddl".equals(declaredType) && isDml) {
            addIssue(result, context, context.startLine, "TYPE_MISMATCH",
                "DDL文件中包含DML语句(" + upper.split("\\s+")[0] + ")，DDL文件只允许 CREATE/ALTER/DROP/RENAME/TRUNCATE", "ERROR");
        } else if ("dml".equals(declaredType) && isDdl) {
            addIssue(result, context, context.startLine, "TYPE_MISMATCH",
                "DML文件中包含DDL语句(" + upper.split("\\s+")[0] + ")，DML文件只允许 INSERT/REPLACE/UPDATE/DELETE", "ERROR");
        }
    }

    private void validateSqlFileName(File file, CheckResult result) {
        String fileName = file.getName();
        String pattern = properties.getSqlFileNamePattern();
        if (pattern == null || pattern.trim().isEmpty()) {
            pattern = "^[a-zA-Z0-9_]+_(ddl|dml)_[a-zA-Z0-9_]+\\.sql$";
        }
        if (!Pattern.compile(pattern, Pattern.CASE_INSENSITIVE).matcher(fileName).matches()) {
            result.addIssue(new CheckResult.Issue(0, "CONFIG_ERROR", "SQL文件名不符合规范: " + fileName, "WARNING"));
        }
    }

    private void detectFileEncoding(CheckResult result, File file) {
        try {
            byte[] bytes = Files.readAllBytes(file.toPath());
            if (bytes.length == 0) {
                return;
            }

            java.nio.charset.CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder();
            decoder.onMalformedInput(java.nio.charset.CodingErrorAction.REPORT);
            decoder.onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT);
            try {
                decoder.decode(java.nio.ByteBuffer.wrap(bytes));
            } catch (java.nio.charset.MalformedInputException e) {
                result.addIssue(new CheckResult.Issue(0, "ENCODING", "文件编码不是UTF-8，存在乱码风险", "ERROR"));
            }
        } catch (IOException e) {
            result.addIssue(new CheckResult.Issue(0, "ENCODING", "无法读取文件编码: " + e.getMessage(), "ERROR"));
        }
    }

    private void checkDatabaseRules(CheckResult result, StatementContext context) {
        if (!properties.isDatabaseCheckEnabled()) {
            return;
        }

        String configuredDatabase = properties.getDatabaseName() == null ? "" : properties.getDatabaseName().trim();
        if (configuredDatabase.isEmpty()) {
            addIssue(result, context, context.startLine, "CONFIG_ERROR", "启用数据库前缀检查时必须配置database-name", "ERROR");
            return;
        }

        if (USE_DATABASE.matcher(context.analysisStatement).find()) {
            int issueLine = findBestLineNumber(context, "\\bUSE\\s+");
            addIssue(result, context, issueLine, "DANGEROUS", "禁止使用 USE 切库语句", "ERROR");
        }

        Matcher referenceMatcher = TABLE_REFERENCE.matcher(context.analysisStatement);
        while (referenceMatcher.find()) {
            String reference = referenceMatcher.group(1).trim();
            String normalizedReference = reference.replace("`", "");
            int dotIndex = normalizedReference.indexOf('.');
            if (dotIndex < 0) {
                int issueLine = findBestLineNumber(context, Pattern.quote(reference));
                addIssue(result, context, issueLine, "SYNTAX_ERROR", "表引用必须写成 数据库名.表名 形式: " + reference, "ERROR");
                continue;
            }

            String databasePart = normalizedReference.substring(0, dotIndex).trim();
            if (!configuredDatabase.equalsIgnoreCase(databasePart)) {
                int issueLine = findBestLineNumber(context, Pattern.quote(reference));
                addIssue(result, context, issueLine, "SYNTAX_ERROR", "表引用必须使用配置的数据库名 " + configuredDatabase + ": " + reference, "ERROR");
            }
        }
    }

    private void checkChinesePunctuationInStatement(CheckResult result, StatementContext context) {
        for (StatementLine line : context.lines) {
            Matcher matcher = CHINESE_PUNCTUATION.matcher(line.analysisText);
            if (matcher.find()) {
                result.addIssue(new CheckResult.Issue(line.number, "CHINESE_PUNCTUATION",
                    "检测到中文符号: " + matcher.group(), "ERROR", line.originalText.trim()));
            }
        }
    }

    private void checkDDLRepeatable(CheckResult result, StatementContext context) {
        String statement = context.analysisStatement;

        if (CREATE_TABLE.matcher(statement).find() && !IF_NOT_EXISTS.matcher(statement).find()) {
            addIssue(result, context, context.startLine, "REPEATABLE",
                "CREATE TABLE 缺少 IF NOT EXISTS，建议添加以支持重复执行", "WARNING");
        }

        if (ALTER_TABLE.matcher(statement).find() && !IF_EXISTS.matcher(statement).find()) {
            addIssue(result, context, context.startLine, "REPEATABLE",
                "ALTER TABLE 建议添加 IF EXISTS", "WARNING");
        }

        if (Pattern.compile("\\bDROP\\s+TABLE\\b", Pattern.CASE_INSENSITIVE).matcher(statement).find()
            && !Pattern.compile("\\bDROP\\s+TABLE\\s+IF\\s+EXISTS\\b", Pattern.CASE_INSENSITIVE).matcher(statement).find()) {
            addIssue(result, context, context.startLine, "REPEATABLE",
                "DROP TABLE建议添加IF EXISTS", "WARNING");
        }

        if (Pattern.compile("\\bDROP\\s+INDEX\\b", Pattern.CASE_INSENSITIVE).matcher(statement).find()
            && !Pattern.compile("\\bDROP\\s+INDEX\\s+IF\\s+EXISTS\\b", Pattern.CASE_INSENSITIVE).matcher(statement).find()) {
            addIssue(result, context, context.startLine, "REPEATABLE",
                "DROP INDEX建议添加IF EXISTS", "WARNING");
        }

        if (Pattern.compile("\\bDROP\\s+DATABASE\\b", Pattern.CASE_INSENSITIVE).matcher(statement).find()
            && !Pattern.compile("\\bDROP\\s+DATABASE\\s+IF\\s+EXISTS\\b", Pattern.CASE_INSENSITIVE).matcher(statement).find()) {
            addIssue(result, context, context.startLine, "REPEATABLE",
                "DROP DATABASE建议添加IF EXISTS", "WARNING");
        }

        if (Pattern.compile("\\bTRUNCATE\\s+TABLE\\b", Pattern.CASE_INSENSITIVE).matcher(statement).find()) {
            addIssue(result, context, context.startLine, "REPEATABLE",
                "TRUNCATE TABLE 危险操作，建议确认是否需要", "WARNING");
        }
    }

    private void checkDDLSyntax(CheckResult result, StatementContext context) {
        String statement = context.analysisStatement;
        String upperLine = statement.toUpperCase();

        reportIfMatches(result, context, FLOAT_DEFAULT_NULL, "\\bFLOAT\\b.*\\bDEFAULT\\s+NULL\\b", "SYNTAX_ERROR",
            "FLOAT类型不建议使用DEFAULT NULL，建议使用DEFAULT 0或DEFAULT 0.0", "ERROR");
        reportIfMatches(result, context, DOUBLE_DEFAULT_NULL, "\\bDOUBLE\\b.*\\bDEFAULT\\s+NULL\\b", "SYNTAX_ERROR",
            "DOUBLE类型不建议使用DEFAULT NULL，建议使用DEFAULT 0或DEFAULT 0.0", "ERROR");
        reportIfMatches(result, context, DECIMAL_DEFAULT_NULL, "\\bDECIMAL?\\b.*\\bDEFAULT\\s+NULL\\b", "SYNTAX_ERROR",
            "DECIMAL类型不建议使用DEFAULT NULL，建议使用DEFAULT 0", "WARNING");
        reportIfMatches(result, context, VARCHAR_ZERO, "VARCHAR\\s*\\(\\s*0\\s*\\)", "SYNTAX_ERROR",
            "VARCHAR长度不能为0", "ERROR");
        reportIfMatches(result, context, CHAR_ZERO, "\\bCHAR\\s*\\(\\s*0\\s*\\)", "SYNTAX_ERROR",
            "CHAR长度不能为0", "ERROR");
        reportIfMatches(result, context, INT_ZERO, "\\bINT\\s*\\(\\s*0\\s*\\)", "SYNTAX_ERROR",
            "INT显示宽度不能为0", "ERROR");
        reportIfMatches(result, context, TEXT_DEFAULT, "\\bTEXT\\b.*\\bDEFAULT\\b", "SYNTAX_ERROR",
            "TEXT类型不能有DEFAULT值", "ERROR");
        reportIfMatches(result, context, TINYTEXT_DEFAULT, "\\bTINYTEXT\\b.*\\bDEFAULT\\b", "SYNTAX_ERROR",
            "TINYTEXT类型不能有DEFAULT值", "ERROR");
        reportIfMatches(result, context, MEDIUMTEXT_DEFAULT, "\\bMEDIUMTEXT\\b.*\\bDEFAULT\\b", "SYNTAX_ERROR",
            "MEDIUMTEXT类型不能有DEFAULT值", "ERROR");
        reportIfMatches(result, context, LONGTEXT_DEFAULT, "\\bLONGTEXT\\b.*\\bDEFAULT\\b", "SYNTAX_ERROR",
            "LONGTEXT类型不能有DEFAULT值", "ERROR");
        reportIfMatches(result, context, BLOB_DEFAULT, "\\bBLOB\\b.*\\bDEFAULT\\b", "SYNTAX_ERROR",
            "BLOB类型不能有DEFAULT值", "ERROR");
        reportIfMatches(result, context, TINYBLOB_DEFAULT, "\\bTINYBLOB\\b.*\\bDEFAULT\\b", "SYNTAX_ERROR",
            "TINYBLOB类型不能有DEFAULT值", "ERROR");
        reportIfMatches(result, context, MEDIUMBLOB_DEFAULT, "\\bMEDIUMBLOB\\b.*\\bDEFAULT\\b", "SYNTAX_ERROR",
            "MEDIUMBLOB类型不能有DEFAULT值", "ERROR");
        reportIfMatches(result, context, LONGBLOB_DEFAULT, "\\bLONGBLOB\\b.*\\bDEFAULT\\b", "SYNTAX_ERROR",
            "LONGBLOB类型不能有DEFAULT值", "ERROR");
        reportIfMatches(result, context, JSON_DEFAULT, "\\bJSON\\b.*\\bDEFAULT\\b", "SYNTAX_ERROR",
            "JSON类型不能有DEFAULT值", "ERROR");
        reportIfMatches(result, context, GEOMETRY_DEFAULT, "\\bGEOMETRY\\b.*\\bDEFAULT\\b", "SYNTAX_ERROR",
            "GEOMETRY类型不能有DEFAULT值", "ERROR");

        if (Pattern.compile("\\bINDEX\\s+\\w*\\s*\\(", Pattern.CASE_INSENSITIVE).matcher(statement).find()
            && Pattern.compile("\\bINDEX\\s+\\(\\s*\\)", Pattern.CASE_INSENSITIVE).matcher(statement).find()) {
            addIssue(result, context, context.startLine, "SYNTAX_ERROR", "INDEX索引名后不能为空", "ERROR");
        }

        if (Pattern.compile("\\bKEY\\s+\\w*\\s*\\(", Pattern.CASE_INSENSITIVE).matcher(statement).find()
            && Pattern.compile("\\bKEY\\s+\\(\\s*\\)", Pattern.CASE_INSENSITIVE).matcher(statement).find()) {
            addIssue(result, context, context.startLine, "SYNTAX_ERROR", "KEY索引名后不能为空", "ERROR");
        }

        reportIfMatches(result, context, UNIQUE_KEY_NO_NAME, "UNIQUE\\s+KEY", "SYNTAX_ERROR",
            "UNIQUE KEY必须指定索引名，格式: UNIQUE KEY index_name(column)", "ERROR");
        reportIfMatches(result, context, FULLTEXT_KEY_NO_NAME, "FULLTEXT\\s+KEY", "SYNTAX_ERROR",
            "FULLTEXT KEY必须指定索引名", "ERROR");
        reportIfMatches(result, context, SPATIAL_KEY_NO_NAME, "SPATIAL\\s+KEY", "SYNTAX_ERROR",
            "SPATIAL KEY必须指定索引名", "ERROR");

        if (Pattern.compile("\\bINDEX\\s*\\(\\s*\\d+\\s*\\)", Pattern.CASE_INSENSITIVE).matcher(statement).find()) {
            addIssue(result, context, context.startLine, "SYNTAX_ERROR", "INDEX不能只有长度没有列名", "ERROR");
        }

        if (Pattern.compile("\\bKEY\\s*\\(\\s*\\d+\\s*\\)", Pattern.CASE_INSENSITIVE).matcher(statement).find()) {
            addIssue(result, context, context.startLine, "SYNTAX_ERROR", "KEY不能只有长度没有列名", "ERROR");
        }

        if (Pattern.compile("\\bCOMMENT\\s+'[^']*$", Pattern.CASE_INSENSITIVE).matcher(statement).find()
            && !Pattern.compile("\\bCOMMENT\\s+'[^']*'\\s*[,\\)]", Pattern.CASE_INSENSITIVE).matcher(statement).find()) {
            addIssue(result, context, context.startLine, "SYNTAX_ERROR", "COMMENT内容未正确闭合(缺少结束单引号)", "ERROR");
        }

        reportIfMatches(result, context, AUTO_INCREMENT_ZERO, "AUTO_INCREMENT\\s*=\\s*0", "SYNTAX_ERROR",
            "AUTO_INCREMENT起始值0，将从0开始递增", "WARNING");

        reportIfMatches(result, context, AUTO_INCREMENT_INVALID, "AUTO_INCREMENT\\s*=\\s*\\d+", "SYNTAX_ERROR",
            "AUTO_INCREMENT起始值只能为0或1", "ERROR");

        if (Pattern.compile("\\bFOREIGN\\s+KEY\\b.*REFERENCES.*\\)\\s*$", Pattern.CASE_INSENSITIVE).matcher(statement).find()
            && !Pattern.compile("\\bFOREIGN\\s+KEY\\b.*REFERENCES.*\\)\\s*(,|$|\\))", Pattern.CASE_INSENSITIVE).matcher(statement).find()) {
            addIssue(result, context, context.startLine, "SYNTAX_ERROR", "FOREIGN KEY缺少ON DELETE/UPDATE或未正确闭合", "ERROR");
        }

        if (Pattern.compile("\\bPRIMARY\\s+KEY\\b.*\\)\\s*$", Pattern.CASE_INSENSITIVE).matcher(statement).find()
            && !Pattern.compile("\\bPRIMARY\\s+KEY\\b.*\\)\\s*(,|$|ENGINE)", Pattern.CASE_INSENSITIVE).matcher(statement).find()) {
            addIssue(result, context, context.startLine, "SYNTAX_ERROR", "PRIMARY KEY定义未正确闭合", "ERROR");
        }

        if (Pattern.compile("\\bDROP\\s+PRIMARY\\s+KEY\\b", Pattern.CASE_INSENSITIVE).matcher(statement).find()) {
            addIssue(result, context, context.startLine, "DANGEROUS", "删除主键是危险操作，请确认是否需要", "WARNING");
        }

        if (Pattern.compile("\\bDROP\\s+UNIQUE\\s+KEY\\b", Pattern.CASE_INSENSITIVE).matcher(statement).find()) {
            addIssue(result, context, context.startLine, "DANGEROUS", "删除唯一索引是危险操作，请确认是否需要", "WARNING");
        }

        if (Pattern.compile("\\bENUM\\s*\\([^)]*\\)[^)]*$", Pattern.CASE_INSENSITIVE).matcher(statement).find()
            && !Pattern.compile("\\bENUM\\s*\\([^)]*\\)\\s*[,\\)]", Pattern.CASE_INSENSITIVE).matcher(statement).find()) {
            addIssue(result, context, context.startLine, "SYNTAX_ERROR", "ENUM定义未正确闭合", "ERROR");
        }

        if (Pattern.compile("\\bSET\\s*\\([^)]*\\)[^)]*$", Pattern.CASE_INSENSITIVE).matcher(statement).find()
            && upperLine.contains("'") && !Pattern.compile("\\bSET\\s*\\([^)]*\\)\\s*[,\\)]", Pattern.CASE_INSENSITIVE).matcher(statement).find()) {
            addIssue(result, context, context.startLine, "SYNTAX_ERROR", "SET定义未正确闭合", "ERROR");
        }

        if (Pattern.compile("\\bCREATE\\s+TABLE\\s+AS\\s+SELECT\\b", Pattern.CASE_INSENSITIVE).matcher(statement).find()) {
            addIssue(result, context, context.startLine, "WARNING",
                "CREATE TABLE ... AS SELECT 会丢失列属性、索引、注释等，建议使用CREATE TABLE + INSERT", "WARNING");
        }

        if (Pattern.compile("\\bALTER\\s+TABLE\\b.*\\bADD\\s+INDEX\\b.*\\(\\s*\\)", Pattern.CASE_INSENSITIVE).matcher(statement).find()) {
            addIssue(result, context, context.startLine, "SYNTAX_ERROR", "ALTER TABLE ADD INDEX 索引列为空", "ERROR");
        }

        if (Pattern.compile("\\bALTER\\s+TABLE\\b.*\\bADD\\s+UNIQUE\\b.*\\(\\s*\\)", Pattern.CASE_INSENSITIVE).matcher(statement).find()) {
            addIssue(result, context, context.startLine, "SYNTAX_ERROR", "ALTER TABLE ADD UNIQUE 索引列为空", "ERROR");
        }

        if (Pattern.compile("\\bALTER\\s+TABLE\\b.*\\bDROP\\s+INDEX\\b", Pattern.CASE_INSENSITIVE).matcher(statement).find()
            && !Pattern.compile("\\bALTER\\s+TABLE\\b.*\\bDROP\\s+INDEX\\s+\\w+", Pattern.CASE_INSENSITIVE).matcher(statement).find()) {
            addIssue(result, context, context.startLine, "SYNTAX_ERROR", "ALTER TABLE DROP INDEX 需要指定索引名", "ERROR");
        }

        if (Pattern.compile("\\bALTER\\s+TABLE\\b.*\\bDROP\\s+KEY\\b", Pattern.CASE_INSENSITIVE).matcher(statement).find()
            && !Pattern.compile("\\bALTER\\s+TABLE\\b.*\\bDROP\\s+KEY\\s+\\w+", Pattern.CASE_INSENSITIVE).matcher(statement).find()) {
            addIssue(result, context, context.startLine, "SYNTAX_ERROR", "ALTER TABLE DROP KEY 需要指定KEY名", "ERROR");
        }

        if (Pattern.compile("\\bTIMESTAMP\\b.*\\bDEFAULT\\s+CURRENT_TIMESTAMP\\b.*\\bON\\s+UPDATE\\s+CURRENT_TIMESTAMP\\b.*[^)]*$",
            Pattern.CASE_INSENSITIVE).matcher(statement).find()) {
            addIssue(result, context, context.startLine, "SYNTAX_ERROR", "TIMESTAMP定义未正确闭合", "ERROR");
        }
    }

    private void checkDDLBestPractice(CheckResult result, StatementContext context, String upperLine) {
        String statement = context.analysisStatement;

        reportIfMatches(result, context, INT_AUTO_INCREMENT, "AUTO_INCREMENT", "BEST_PRACTICE",
            "INT AUTO_INCREMENT建议改为BIGINT，防止数据溢出", "WARNING");
        reportIfMatches(result, context, SMALLINT_AUTO_INCREMENT, "AUTO_INCREMENT", "BEST_PRACTICE",
            "SMALLINT AUTO_INCREMENT范围较小(-32768~32767)，建议评估是否够用", "WARNING");
        reportIfMatches(result, context, TINYINT_AUTO_INCREMENT, "AUTO_INCREMENT", "BEST_PRACTICE",
            "TINYINT AUTO_INCREMENT范围较小(-128~127)，建议评估是否够用", "WARNING");

        Matcher varcharMatcher = VARCHAR_LENGTH.matcher(statement);
        if (varcharMatcher.find()) {
            int len = Integer.parseInt(varcharMatcher.group(1));
            int issueLine = findBestLineNumber(context, "VARCHAR\\s*\\(\\s*" + len + "\\s*\\)");
            if (len > 21845) {
                addIssue(result, context, issueLine, "SYNTAX_ERROR", "VARCHAR长度超过21845可能会导致问题", "ERROR");
            } else if (len > 10000) {
                addIssue(result, context, issueLine, "BEST_PRACTICE", "VARCHAR长度" + len + "较大，建议评估是否可用TEXT替代", "WARNING");
            } else if (len == 255) {
                addIssue(result, context, issueLine, "INFO", "VARCHAR(255)是常见的默认值，但对于中文建议使用更大长度", "INFO");
            }
        }

        Matcher charMatcher = CHAR_LENGTH.matcher(statement);
        if (charMatcher.find()) {
            int len = Integer.parseInt(charMatcher.group(1));
            if (len > 255) {
                int issueLine = findBestLineNumber(context, "CHAR\\s*\\(\\s*" + len + "\\s*\\)");
                addIssue(result, context, issueLine, "SYNTAX_ERROR", "CHAR长度不能超过255", "ERROR");
            }
        }

        if (!CHARSET.matcher(statement).find() && CREATE_TABLE.matcher(statement).find()) {
            addIssue(result, context, context.startLine, "BEST_PRACTICE", "建议指定字符集 CHARSET=utf8mb4", "INFO");
        }

        reportIfMatches(result, context, CHARSET_NOT_MB4, "CHARSET\\s*=", "DEPRECATED",
            "字符集建议使用 utf8mb4", "WARNING");

        if (!ENGINE.matcher(statement).find() && CREATE_TABLE.matcher(statement).find()) {
            addIssue(result, context, context.startLine, "BEST_PRACTICE", "建议指定存储引擎 ENGINE=InnoDB", "INFO");
        }

        reportIfMatches(result, context, ENGINE_MYISAM, "ENGINE\\s*=", "DEPRECATED",
            "MYISAM存储引擎已被废弃，不支持事务和外键，建议使用InnoDB", "WARNING");
        reportIfMatches(result, context, ENGINE_MEMORY, "ENGINE\\s*=", "WARNING",
            "MEMORY存储引擎数据不持久化，重启后会丢失", "WARNING");

        if (Pattern.compile("\\bINDEX\\s*\\(\\s*\\w+\\s*,\\s*\\w+\\s*,\\s*\\w+\\s*,\\s*\\w+\\s*,\\s*\\w+\\s*,\\s*\\w+\\s*\\)",
            Pattern.CASE_INSENSITIVE).matcher(statement).find()) {
            addIssue(result, context, context.startLine, "BEST_PRACTICE", "联合索引列数超过6列，建议拆分或优化", "WARNING");
        }

        if (Pattern.compile("\\bINDEX\\s*\\(\\s*\\w+\\s*,\\s*\\w+\\s*,\\s*\\w+\\s*,\\s*\\w+\\s*,\\s*\\w+\\s*\\)",
            Pattern.CASE_INSENSITIVE).matcher(statement).find()) {
            addIssue(result, context, context.startLine, "BEST_PRACTICE", "联合索引列数为5列，建议控制在6列以内", "INFO");
        }

        reportIfMatches(result, context, VARCHAR_KEY, "VARCHAR\\s*\\(", "STYLE",
            "VARCHAR直接做KEY/INDEX时建议指定长度前缀", "INFO");
        reportIfMatches(result, context, TEXT_KEY, "TEXT\\s+KEY", "BEST_PRACTICE",
            "TEXT类型不能直接做KEY/INDEX，需要使用前缀索引", "ERROR");
        reportIfMatches(result, context, TEXT_INDEX, "TEXT\\s+INDEX", "BEST_PRACTICE",
            "TEXT类型不能直接做INDEX，需要使用前缀索引", "ERROR");
        reportIfMatches(result, context, BLOB_KEY, "BLOB\\s+KEY", "BEST_PRACTICE",
            "BLOB类型不能做KEY/INDEX", "ERROR");
        reportIfMatches(result, context, BLOB_INDEX, "BLOB\\s+INDEX", "BEST_PRACTICE",
            "BLOB类型不能做INDEX", "ERROR");

        if (Pattern.compile("\\bALTER\\s+TABLE\\b.*\\bADD\\s+COLUMN\\b", Pattern.CASE_INSENSITIVE).matcher(statement).find()) {
            if (!Pattern.compile("\\bADD\\s+COLUMN\\b.*\\bAFTER\\b", Pattern.CASE_INSENSITIVE).matcher(statement).find()
                && !Pattern.compile("\\bADD\\s+COLUMN\\b.*\\bFIRST\\b", Pattern.CASE_INSENSITIVE).matcher(statement).find()) {
                addIssue(result, context, context.startLine, "BEST_PRACTICE", "ADD COLUMN建议指定位置(AFTER column_name / FIRST)", "INFO");
            }
        }

        if (Pattern.compile("\\bALTER\\s+TABLE\\b.*\\bMODIFY\\s+COLUMN\\b", Pattern.CASE_INSENSITIVE).matcher(statement).find()) {
            if (!Pattern.compile("\\bMODIFY\\s+COLUMN\\b.*\\bAFTER\\b", Pattern.CASE_INSENSITIVE).matcher(statement).find()
                && !Pattern.compile("\\bMODIFY\\s+COLUMN\\b.*\\bFIRST\\b", Pattern.CASE_INSENSITIVE).matcher(statement).find()) {
                addIssue(result, context, context.startLine, "BEST_PRACTICE", "MODIFY COLUMN建议指定位置(AFTER column_name / FIRST)", "INFO");
            }
        }

        if (Pattern.compile("\\bDATE\\s+DEFAULT\\s+CURRENT_TIMESTAMP\\b", Pattern.CASE_INSENSITIVE).matcher(statement).find()) {
            addIssue(result, context, context.startLine, "SYNTAX_ERROR", "DATE类型不支持DEFAULT CURRENT_TIMESTAMP，请使用DATETIME或TIMESTAMP", "ERROR");
        }

        if (Pattern.compile("\\bTIME\\s+DEFAULT\\s+CURRENT_TIMESTAMP\\b", Pattern.CASE_INSENSITIVE).matcher(statement).find()) {
            addIssue(result, context, context.startLine, "SYNTAX_ERROR", "TIME类型不支持DEFAULT CURRENT_TIMESTAMP，请使用DATETIME或TIMESTAMP", "ERROR");
        }

        if (Pattern.compile("\\bYEAR\\s+DEFAULT\\s+CURRENT_TIMESTAMP\\b", Pattern.CASE_INSENSITIVE).matcher(statement).find()) {
            addIssue(result, context, context.startLine, "SYNTAX_ERROR", "YEAR类型不支持DEFAULT CURRENT_TIMESTAMP", "ERROR");
        }

        if (Pattern.compile("\\bCHAR\\s*\\(\\s*1\\s*\\)\\b", Pattern.CASE_INSENSITIVE).matcher(statement).find()) {
            int issueLine = findBestLineNumber(context, "CHAR\\s*\\(\\s*1\\s*\\)");
            addIssue(result, context, issueLine, "BEST_PRACTICE", "CHAR(1)建议改用TINYINT或ENUM代替", "INFO");
        }

        if (Pattern.compile("\\bVARCHAR\\s*\\(\\s*1\\s*\\)\\b", Pattern.CASE_INSENSITIVE).matcher(statement).find()) {
            int issueLine = findBestLineNumber(context, "VARCHAR\\s*\\(\\s*1\\s*\\)");
            addIssue(result, context, issueLine, "BEST_PRACTICE", "VARCHAR(1)建议改用CHAR(1)或TINYINT/ENUM代替", "INFO");
        }

        reportIfMatches(result, context, SELECT_STAR, "SELECT\\s+\\*", "BEST_PRACTICE",
            "SELECT * 会查询所有字段，建议指定具体字段名", "WARNING");
        reportIfMatches(result, context, UPDATE_WHERE_ALL, "WHERE\\s+1=1", "DANGEROUS",
            "UPDATE WHERE 1=1 是全表更新危险操作，请确认是否需要", "WARNING");
        reportIfMatches(result, context, DELETE_WHERE_ALL, "WHERE\\s+1=1", "DANGEROUS",
            "DELETE WHERE 1=1 是全表删除危险操作，请确认是否需要", "WARNING");
        reportIfMatches(result, context, UPDATE_WITHOUT_WHERE, "UPDATE\\s+\\w+\\s+SET", "DANGEROUS",
            "UPDATE语句缺少WHERE条件，会更新全表！", "ERROR");
        reportIfMatches(result, context, DELETE_WITHOUT_WHERE, "DELETE\\s+FROM", "DANGEROUS",
            "DELETE语句缺少WHERE条件，会删除全表！", "ERROR");

        if (isDdlStatement(context.analysisStatement)) {
            checkReservedIdentifiers(result, context);
        }

        if (Pattern.compile("\\bTABLE\\s+\\`?\\d", Pattern.CASE_INSENSITIVE).matcher(statement).find()) {
            addIssue(result, context, context.startLine, "SYNTAX_ERROR", "表名不能以数字开头", "ERROR");
        }

        if (Pattern.compile("\\bCOLUMN\\s+\\`?\\d", Pattern.CASE_INSENSITIVE).matcher(statement).find()) {
            addIssue(result, context, context.startLine, "SYNTAX_ERROR", "列名不能以数字开头", "ERROR");
        }

        if (Pattern.compile("\\bCREATE\\s+INDEX\\b", Pattern.CASE_INSENSITIVE).matcher(statement).find()) {
            addIssue(result, context, context.startLine, "INFO", "建议使用ALTER TABLE ADD INDEX替代CREATE INDEX，可支持IF NOT EXISTS", "INFO");
        }

        if (Pattern.compile("\\bRENAME\\s+TABLE\\b", Pattern.CASE_INSENSITIVE).matcher(statement).find()) {
            addIssue(result, context, context.startLine, "WARNING", "RENAME TABLE会改变表名，确保应用代码已更新引用", "INFO");
        }

        if (COLLATE_UTF8.matcher(statement).find() && !COLLATE_UTF8MB4.matcher(statement).find()) {
            int issueLine = findBestLineNumber(context, "COLLATE\\s*=");
            addIssue(result, context, issueLine, "DEPRECATED", "utf8字符集已被废弃，请使用utf8mb4", "WARNING");
        }

        reportIfMatches(result, context, ZEROFILL, "ZEROFILL", "DEPRECATED",
            "ZEROFILL已废弃，且会自动添加UNSIGNED", "WARNING");

        if (NO_UNSIGNED.matcher(statement).find()) {
            addIssue(result, context, context.startLine, "WARNING", "NO UNSIGNED语法已废弃，请使用SIGNED", "WARNING");
        }

        if (Pattern.compile("\\bFULLTEXT\\s*\\(", Pattern.CASE_INSENSITIVE).matcher(statement).find()) {
            if (!Pattern.compile("\\bFULLTEXT\\s*\\(.*\\b(VARCHAR|TEXT)\\b", Pattern.CASE_INSENSITIVE).matcher(statement).find()) {
                addIssue(result, context, context.startLine, "SYNTAX_ERROR", "FULLTEXT索引只支持CHAR、VARCHAR、TEXT类型的列", "ERROR");
            }
        }

        if (Pattern.compile("\\bSPATIAL\\s*\\(", Pattern.CASE_INSENSITIVE).matcher(statement).find()) {
            if (!Pattern.compile("\\bSPATIAL\\s*\\(.*\\b(GEOMETRY|POINT|LINESTRING|POLYGON)\\b", Pattern.CASE_INSENSITIVE).matcher(statement).find()) {
                addIssue(result, context, context.startLine, "SYNTAX_ERROR", "SPATIAL索引只支持GEOMETRY类型的列", "ERROR");
            }
        }

        if (Pattern.compile("\\bLIMIT\\s+\\d+\\s*,\\s*\\d+\\b", Pattern.CASE_INSENSITIVE).matcher(statement).find()) {
            addIssue(result, context, context.startLine, "BEST_PRACTICE", "LIMIT offset,count 语法已废弃，建议使用 LIMIT count OFFSET offset", "WARNING");
        }

        if (Pattern.compile("\\b\\(\\s*\\+\\s*\\d+\\s*,\\s*\\d+\\s*\\)\\s*$", Pattern.CASE_INSENSITIVE).matcher(statement).find()) {
            addIssue(result, context, context.startLine, "WARNING", "INSERT VALUES中有多余括号，请检查", "WARNING");
        }
    }

    private boolean isDdlStatement(String analysisStatement) {
        String upper = analysisStatement.trim().toUpperCase();
        return upper.startsWith("CREATE") || upper.startsWith("ALTER")
            || upper.startsWith("DROP") || upper.startsWith("RENAME");
    }

    private void checkReservedIdentifiers(CheckResult result, StatementContext context) {
        String tableName = extractTableName(context.analysisStatement);
        checkIdentifierAgainstReservedWords(result, tableName, context);
        checkIdentifierLength(result, tableName, "表名", context);

        String statementIndexName = extractIndexName(context.analysisStatement);
        checkIdentifierAgainstReservedWords(result, statementIndexName, context);
        checkIdentifierLength(result, statementIndexName, "索引名", context);

        for (StatementLine line : context.lines) {
            String indexName = extractIndexName(line.analysisText);
            checkIdentifierAgainstReservedWords(result, indexName, context);
            checkIdentifierLength(result, indexName, "索引名", context);

            String columnName = extractColumnName(line.analysisText);
            checkIdentifierAgainstReservedWords(result, columnName, context);
            checkIdentifierLength(result, columnName, "列名", context);
        }
    }

    private String extractTableName(String line) {
        Matcher matcher = TABLE_NAME_PATTERN.matcher(line);
        return matcher.find() ? matcher.group(2) : null;
    }

    private String extractIndexName(String line) {
        Matcher matcher = INDEX_NAME_PATTERN.matcher(line);
        return matcher.find() ? matcher.group(2) : null;
    }

    private String extractColumnName(String line) {
        String trimmedLine = line.trim();
        if (trimmedLine.isEmpty() || trimmedLine.startsWith("--") || trimmedLine.startsWith("#")) {
            return null;
        }
        if (trimmedLine.startsWith(")")) {
            return null;
        }

        String upperTrimmed = trimmedLine.toUpperCase();
        if (upperTrimmed.startsWith("PRIMARY KEY")
            || upperTrimmed.startsWith("UNIQUE KEY")
            || upperTrimmed.startsWith("KEY ")
            || upperTrimmed.startsWith("INDEX ")
            || upperTrimmed.startsWith("CONSTRAINT ")
            || upperTrimmed.startsWith("FOREIGN KEY")
            || upperTrimmed.startsWith("FULLTEXT KEY")
            || upperTrimmed.startsWith("SPATIAL KEY")
            || upperTrimmed.startsWith("CREATE ")
            || upperTrimmed.startsWith("ALTER ")
            || upperTrimmed.startsWith("DROP ")
            || upperTrimmed.startsWith("INSERT ")
            || upperTrimmed.startsWith("UPDATE ")
            || upperTrimmed.startsWith("DELETE ")
            || upperTrimmed.startsWith("REPLACE ")
            || upperTrimmed.startsWith("TRUNCATE ")
            || upperTrimmed.startsWith("RENAME ")
            || upperTrimmed.startsWith("SELECT ")) {
            return null;
        }

        Matcher matcher = COLUMN_DEFINITION_PATTERN.matcher(line);
        return matcher.find() ? matcher.group(2) : null;
    }

    private void checkIdentifierAgainstReservedWords(CheckResult result, String identifier, StatementContext context) {
        if (identifier == null) {
            return;
        }
        if (RESERVED_WORD_SET.contains(identifier.toUpperCase())) {
            int issueLine = findBestLineNumber(context, "`?" + Pattern.quote(identifier) + "`?");
            addIssue(result, context, issueLine, "RESERVED_WORD", "列名/表名 '" + identifier + "' 是MySQL保留字，建议修改", "WARNING");
        }
    }

    private void checkIdentifierLength(CheckResult result, String identifier, String identifierType, StatementContext context) {
        if (identifier == null || identifier.length() <= 64) {
            return;
        }
        int issueLine = findBestLineNumber(context, "`?" + Pattern.quote(identifier) + "`?");
        addIssue(result, context, issueLine, "SYNTAX_ERROR", identifierType + "长度不能超过64字符", "ERROR");
    }

    private void reportIfMatches(CheckResult result, StatementContext context, Pattern statementPattern, String linePattern,
                                 String type, String message, String severity) {
        if (statementPattern.matcher(context.analysisStatement).find()) {
            int issueLine = findBestLineNumber(context, linePattern);
            addIssue(result, context, issueLine, type, message, severity);
        }
    }

    private void addIssue(CheckResult result, StatementContext context, int lineNumber, String type, String message, String severity) {
        String sourceLineText = findSourceLineText(context, lineNumber);
        result.addIssue(new CheckResult.Issue(lineNumber, type, message, severity, sourceLineText));
    }

    private int findBestLineNumber(StatementContext context, String regex) {
        Pattern pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
        for (StatementLine line : context.lines) {
            if (pattern.matcher(line.analysisText).find()) {
                return line.number;
            }
        }
        return context.startLine;
    }

    private String findSourceLineText(StatementContext context, int lineNumber) {
        for (StatementLine line : context.lines) {
            if (line.number == lineNumber && !line.originalText.trim().isEmpty()) {
                return line.originalText.trim();
            }
        }
        for (StatementLine line : context.lines) {
            String trimmed = line.originalText.trim();
            if (!trimmed.isEmpty() && !trimmed.startsWith("--") && !trimmed.startsWith("#")) {
                return trimmed;
            }
        }
        return null;
    }

    private List<StatementContext> splitStatements(String content) {
        List<StatementContext> statements = new ArrayList<>();
        StringBuilder original = new StringBuilder();
        StringBuilder analysis = new StringBuilder();
        int statementStartLine = 1;
        int lineNumber = 1;
        boolean inSingleQuote = false;
        boolean inLineComment = false;
        boolean inBlockComment = false;
        statementStartLine = -1;

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
            } else if (inLineComment) {
                original.append(current);
                analysis.append(maskChar(current));
                if (current == '\n') {
                    inLineComment = false;
                    lineNumber++;
                }
            } else if (inBlockComment) {
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
            } else {
                if (current == '\'') {
                    inSingleQuote = true;
                    original.append(current);
                    analysis.append(maskChar(current));
                } else if (current == '-' && next == '-') {
                    inLineComment = true;
                    original.append(current).append(next);
                    analysis.append(maskChar(current)).append(maskChar(next));
                    i++;
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
                    addStatement(statements, original, analysis, statementStartLine);
                    statementStartLine = -1;
                } else {
                    original.append(current);
                    analysis.append(current);
                    if (current == '\n') {
                        lineNumber++;
                    }
                }
            }
        }

        addStatement(statements, original, analysis, statementStartLine);
        return statements;
    }

    private void addStatement(List<StatementContext> statements, StringBuilder original, StringBuilder analysis, int startLine) {
        String originalStatement = trimTrailingWhitespace(original.toString());
        String analysisStatement = trimTrailingWhitespace(analysis.toString());
        if (!originalStatement.trim().isEmpty()) {
            statements.add(new StatementContext(originalStatement, analysisStatement, startLine > 0 ? startLine : 1));
        }
        original.setLength(0);
        analysis.setLength(0);
    }

    private String trimTrailingWhitespace(String text) {
        int end = text.length();
        while (end > 0 && Character.isWhitespace(text.charAt(end - 1))) {
            end--;
        }
        return text.substring(0, end);
    }

    private char maskChar(char ch) {
        return ch == '\n' ? '\n' : ' ';
    }

    private static class StatementContext {
        private final String originalStatement;
        private final String analysisStatement;
        private final int startLine;
        private final List<StatementLine> lines;

        private StatementContext(String originalStatement, String analysisStatement, int startLine) {
            this.originalStatement = originalStatement;
            this.analysisStatement = analysisStatement;
            this.startLine = startLine;
            this.lines = new ArrayList<>();

            String[] originalLines = originalStatement.split("\\n", -1);
            String[] analysisLines = analysisStatement.split("\\n", -1);
            for (int i = 0; i < originalLines.length; i++) {
                String analLine = i < analysisLines.length ? analysisLines[i] : "";
                lines.add(new StatementLine(startLine + i, originalLines[i], analLine));
            }
        }
    }

    private static class StatementLine {
        private final int number;
        private final String originalText;
        private final String analysisText;

        private StatementLine(int number, String originalText, String analysisText) {
            this.number = number;
            this.originalText = originalText;
            this.analysisText = analysisText;
        }
    }
}
