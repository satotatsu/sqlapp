/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.sqlite.sql;

import static org.junit.jupiter.api.Assertions.*;
import java.sql.Connection;
import java.sql.DriverManager;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.sqlite.DialectHolder;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Table;

class SqliteVirtualTableDmlTest {
	private Table read(Connection connection, String schema, String name) {
		var reader = DialectHolder.defaultDialect.getCatalogReader().getSchemaReader().getTableReader();
		reader.setSchemaName(schema); reader.setObjectName(name);
		return reader.getAllFull(connection).get(0);
	}

	@Test
	void insertBindsOnlyVisibleColumnsAndNeverExecutesFtsControlValues() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:"); var statement = connection.createStatement()) {
			statement.execute("CREATE VIRTUAL TABLE docs USING fts5(body)");
			statement.execute("INSERT INTO docs(body) VALUES('kept')");
			var table = read(connection,"main","docs");
			var row = table.getRows().newElement(); row.put("body","searchable");
			row.put("docs","delete-all"); row.put("rank",99); table.getRows().add(row);
			var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
			var node = registry.createSqlNodes(table,SqlType.INSERT).get(0);
			try (var parameters = node.eval(row); var insert = parameters.createStatement(connection)) {
				assertEquals(1,parameters.getBindParameters().size()); parameters.setBind(insert); insert.executeUpdate();
			}
			try (var result = statement.executeQuery("SELECT count(*) FROM docs")) { assertTrue(result.next()); assertEquals(2,result.getInt(1)); }
			try (var result = statement.executeQuery("SELECT body FROM docs WHERE docs MATCH 'searchable'")) {
				assertTrue(result.next()); assertEquals("searchable",result.getString(1));
			}
		}
	}

	@Test
	void tableSourceInsertDoesNotRequireHiddenControlColumnsInStaging() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:"); var statement = connection.createStatement()) {
			statement.execute("ATTACH ':memory:' AS other");
			statement.execute("CREATE VIRTUAL TABLE other.docs USING fts5(body)");
			statement.execute("CREATE TEMP TABLE stage(body TEXT)");
			statement.execute("INSERT INTO stage VALUES('first'),('second')");
			var target = read(connection,"other","docs"); var source = read(connection,"temp","stage");
			var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
			var factory = (SqliteInsertSelectTableFactory)registry.getSqlFactory(target,SqlType.INSERT_SELECT_TABLE);
			statement.execute(factory.createSql(target,source).get(0).getSqlText());
			try (var result = statement.executeQuery("SELECT count(*) FROM other.docs")) { assertTrue(result.next()); assertEquals(2,result.getInt(1)); }
			try (var result = statement.executeQuery("SELECT body FROM other.docs WHERE docs MATCH 'second'")) { assertTrue(result.next()); assertEquals("second",result.getString(1)); }
		}
	}

	@Test
	void updateUsesAnExplicitRowidKeyAndOmitsHiddenAssignments() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:"); var statement = connection.createStatement()) {
			statement.execute("CREATE VIRTUAL TABLE docs USING fts5(body)");
			statement.execute("INSERT INTO docs(rowid,body) VALUES(1,'before'),(2,'kept')");
			var table = read(connection,"main","docs");
			// Virtual-table keys are module-specific; the caller explicitly models SQLite's rowid here.
			var rowid = new Column("rowid").setDataType(DataType.BIGINT); table.getColumns().add(rowid); table.setPrimaryKey((String)null,rowid);
			var row = table.getRows().newElement(); row.put("rowid",1L); row.put("body","after");
			row.put("docs","delete-all"); row.put("rank",99); table.getRows().add(row);
			var node = DialectHolder.defaultDialect.createSqlFactoryRegistry().createSqlNodes(table,SqlType.UPDATE).get(0);
			try (var parameters = node.eval(row); var update = parameters.createStatement(connection)) { parameters.setBind(update); update.executeUpdate(); }
			try (var result = statement.executeQuery("SELECT body FROM docs ORDER BY rowid")) {
				assertTrue(result.next()); assertEquals("after",result.getString(1));
				assertTrue(result.next()); assertEquals("kept",result.getString(1)); assertFalse(result.next());
			}
		}
	}

	@Test
	void everyMergeEntryPointRejectsVirtualTargetsBeforeKeyResolution() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:"); var statement = connection.createStatement()) {
			statement.execute("CREATE VIRTUAL TABLE docs USING fts5(body)");
			var table = read(connection,"main","docs"); var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
			for (SqlType type : new SqlType[] {SqlType.MERGE,SqlType.MERGE_ROWS,SqlType.MERGE_TABLE}) {
				var error = assertThrows(IllegalArgumentException.class,() -> registry.createSql(table,type));
				assertTrue(error.getMessage().contains("virtual tables"));
			}
			var definitionOnly = new Table("docs"); definitionOnly.setDefinition("CREATE VIRTUAL TABLE docs USING fts5(body)");
			assertThrows(IllegalArgumentException.class,() -> registry.createSql(definitionOnly,SqlType.MERGE));
		}
	}
}
