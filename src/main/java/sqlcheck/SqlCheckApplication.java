package sqlcheck;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import sqlcheck.config.SqlCheckProperties;

@SpringBootApplication
@EnableConfigurationProperties(SqlCheckProperties.class)
public class SqlCheckApplication {

    public static void main(String[] args) {
        SpringApplication.run(SqlCheckApplication.class, args);
    }
}