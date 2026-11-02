/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.mariadb.metadata;

import static org.junit.jupiter.api.Assertions.*;
import java.sql.Connection;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.*;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.*;
import com.sqlapp.jdbc.sql.node.SqlNode;

class MariadbPeriodKeyReaderTest {
	private static class Reader extends MariadbUniqueConstraint11_40Reader {
		int queries;

		Reader() {
			super(DialectResolver.getInstance().getDialect("MariaDB", 11, 4, 0));
		}

		void readTables(List<Table> tables) {
			readKeyPeriods(null, new ParametersContext(), tables.stream().flatMap(t -> t.getConstraints().stream())
					.filter(c -> c instanceof UniqueConstraint).map(c -> (UniqueConstraint) c).toList());
		}

		@Override
		protected JdbcQueryHandler execute(Connection c, SqlNode n, ParametersContext p, ResultSetNextHandler h) {
			queries++;
			assertTrue(n.eval(p).getSql().contains("information_schema.KEY_PERIOD_USAGE"));
			try {
				for (String schema : List.of("app", "audit", "outside")) {
					h.handleResultSetNext(new ExResultSet(null) {
						@Override
						public String getString(String name) {
							return switch (name) {
							case "TABLE_CATALOG" -> "def";
							case "TABLE_SCHEMA" -> schema;
							case "TABLE_NAME" -> "history";
							case "CONSTRAINT_NAME" -> "unique_time";
							case "PERIOD_NAME" -> "valid_time";
							default -> null;
							};
						}
					});
				}
			} catch (java.sql.SQLException e) {
				throw new IllegalStateException(e);
			}
			return null;
		}
	}

	private Table table(String schema) {
		Table table = new Table("history").setCatalogName("def").setSchemaName(schema);
		table.getColumns().add("id", c -> c.setDataType(DataType.INT));
		table.getColumns().add("valid_from", c -> c.setDataType(DataType.DATE).setNullable(false));
		table.getColumns().add("valid_until", c -> c.setDataType(DataType.DATE).setNullable(false));
		table.getTemporalPeriods()
				.add(new TemporalPeriod("valid_time").setPeriodType(TemporalPeriodType.APPLICATION_TIME)
						.setStartColumnName("valid_from").setEndColumnName("valid_until"));
		UniqueConstraint key = new UniqueConstraint("unique_time");
		key.getColumns().add("id");
		key.getColumns().add("valid_from");
		key.getColumns().add("valid_until");
		table.getConstraints().add(key);
		return table;
	}

	@Test
	void readsAllKeysOnceAndRecreatesPeriodKeyAfterXml() throws Exception {
		var reader = new Reader();
		var tables = List.of(table("app"), table("audit"));
		reader.readTables(tables);
		assertEquals(1, reader.queries);
		for (Table table : tables) {
			Table restored = SchemaUtils.readXml(new java.io.StringReader(table.asXml()));
			assertEquals("valid_time",
					restored.getConstraints().get(0).getSpecifics().get("mariadb.withoutOverlapsPeriod"));
			var registry = DialectResolver.getInstance().getDialect("MariaDB", 11, 4, 0).createSqlFactoryRegistry();
			String create = registry.createSql(restored, SqlType.CREATE).get(0).getSqlText();
			String alter = registry.createSql(restored.getConstraints().get(0), SqlType.CREATE).get(0).getSqlText();
			assertTrue(create.contains("valid_time WITHOUT OVERLAPS"), create);
			assertTrue(alter.contains("valid_time WITHOUT OVERLAPS"), alter);
			assertFalse(alter.contains("valid_from"), alter);
			assertFalse(alter.contains("valid_until"), alter);
			restored.setDefinition("CREATE TABLE history (id INT)");
			assertTrue(registry.createSql(restored, SqlType.CREATE).get(0).getSqlText()
					.contains("CREATE TABLE history (id INT)"));
		}
	}

	@Test
	void rejectsUnresolvedOrIncompletePeriodAndOldVersionWithoutMutatingKey() {
		Table table = table("app");
		var key = (UniqueConstraint) table.getConstraints().get(0);
		key.getSpecifics().put("mariadb.withoutOverlapsPeriod", "valid_time");
		var modern = DialectResolver.getInstance().getDialect("MariaDB", 11, 4, 0).createSqlFactoryRegistry();
		var old = DialectResolver.getInstance().getDialect("MariaDB", 10, 5, 0).createSqlFactoryRegistry();
		assertThrows(IllegalArgumentException.class, () -> old.createSql(key, SqlType.CREATE));
		modern.createSql(key, SqlType.CREATE);
		assertEquals(3, key.getColumns().size());
		key.setPrimaryKey(true);
		assertTrue(modern.createSql(key, SqlType.CREATE).get(0).getSqlText().contains("PRIMARY KEY"));
		key.getColumns().remove("valid_until");
		assertThrows(IllegalArgumentException.class, () -> modern.createSql(key, SqlType.CREATE));
		table.getTemporalPeriods().clear();
		assertThrows(IllegalArgumentException.class, () -> modern.createSql(key, SqlType.CREATE));
	}

	@Test
	void emptySelectionSkipsQueryAndUnresolvedKeyFails() {
		var reader = new Reader();
		reader.readTables(List.of());
		assertEquals(0, reader.queries);
		Table table = table("app");
		table.getConstraints().get(0).setName("different_key");
		assertThrows(IllegalStateException.class, () -> reader.readTables(List.of(table)));
	}
}
