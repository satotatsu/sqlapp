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

class MariadbApplicationPeriodReaderTest {
	private static class Reader extends MariadbTable11_40Reader {
		int queries;
		String periodName = "valid time";
		String end = "valid_until";

		Reader() {
			super(DialectResolver.getInstance().getDialect("MariaDB", 11, 4, 0));
		}

		@Override
		protected JdbcQueryHandler execute(Connection c, SqlNode n, ParametersContext p, ResultSetNextHandler h) {
			queries++;
			assertTrue(n.eval(p).getSql().contains("information_schema.PERIODS"));
			try {
				for (String schema : List.of("app", "audit", "unrequested")) {
					h.handleResultSetNext(new ExResultSet(null) {
						@Override
						public String getString(String name) {
							return switch (name) {
							case "TABLE_CATALOG" -> "def";
							case "TABLE_SCHEMA" -> schema;
							case "TABLE_NAME" -> "history";
							case "PERIOD" -> periodName;
							case "START_COLUMN_NAME" -> "valid_from";
							case "END_COLUMN_NAME" -> end;
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
		table.getColumns().add("valid_from", c -> c.setDataType(DataType.DATE).setNullable(false));
		table.getColumns().add("valid_until", c -> c.setDataType(DataType.DATE).setNullable(false));
		return table;
	}

	@Test
	void periodsForSameNamedTablesAreReadInOneQueryAndRecreated() throws Exception {
		var reader = new Reader();
		var tables = List.of(table("app"), table("audit"));
		reader.readApplicationPeriods(null, new ParametersContext(), tables);
		assertEquals(1, reader.queries);
		for (Table table : tables) {
			assertEquals(1, table.getTemporalPeriods().size());
			assertEquals(TemporalPeriodType.APPLICATION_TIME, table.getTemporalPeriods().get(0).getPeriodType());
			Table restored = SchemaUtils.readXml(new java.io.StringReader(table.asXml()));
			var registry = DialectResolver.getInstance().getDialect("MariaDB", 11, 4, 0).createSqlFactoryRegistry();
			String ddl = registry.createSql(restored, SqlType.CREATE).get(0).getSqlText();
			assertTrue(ddl.contains("PERIOD FOR `valid time`"), ddl);
			assertTrue(ddl.replaceAll("\\s+", "").contains("(valid_from,valid_until)"), ddl);
		}
	}

	@Test
	void rejectsBlankNamesAndRepeatedBoundaryColumns() {
		for (String name : new String[] { null, "", "  " }) {
			var reader = new Reader();
			reader.periodName = name;
			assertThrows(IllegalStateException.class,
					() -> reader.readApplicationPeriods(null, new ParametersContext(), List.of(table("app"))));
		}
		var reader = new Reader();
		reader.end = "valid_from";
		assertThrows(IllegalStateException.class,
				() -> reader.readApplicationPeriods(null, new ParametersContext(), List.of(table("app"))));
	}

	@Test
	void emptySelectionDoesNotQueryAndOlderReaderRemainsUnchanged() {
		var reader = new Reader();
		reader.readApplicationPeriods(null, new ParametersContext(), List.of());
		assertEquals(0, reader.queries);
		assertInstanceOf(MariadbTable10_27Reader.class,
				new MariadbSchema10_27Reader(DialectResolver.getInstance().getDialect("MariaDB", 10, 5, 0))
						.newTableReader());
		assertInstanceOf(MariadbTable11_40Reader.class,
				new MariadbSchema11_40Reader(DialectResolver.getInstance().getDialect("MariaDB", 11, 4, 0))
						.newTableReader());
	}
}
