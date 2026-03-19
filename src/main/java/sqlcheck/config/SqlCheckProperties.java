package sqlcheck.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;
import java.nio.file.Paths;

@ConfigurationProperties(prefix = "sqlcheck")
public class SqlCheckProperties {

    private String sqlDir = "sql";
    private String apolloDir = "apollo";
    private String outputDir = "output";

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

    public String getOutputDir() {
        return outputDir;
    }

    public void setOutputDir(String outputDir) {
        this.outputDir = outputDir;
    }

    public Path resolveSqlDir() {
        return resolvePath(sqlDir);
    }

    public Path resolveApolloDir() {
        return resolvePath(apolloDir);
    }

    public Path resolveOutputDir() {
        return resolvePath(outputDir);
    }

    public Path resolvePath(String configuredPath) {
        Path rawPath = Paths.get(configuredPath == null || configuredPath.trim().isEmpty() ? "." : configuredPath.trim());
        if (rawPath.isAbsolute()) {
            return rawPath.normalize();
        }
        return Paths.get(System.getProperty("user.dir")).resolve(rawPath).normalize().toAbsolutePath();
    }
}
