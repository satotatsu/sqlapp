/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.sqlite.sql;

import static org.junit.jupiter.api.Assertions.*;
import java.sql.DriverManager;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.dialect.sqlite.DialectHolder;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.TableRelationTreeHolder;
import com.sqlapp.jdbc.sql.SqlParser;

class SqliteSelectRowsTest {
	@Test
	void copyReopensTheSqliteRootCursorAfterCommittedBatches() throws Exception {
		for (int batchSize : List.of(1, 2))
			verifyCopyAcrossCommits(batchSize);
	}

	private void verifyCopyAcrossCommits(int batchSize) throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:");
				var statement = connection.createStatement()) {
			statement.execute("CREATE TABLE source_table(id INT PRIMARY KEY,value TEXT)");
			statement.execute("CREATE TABLE target_table(id INT PRIMARY KEY,value TEXT)");
			statement.execute("INSERT INTO source_table VALUES(3,'three'),(1,'one'),(2,'two'),(4,'four'),(5,'five')");
			var reader = DialectHolder.defaultDialect.getCatalogReader().getSchemaReader().getTableReader();
			reader.setSchemaName("main");
			var tables = reader.getAllFull(connection);
			var sourceTable = tables.stream().filter(t -> t.getName().equals("source_table")).findFirst().orElseThrow();
			var targetTable = tables.stream().filter(t -> t.getName().equals("target_table")).findFirst().orElseThrow();
			connection.setAutoCommit(false);
			try (var source = new com.sqlapp.jdbc.sql.JdbcTreeDataSession(connection, sourceTable);
					var target = new com.sqlapp.jdbc.sql.JdbcTreeDataSession(connection, targetTable);
					var copy = new com.sqlapp.jdbc.sql.JdbcTreeDataCopySession(source, target)) {
				source.setTableOperationMode(com.sqlapp.jdbc.sql.JdbcTreeDataSession.TableOperationMode.NONE);
				copy.setRootBatchSize(batchSize);
				copy.setCommitEveryRootBatches(1);
				copy.execute(active -> {
					source.select(sourceTable);
					int count = 0;
					while (active.next(sourceTable)) {
						assertTrue(++count <= 5, "Processed roots must not be selected again");
						active.newCopy(active.getRow(sourceTable), targetTable);
					}
					assertEquals(5, count);
				});
			}
			try (var result = statement.executeQuery("SELECT count(*),sum(id) FROM target_table")) {
				assertTrue(result.next());
				assertEquals(5, result.getInt(1));
				assertEquals(15, result.getInt(2));
			}
			try (var result = statement.executeQuery("SELECT count(*) FROM source_table")) {
				assertTrue(result.next());
				assertEquals(0, result.getInt(1));
			}
		}
	}

	@Test
	void treeSessionScansTheRootWithoutAnInputKeyList() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:");
				var statement = connection.createStatement()) {
			statement.execute("CREATE TABLE t(id INT PRIMARY KEY)");
			statement.execute("INSERT INTO t VALUES(3),(1),(2)");
			var reader = DialectHolder.defaultDialect.getCatalogReader().getSchemaReader().getTableReader();
			reader.setSchemaName("main");
			reader.setObjectName("t");
			var table = reader.getAllFull(connection).get(0);
			assertTrue(table.getRows().isEmpty());
			connection.setAutoCommit(false);
			var selected = new java.util.ArrayList<Integer>();
			try (var session = new com.sqlapp.jdbc.sql.JdbcTreeDataSession(connection, table)) {
				session.setTableOperationMode(com.sqlapp.jdbc.sql.JdbcTreeDataSession.TableOperationMode.NONE);
				session.execute(active -> {
					active.select(table);
					while (active.next(table))
						selected.add(((Number) active.getRow(table).get("id")).intValue());
				});
			}
			assertEquals(List.of(1, 2, 3), selected);
		}
	}

	@Test
	void rootRowsSelectAndDeleteOnlyRelatedChildren() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:");
				var statement = connection.createStatement()) {
			statement.execute("CREATE TABLE parent(id INT PRIMARY KEY)");
			statement.execute("CREATE TABLE child(id INT PRIMARY KEY,parent_id INT REFERENCES parent(id))");
			statement.execute("INSERT INTO parent VALUES(1),(2)");
			statement.execute("INSERT INTO child VALUES(10,1),(11,1),(20,2)");
			var parent = new Table("parent").setSchemaName("main");
			parent.getColumns().add(new Column("id").setDataType(DataType.INT));
			parent.setPrimaryKey((String) null, parent.getColumns().get("id"));
			var child = new Table("child").setSchemaName("main");
			child.getColumns().add(new Column("id").setDataType(DataType.INT));
			child.getColumns().add(new Column("parent_id").setDataType(DataType.INT));
			child.setPrimaryKey((String) null, child.getColumns().get("id"));
			child.getConstraints().addForeignKeyConstraint("fk", fk -> {
				fk.setColumns(child.getColumns().get("parent_id"));
				fk.setRelatedColumns(parent.getColumns().get("id"));
			});
			var relation = new TableRelationTreeHolder(parent, child).getTableRelation(child);
			var row = parent.getRows().newElement();
			row.put("id", 1);
			var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
			var select = registry.createSqlNodes(relation, SqlType.SELECT_BY_ROOT_ROWS).get(0);
			try (var parameters = select.eval(relation, List.of(row));
					var query = parameters.createStatement(connection)) {
				parameters.setBind(query);
				try (var result = query.executeQuery()) {
					int count = 0;
					while (result.next()) {
						assertEquals(1, result.getInt("parent_id"));
						count++;
					}
					assertEquals(2, count);
				}
			}
			var delete = registry.createSqlNodes(relation, SqlType.DELETE_BY_ROOT_ROWS).get(0);
			try (var parameters = delete.eval(relation, List.of());
					var query = parameters.createStatement(connection)) {
				assertEquals(0, query.executeUpdate());
			}
			try (var parameters = delete.eval(relation, List.of(row));
					var query = parameters.createStatement(connection)) {
				parameters.setBind(query);
				assertEquals(2, query.executeUpdate());
			}
			try (var result = statement.executeQuery("SELECT id FROM child")) {
				assertTrue(result.next());
				assertEquals(20, result.getInt(1));
				assertFalse(result.next());
			}
			var root = new TableRelationTreeHolder(parent, child).getTableRelation(parent);
			var rootSelect = registry.createSqlNodes(root, SqlType.SELECT_BY_ROOT_ROWS).get(0);
			try (var parameters = rootSelect.eval(root, List.of(row));
					var query = parameters.createStatement(connection)) {
				parameters.setBind(query);
				try (var result = query.executeQuery()) {
					assertTrue(result.next());
					assertEquals(1, result.getInt("id"));
					assertFalse(result.next());
				}
			}
			var rootDelete = registry.createSqlNodes(root, SqlType.DELETE_BY_ROOT_ROWS).get(0);
			try (var parameters = rootDelete.eval(root, List.of());
					var query = parameters.createStatement(connection)) {
				assertEquals(0, query.executeUpdate());
			}
			try (var parameters = rootDelete.eval(root, List.of(row));
					var query = parameters.createStatement(connection)) {
				parameters.setBind(query);
				assertEquals(1, query.executeUpdate());
			}
		}
	}

	@Test
	void compositeKeysAndEmptyInputsExecuteAcrossDialectVersions() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:");
				var statement = connection.createStatement()) {
			statement.execute("CREATE TABLE t(a INT,b TEXT,PRIMARY KEY(a,b))");
			statement.execute("INSERT INTO t VALUES(1,'one'),(1,'two'),(2,'two')");
			for (var dialect : List.of(DialectHolder.defaultDialect,
					DialectResolver.getInstance().getDialect("SQLite", 3, 7, 0),
					DialectResolver.getInstance().getDialect("SQLite", 3, 15, 0))) {
				var table = new Table("t").setSchemaName("main");
				table.getColumns().add(new Column("a").setDataType(DataType.INT));
				table.getColumns().add(new Column("b").setDataType(DataType.VARCHAR));
				table.setPrimaryKey((String) null, table.getColumns().get("a"), table.getColumns().get("b"));
				for (var pair : List.of(List.of(1, "one"), List.of(2, "two"))) {
					var row = table.getRows().newElement();
					row.put("a", pair.get(0));
					row.put("b", pair.get(1));
					table.getRows().add(row);
				}
				var node = dialect.createSqlFactoryRegistry().createSqlNodes(table, SqlType.SELECT_BY_ROWS).get(0);
				try (var parameters = node.eval(table); var query = parameters.createStatement(connection)) {
					parameters.setBind(query);
					try (var result = query.executeQuery()) {
						int count = 0;
						while (result.next())
							count++;
						assertEquals(2, count);
					}
				}
				table.getRows().clear();
				try (var parameters = node.eval(table);
						var query = parameters.createStatement(connection);
						var result = query.executeQuery()) {
					assertFalse(result.next());
				}
				var relation = new TableRelationTreeHolder(table).getTableRelation(table);
				var delete = SqlParser.getInstance().parse(dialect,
						"DELETE FROM main.t WHERE 1=1 /*ROWS=(target=ROOT)*/");
				try (var parameters = delete.eval(relation, List.of());
						var query = parameters.createStatement(connection)) {
					assertEquals(0, query.executeUpdate());
				}
			}
			try (var result = statement.executeQuery("SELECT count(*) FROM t")) {
				assertTrue(result.next());
				assertEquals(3, result.getInt(1));
			}
		}
	}
}
