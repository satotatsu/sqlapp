/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-mdb.
 */
package com.sqlapp.data.db.dialect.mdb;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Row;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.jdbc.sql.BindParameter;
import com.sqlapp.jdbc.sql.SqlParameterCollection;
import com.sqlapp.util.CommonUtils;

class MdbSqlIntegrationTest {

	@TempDir
	Path tempDirectory;

	@Test
	void generatedDdlDmlAndMetadataRoundTrip() throws Exception {
		final Path database = tempDirectory.resolve("generated.accdb");
		final Dialect dialect = DialectHolder.defaultDialect;
		final Table table = createTable(dialect);
		final List<SqlOperation> creates = dialect.createSqlFactoryRegistry()
				.createSql(table, SqlType.CREATE);
		assertTrue(creates.get(0).getSqlText().contains("ID COUNTER PRIMARY KEY"),
				creates.get(0).getSqlText());
		assertTrue(creates.stream().anyMatch(operation -> operation.getSqlText()
				.startsWith("CREATE INDEX IDX_ORDER_DETAIL_NAME")));

		try (Connection connection = DriverManager.getConnection(
				"jdbc:ucanaccess://" + database.toAbsolutePath()
						+ ";newDatabaseVersion=V2010")) {
			try (Statement statement = connection.createStatement()) {
				for (final SqlOperation operation : creates) {
					statement.execute(operation.getSqlText());
				}
			}

			for (int i = 1; i <= 2; i++) {
				final Row row = table.newRow();
				row.put("DISPLAY_NAME", "row-" + i);
				row.put("DESCRIPTION", "description-" + i);
				row.put("ENABLED", i % 2 == 0);
				row.put("ATTACHMENT", new byte[] { (byte) i });
				row.put("CREATED_AT", Timestamp.valueOf(
						"2026-09-10 12:00:0" + i));
				table.getRows().add(row);
			}
			final SqlParameterCollection parameters = CommonUtils.first(
					dialect.createSqlFactoryRegistry()
							.createSqlNodes(table, SqlType.INSERT_ROWS))
					.eval(table.getRows());
			assertTrue(parameters.getSql().replaceAll("\\s+", " ")
					.contains("VALUES (?"),
					parameters.getSql());
			assertFalse(parameters.getSql().contains("(VALUES(0))"),
					parameters.getSql());
			try (PreparedStatement statement = connection
					.prepareStatement(parameters.getSql())) {
				final List<BindParameter> bindParameters = parameters
						.getBindParameters().get(0).getBindParameters();
				for (int i = 0; i < bindParameters.size(); i++) {
					statement.setObject(i + 1, bindParameters.get(i).getValue());
				}
				assertEquals(2, statement.executeUpdate());
			}

			final String truncateSql = dialect.createSqlFactoryRegistry()
					.createSql(table, SqlType.TRUNCATE).get(0).getSqlText();
			assertTrue(truncateSql.startsWith("DELETE FROM"), truncateSql);
			try (Statement statement = connection.createStatement()) {
				assertEquals(2, statement.executeUpdate(truncateSql));
				try (ResultSet resultSet = statement.executeQuery(
						"SELECT COUNT(*) FROM [ORDER DETAIL]")) {
					assertTrue(resultSet.next());
					assertEquals(0, resultSet.getInt(1));
				}
			}
		}

		final Table loaded = MdbFileLoader.loadTable(database, "ORDER DETAIL");
		assertTrue(loaded.getColumns().get("ID").isIdentity());
		assertNotNull(loaded.getConstraints().getPrimaryKeyConstraint());
		assertNotNull(loaded.getIndexes().get("IDX_ORDER_DETAIL_NAME"));
	}

	private Table createTable(final Dialect dialect) {
		final Table table = new Table("ORDER DETAIL");
		table.setDialect(dialect);
		final Column id = new Column("ID").setDataType(DataType.INT)
				.setIdentity(true);
		table.getColumns().add(id);
		table.getColumns().add(new Column("DISPLAY_NAME")
				.setDataType(DataType.NVARCHAR).setLength(50L)
				.setNotNull(true));
		table.getColumns().add(new Column("DESCRIPTION")
				.setDataType(DataType.LONGNVARCHAR));
		table.getColumns().add(new Column("ENABLED")
				.setDataType(DataType.BOOLEAN));
		table.getColumns().add(new Column("ATTACHMENT")
				.setDataType(DataType.BLOB));
		table.getColumns().add(new Column("CREATED_AT")
				.setDataType(DataType.DATETIME)
				.setDefaultValue(dialect.getCurrentDateTimeFunction()));
		table.setPrimaryKey("PK_ORDER_DETAIL", id);
		table.getIndexes().add("IDX_ORDER_DETAIL_NAME",
				table.getColumns().get("DISPLAY_NAME"));
		return table;
	}
}
