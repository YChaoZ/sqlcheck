package sqlcheck.aggregate;

import org.junit.jupiter.api.Test;
import sqlcheck.config.SqlCheckProperties;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqlScriptAggregatorTest {

    @Test
    void shouldAggregateByDatabaseAndTypeAndKeepSubmitterOrder() throws IOException {
        // baseDir/sql/ → input; baseDir/aggregation/ → output (auto-derived)
        Path baseDir = Files.createTempDirectory("sql-agg-");
        Path input = Files.createDirectories(baseDir.resolve("sql"));

        write(input.resolve("biz_ddl_alice.sql"), "CREATE TABLE a(id BIGINT);");
        write(input.resolve("biz_ddl_bob.sql"), "ALTER TABLE a ADD COLUMN name VARCHAR(20);");
        write(input.resolve("biz_dml_alice.sql"), "INSERT INTO a VALUES (1);");
        write(input.resolve("other_dml_bob.sql"), "INSERT INTO b VALUES (2);");

        SqlCheckProperties properties = new SqlCheckProperties();
        properties.setSqlDir(input.toString());
        properties.setAggregationSkipPrefixes(Arrays.asList("skip_"));

        Path output = properties.resolveAggregationOutputDir();

        SqlScriptAggregator aggregator = new SqlScriptAggregator(properties);
        List<Path> outputs = aggregator.aggregate();

        assertEquals(3, outputs.size());
        assertTrue(Files.exists(output.resolve("biz_ddl.sql")));
        assertTrue(Files.exists(output.resolve("biz_dml.sql")));
        assertTrue(Files.exists(output.resolve("other_dml.sql")));

        String ddl = read(output.resolve("biz_ddl.sql"));
        assertTrue(ddl.startsWith("-- alice"));
        assertTrue(ddl.contains("CREATE TABLE a(id BIGINT);"));
        assertTrue(ddl.contains("-- bob"));
        assertTrue(ddl.contains("ALTER TABLE a ADD COLUMN name VARCHAR(20);"));
        assertTrue(ddl.indexOf("CREATE TABLE a(id BIGINT);") < ddl.indexOf("ALTER TABLE a ADD COLUMN name VARCHAR(20);"));

        String dml = read(output.resolve("biz_dml.sql"));
        assertTrue(dml.contains("-- alice"));
        assertTrue(dml.contains("INSERT INTO a VALUES (1);"));
    }

    @Test
    void shouldSkipPrefixedScripts() throws IOException {
        Path baseDir = Files.createTempDirectory("sql-agg-skip-");
        Path input = Files.createDirectories(baseDir.resolve("sql"));

        write(input.resolve("skip_biz_ddl_alice.sql"), "CREATE TABLE a(id BIGINT);");
        write(input.resolve("biz_ddl_alice.sql"), "CREATE TABLE b(id BIGINT);");

        SqlCheckProperties properties = new SqlCheckProperties();
        properties.setSqlDir(input.toString());
        properties.setAggregationSkipPrefixes(Arrays.asList("skip_"));

        Path output = properties.resolveAggregationOutputDir();

        SqlScriptAggregator aggregator = new SqlScriptAggregator(properties);
        aggregator.aggregate();

        String ddl = read(output.resolve("biz_ddl.sql"));
        assertFalse(ddl.contains("skip_biz_ddl_alice.sql"));
        assertTrue(ddl.contains("CREATE TABLE b(id BIGINT);"));
    }

    private void write(Path file, String content) throws IOException {
        Files.write(file, content.getBytes(StandardCharsets.UTF_8));
    }

    private String read(Path file) throws IOException {
        return new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
    }
}
