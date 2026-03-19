package sqlcheck.runner;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import sqlcheck.service.SqlCheckService;

@Component
public class SqlCheckRunner implements CommandLineRunner {

    private final SqlCheckService sqlCheckService;

    public SqlCheckRunner(SqlCheckService sqlCheckService) {
        this.sqlCheckService = sqlCheckService;
    }

    @Override
    public void run(String... args) throws Exception {
        sqlCheckService.run();
    }
}
