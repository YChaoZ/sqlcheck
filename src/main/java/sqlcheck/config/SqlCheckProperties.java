package sqlcheck.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "sqlcheck")
public class SqlCheckProperties {

    private String sqlDir = "sql";
    private String apolloDir = "apollo";
    private String sqlFileNamePattern = "^(?<database>[a-zA-Z0-9_]+)_(?<type>ddl|dml)_(?<submitter>[a-zA-Z0-9_]+)\\.sql$";
    private String aggregationFileNamePattern = "^(?<database>[a-zA-Z0-9_]+)_(?<type>ddl|dml)_(?<submitter>[a-zA-Z0-9_]+)\\.sql$";
    private List<String> aggregationSkipPrefixes = new ArrayList<>();
    private boolean databaseCheckEnabled = false;
    private String databaseName = "";
    private PackageConfig packageConfig = new PackageConfig();

    public String getSqlDir() {
        return sqlDir;
    }

    public void setSqlDir(String sqlDir) {
        this.sqlDir = sqlDir;
    }

    public String getApolloDir() {
        return apolloDir;
    }

    public void setApolloDir(String apolloDir) {
        this.apolloDir = apolloDir;
    }

    public String getSqlFileNamePattern() {
        return sqlFileNamePattern;
    }

    public void setSqlFileNamePattern(String sqlFileNamePattern) {
        this.sqlFileNamePattern = sqlFileNamePattern;
    }

    public String getAggregationFileNamePattern() {
        return aggregationFileNamePattern;
    }

    public void setAggregationFileNamePattern(String aggregationFileNamePattern) {
        this.aggregationFileNamePattern = aggregationFileNamePattern;
    }

    public List<String> getAggregationSkipPrefixes() {
        return aggregationSkipPrefixes;
    }

    public void setAggregationSkipPrefixes(List<String> aggregationSkipPrefixes) {
        this.aggregationSkipPrefixes = aggregationSkipPrefixes == null ? new ArrayList<>() : aggregationSkipPrefixes;
    }

    public boolean isDatabaseCheckEnabled() {
        return databaseCheckEnabled;
    }

    public void setDatabaseCheckEnabled(boolean databaseCheckEnabled) {
        this.databaseCheckEnabled = databaseCheckEnabled;
    }

    public String getDatabaseName() {
        return databaseName;
    }

    public void setDatabaseName(String databaseName) {
        this.databaseName = databaseName;
    }

    public PackageConfig getPackageConfig() {
        return packageConfig;
    }

    public void setPackageConfig(PackageConfig packageConfig) {
        this.packageConfig = packageConfig;
    }

    public Path resolveSqlDir() {
        return resolvePath(sqlDir);
    }

    public Path resolveApolloDir() {
        return resolvePath(apolloDir);
    }

    /** 报告目录：sql-dir 的父目录下的 report/ */
    public Path resolveOutputDir() {
        return resolveSqlDir().getParent().resolve("report");
    }

    /** 整合输入目录：与 sql-dir 相同 */
    public Path resolveAggregationInputDir() {
        return resolveSqlDir();
    }

    /** 整合输出目录：sql-dir 的父目录下的 aggregation/ */
    public Path resolveAggregationOutputDir() {
        return resolveSqlDir().getParent().resolve("aggregation");
    }

    public Path resolvePackageSourceDir() {
        if (packageConfig.getSourceDir() == null || packageConfig.getSourceDir().trim().isEmpty()) {
            return null;
        }
        return resolvePath(packageConfig.getSourceDir());
    }

    public Path resolvePath(String configuredPath) {
        Path rawPath = Paths.get(configuredPath == null || configuredPath.trim().isEmpty() ? "." : configuredPath.trim());
        if (rawPath.isAbsolute()) {
            return rawPath.normalize();
        }
        return Paths.get(System.getProperty("user.dir")).resolve(rawPath).normalize().toAbsolutePath();
    }

    public static class PackageConfig {
        private String zipName = "NBMS";
        private String tarName = "diomdb_20260144";
        private String sourceDir = "./scripts";
        private boolean enabled = true;

        public String getZipName() {
            return zipName;
        }

        public void setZipName(String zipName) {
            this.zipName = zipName;
        }

        public String getTarName() {
            return tarName;
        }

        public void setTarName(String tarName) {
            this.tarName = tarName;
        }

        public String getSourceDir() {
            return sourceDir;
        }

        public void setSourceDir(String sourceDir) {
            this.sourceDir = sourceDir;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }
}