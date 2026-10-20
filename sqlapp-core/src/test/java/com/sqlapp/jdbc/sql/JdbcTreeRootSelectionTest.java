/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.jdbc.sql;

import static org.junit.jupiter.api.Assertions.*;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Table;

class JdbcTreeRootSelectionTest {
	@Test
	void emptyModelRowsStillPermitAnExplicitOrderedRootScan() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:hsqldb:mem:" + UUID.randomUUID(),"sa","");
				var statement = connection.createStatement()) {
			statement.execute("CREATE TABLE ROOT_TABLE(ID INT PRIMARY KEY)");
			statement.execute("INSERT INTO ROOT_TABLE VALUES(3),(1),(2)");
			connection.setAutoCommit(false);
			var table = new Table("ROOT_TABLE").setSchemaName("PUBLIC");
			table.setDialect(com.sqlapp.data.db.dialect.DialectResolver.getInstance().getDialect(connection));
			table.getColumns().add(new Column("ID").setDataType(DataType.INT));
			table.setPrimaryKey((String)null,table.getColumns().get("ID"));
			var selected = new ArrayList<Integer>();
			try (var session = new JdbcTreeDataSession(connection,table)) {
				session.setTableOperationMode(JdbcTreeDataSession.TableOperationMode.NONE);
				session.setRootBatchSize(2);
				session.execute(active -> {
					active.select(table);
					while (active.next(table)) {
						selected.add((Integer)active.getRow(table).get("ID"));
						assertEquals(selected.size() == 1,active.getTableRelation(table).hasBufferedRows());
					}
				});
			}
			assertEquals(List.of(1,2,3),selected);
			assertTrue(table.getRows().isEmpty());
		}
	}

	@Test
	void keylessRootScanRetainsTheConfiguredSelectAllCondition() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:hsqldb:mem:" + UUID.randomUUID(),"sa","");
				var statement = connection.createStatement()) {
			statement.execute("CREATE TABLE ROOT_TABLE(ID INT)");
			statement.execute("INSERT INTO ROOT_TABLE VALUES(1),(2)");
			connection.setAutoCommit(false);
			var table = new Table("ROOT_TABLE").setSchemaName("PUBLIC");
			table.setDialect(com.sqlapp.data.db.dialect.DialectResolver.getInstance().getDialect(connection));
			table.getColumns().add(new Column("ID").setDataType(DataType.INT));
			var selected = new ArrayList<Integer>();
			try (var session = new JdbcTreeDataSession(connection,table)) {
				session.setTableOperationMode(JdbcTreeDataSession.TableOperationMode.NONE);
				session.getTableOptions().setSelectAllCondition((t,builder) -> builder.and().space()._add("ID=2"));
				session.getTableOptions().setSelectOrderByColumnsStrategy(t -> com.sqlapp.data.db.sql.ColumnSelectionStrategy.FULL);
				session.execute(active -> {
					active.select(table);
					while (active.next(table)) selected.add((Integer)active.getRow(table).get("ID"));
				});
			}
			assertEquals(List.of(2),selected);
		}
	}
}
