import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import org.hsqldb.jdbc.JDBCDataSource;

import com.sqlapp.data.db.command.migration.bulk.BulkMigration;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Catalog;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.SchemaUtils;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.jdbc.bulk.BulkInsertResolver;
import com.sqlapp.jdbc.bulk.BulkOption;
import com.sqlapp.jdbc.bulk.BulkUpsertOption;
import com.sqlapp.jdbc.bulk.BulkUpsertResolver;

/** Runs only against two newly created, process-local HSQL memory databases. */
public class MigrateDemo {
    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            throw new IllegalArgumentException("Expected Schema XML and output directory");
        }
        Catalog catalog = SchemaUtils.readXml(new File(args[0]));
        // The XML's catalog label names the documentation, not either memory database.
        catalog.setName(null);
        Schema schema = catalog.getSchemas().get("PUBLIC");
        Path output = Path.of(args[1]);
        Files.createDirectories(output);
        String run = UUID.randomUUID().toString().replace("-", "");
        JDBCDataSource source = dataSource("demo_source_" + run);
        JDBCDataSource target = dataSource("demo_target_" + run);
        createTables(source, schema);
        createTables(target, schema);

        Table customers = schema.getTables().get("CUSTOMER");
        Table orders = schema.getTables().get("CUSTOMER_ORDER");
        addCustomer(customers, 1L, "Alice");
        addCustomer(customers, 2L, "Bob");
        addOrder(orders, 101L, 1L);
        addOrder(orders, 102L, 1L);
        addOrder(orders, 103L, 2L);
        try (var connection = source.getConnection()) {
            BulkInsertResolver.execute(connection, customers, BulkOption.defaults());
            BulkInsertResolver.execute(connection, orders, BulkOption.defaults());
        }
        customers.getRows().clear();
        orders.getRows().clear();

        // The ordinary high-level path: migrate, verify, and retain detailed artifacts.
        var migration = BulkMigration.builder().source(source).target(target).schema(schema)
                .executionReport(output.resolve("execution.json"))
                .verificationReport(output.resolve("verification-match.json")).build();
        var execution = migration.run();
        require(execution.isMatch(), "Initial migration must match");
        require(execution.verification().getExpectedRows() == 5, "Expected five source rows");
        require(execution.verification().getActualRows() == 5, "Expected five target rows");
        require(execution.migration().getTasks().size() == 2, "Expected two migrated tables");

        // Change one target value through the shared UPSERT API, keeping row counts equal.
        addCustomer(customers, 1L, "Changed in target");
        try (var connection = target.getConnection()) {
            BulkUpsertResolver.execute(connection, customers, BulkUpsertOption.defaults());
        }
        customers.getRows().clear();
        var verifier = BulkMigration.builder().source(source).target(target).schema(schema)
                .verificationReport(output.resolve("verification-mismatch.json")).build();
        var mismatch = verifier.verify();
        require(!mismatch.isMatch(), "Changed target value must be detected");
        require(mismatch.getMismatchedTasks() == 1, "Only CUSTOMER should mismatch");
        require(mismatch.getExpectedRows() == 5 && mismatch.getActualRows() == 5,
                "Mismatch should be detected despite equal row counts");
        require(mismatch.getTasks().stream().filter(task -> !task.getVerificationResult().isMatch())
                .allMatch(task -> task.getTaskId().endsWith("CUSTOMER")), "Unexpected mismatched table");

        String summary = "Initial migration: MATCH; 2 tables; 5 source rows; 5 target rows\n"
                + "After changing CUSTOMER name: MISMATCH; 1 table; 5 source rows; 5 target rows\n"
                + "Detection uses ordered row hashes as well as counts. No repair was executed.\n";
        Files.writeString(output.resolve("summary.txt"), summary, StandardCharsets.UTF_8);
        System.out.print(summary);
        System.out.println("Inspect reports in " + output.toAbsolutePath());
    }

    private static JDBCDataSource dataSource(String name) {
        var dataSource = new JDBCDataSource();
        dataSource.setUrl("jdbc:hsqldb:mem:" + name);
        dataSource.setUser("SA");
        return dataSource;
    }

    private static void createTables(JDBCDataSource dataSource, Schema schema) throws Exception {
        try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
            var registry = DialectResolver.getInstance().getDialect(connection).createSqlFactoryRegistry();
            // The fixture lists CUSTOMER first, followed by its dependent CUSTOMER_ORDER.
            for (Table table : schema.getTables()) {
                var operations = registry.createSql(table, SqlType.CREATE);
                require(!operations.isEmpty(), "CREATE SQL required for " + table.getName());
                for (var operation : operations) {
                    statement.execute(operation.getSqlText());
                }
            }
        }
    }

    private static void addCustomer(Table table, long id, String name) {
        var row = table.newRow();
        row.put("CUSTOMER_ID", id);
        row.put("CUSTOMER_NAME", name);
        table.getRows().add(row);
    }

    private static void addOrder(Table table, long id, long customerId) {
        var row = table.newRow();
        row.put("ORDER_ID", id);
        row.put("CUSTOMER_ID", customerId);
        row.put("ORDER_DATE", java.sql.Date.valueOf("2026-01-15"));
        table.getRows().add(row);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
