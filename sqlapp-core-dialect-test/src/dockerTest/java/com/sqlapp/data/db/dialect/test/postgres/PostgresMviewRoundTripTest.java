/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.test.postgres;

import static org.junit.jupiter.api.Assertions.*;
import java.io.StringReader;
import java.sql.Connection;
import java.util.UUID;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.PortBinding;
import com.github.dockerjava.api.model.Ports.Binding;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.*;

class PostgresMviewRoundTripTest {
	@ParameterizedTest
	@ValueSource(strings = { "postgres:9.3.25", "postgres:18.4" })
	void readsXmlRecreatesAndChangesMaterializedView(String image) throws Exception {
		try (var db = new PostgreSQLContainer(image).withDatabaseName("sqlapp_mview").withUsername("sqlapp_test")
				.withPassword(UUID.randomUUID().toString()).withCreateContainerCmdModifier(cmd -> cmd.getHostConfig()
						.withPortBindings(new PortBinding(Binding.bindIp("127.0.0.1"), new ExposedPort(5432))))) {
			db.start();
			try (var connection = db.createConnection(""); var sql = connection.createStatement()) {
				String schema = "mview_" + UUID.randomUUID().toString().replace("-", "");
				String role = schema + "_owner";
				sql.execute("CREATE SCHEMA " + schema);
				sql.execute("CREATE ROLE " + role);
				try {
					sql.execute("GRANT USAGE, CREATE ON SCHEMA " + schema + " TO " + role);
					sql.execute("CREATE TABLE " + schema + ".items (id integer)");
					sql.execute("GRANT SELECT ON " + schema + ".items TO " + role);
					sql.execute("INSERT INTO " + schema + ".items VALUES (7)");
					sql.execute("CREATE MATERIALIZED VIEW " + schema
							+ ".\"Daily Totals\" (\"Result ID\") TABLESPACE pg_default AS SELECT id FROM " + schema
							+ ".items");
					sql.execute("CREATE INDEX \"By Result\" ON " + schema + ".\"Daily Totals\" (\"Result ID\")");
					sql.execute("CREATE INDEX retained_result ON " + schema + ".\"Daily Totals\" (\"Result ID\")");
					sql.execute("COMMENT ON MATERIALIZED VIEW " + schema + ".\"Daily Totals\" IS 'view note'");
					sql.execute("COMMENT ON COLUMN " + schema + ".\"Daily Totals\".\"Result ID\" IS '''column'''");
					sql.execute("COMMENT ON INDEX " + schema + ".\"By Result\" IS '''index'''");
					var original = read(connection, schema);
					Schema restored = SchemaUtils.readXml(new StringReader(original.asXml()));
					var dialect = DialectResolver.getInstance().getDialect(connection);
					restored.setDialect(dialect);
					var view = restored.getMviews().get("Daily Totals");
					assertEquals("view note", view.getRemarks());
					assertEquals("'column'", view.getColumns().get("Result ID").getRemarks());
					assertEquals("'index'", view.getIndexes().get("By Result").getRemarks());
					var registry = dialect.createSqlFactoryRegistry();
					registry.getOptions().setDecorateSchemaName(true);
					for (var operation : registry.createSql(view, SqlType.DROP))
						sql.execute(operation.getSqlText());
					view.setTableSpaceName("pg_default");
					for (var operation : registry.createSql(view, SqlType.CREATE))
						sql.execute(operation.getSqlText());
					var recreated = read(connection, schema);
					assertEquals("'column'",
							recreated.getMviews().get("Daily Totals").getColumns().get("Result ID").getRemarks());
					assertEquals("'index'",
							recreated.getMviews().get("Daily Totals").getIndexes().get("By Result").getRemarks());
					var target = recreated.clone();
					target.setDialect(dialect);
					var changed = target.getMviews().get("Daily Totals");
					changed.setTableSpaceName("pg_default").setRemarks(null);
					changed.setOwnerName(role);
					changed.getColumns().get("Result ID").setRemarks("changed column");
					changed.getIndexes().get("retained_result").setRemarks("'changed index'");
					changed.getIndexes().remove(changed.getIndexes().get("By Result"));
					var added = new Index("new_result");
					added.getColumns().add("Result ID");
					changed.getIndexes().add(added);
					for (var operation : registry.createSql(recreated.diff(target)))
						sql.execute(operation.getSqlText());
					var actual = read(connection, schema).getMviews().get("Daily Totals");
					assertEquals(role, actual.getOwnerName());
					assertNull(actual.getRemarks());
					assertEquals("changed column", actual.getColumns().get("Result ID").getRemarks());
					assertNotNull(actual.getIndexes().get("new_result"));
					assertEquals("'changed index'", actual.getIndexes().get("retained_result").getRemarks());
					sql.execute("INSERT INTO " + schema + ".items VALUES (8)");
					for (var operation : registry.createSql(actual, SqlType.REFRESH))
						sql.execute(operation.getSqlText());
					try (var rows = sql
							.executeQuery("SELECT max(\"Result ID\") FROM " + schema + ".\"Daily Totals\"")) {
						assertTrue(rows.next());
						assertEquals(8, rows.getInt(1));
					}
				} finally {
					sql.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
					sql.execute("DROP ROLE " + role);
				}
			}
		}
	}

	private Schema read(Connection connection, String schema) {
		var reader = DialectResolver.getInstance().getDialect(connection).getCatalogReader().getSchemaReader();
		reader.setSchemaName(schema);
		return reader.getAllFull(connection).stream().filter(value -> schema.equals(value.getName())).findFirst()
				.orElseThrow();
	}
}
