import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.sqlapp.data.db.command.migration.model.ExecuteMigrationModelsCommand;
import com.zaxxer.hikari.HikariDataSource;

/** Runs real sqlapp SQL models on an isolated embedded DuckDB connection. */
public class DataEngineeringDemo {
    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            throw new IllegalArgumentException("Expected model YAML and output directory");
        }
        Path configuration = Path.of(args[0]);
        Path output = Path.of(args[1]);
        Files.createDirectories(output);
        try (var dataSource = new HikariDataSource()) {
            dataSource.setJdbcUrl("jdbc:duckdb:");
            dataSource.setMaximumPoolSize(1);
            dataSource.setMinimumIdle(1);
            var command = new ExecuteMigrationModelsCommand();
            command.setDataSource(dataSource);
            command.setCloseDataSource(false);
            command.setConfigurationFile(configuration.toFile());
            command.run();
            if (!command.getResult().isSuccess() || command.getResult().models().size() != 2
                    || command.getUnitTestResults().size() != 1
                    || !command.getUnitTestResults().getFirst().match()) {
                throw new IllegalStateException("Expected two built models and one passing model test");
            }
            StringBuilder csv = new StringBuilder("customer,total\n");
            try (var connection = dataSource.getConnection();
                    var statement = connection.createStatement();
                    var rows = statement.executeQuery("SELECT customer, total FROM customer_sales ORDER BY customer")) {
                while (rows.next()) {
                    csv.append(rows.getString(1)).append(',').append(rows.getBigDecimal(2).toPlainString()).append('\n');
                }
            }
            if (!csv.toString().equals("customer,total\nALICE,30\nBOB,7\n")) {
                throw new IllegalStateException("Unexpected SQL model output: " + csv);
            }
            Files.writeString(output.resolve("customer-sales.csv"), csv, StandardCharsets.UTF_8);
            String report = "{\n  \"success\": " + command.getResult().isSuccess()
                    + ",\n  \"models\": " + command.getResult().models().size()
                    + ",\n  \"unitTests\": " + command.getUnitTestResults().size() + "\n}\n";
            Files.writeString(output.resolve("result.json"), report, StandardCharsets.UTF_8);
            Files.copy(configuration, output.resolve("models.yaml"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
