/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.postgres.metadata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringReader;
import java.sql.Types;
import java.util.List;

import javax.sql.rowset.RowSetMetaDataImpl;
import javax.sql.rowset.RowSetProvider;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.postgres.DialectHolder;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Index;
import com.sqlapp.data.schemas.Mview;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.SchemaUtils;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.jdbc.ExResultSet;

class PostgresMviewOwnerTest {
	@Test
	void isolatesIndexIfNotExistsAtPostgres95Boundary() {
		for (var dialect : List.of(DialectHolder.postgreSQL93, DialectHolder.postgreSQL94, DialectHolder.postgreSQL95,
				DialectHolder.postgreSQL100, DialectHolder.postgreSQL150, DialectHolder.postgreSQL180)) {
			var table = new Table("items");
			table.getColumns().add("id", column -> column.setDataType(DataType.INT));
			var index = new Index("by_id");
			index.getColumns().add("id");
			table.getIndexes().add(index);
			var registry = dialect.createSqlFactoryRegistry();
			registry.getOptions().setCreateIfNotExists(true);
			boolean expected = dialect != DialectHolder.postgreSQL93 && dialect != DialectHolder.postgreSQL94;
			assertEquals(expected,
					registry.createSql(index, SqlType.CREATE).getFirst().getSqlText().contains("IF NOT EXISTS"));
		}
	}

	@Test
	void readsMaterializedViewColumnsAcrossVersionBoundaries() {
		for (var dialect : List.of(DialectHolder.postgreSQL93, DialectHolder.postgreSQL150,
				DialectHolder.postgreSQL180)) {
			var reader = (PostgresColumnReader) new Postgres93MviewReader(dialect).getColumnReader();
			assertTrue(java.util.Arrays.asList(reader.getType()).contains("m"));
		}
	}

	@Test
	void appliesIndexAndColumnCommentDifferencesBeforeOwnership() {
		var original = new Mview("totals").setOwnerName("root");
		original.setSchemaName("reports").setStatement("SELECT 7");
		original.getColumns().add(new Column("total").setRemarks("old column"));
		for (String name : List.of("removed_index", "retained_index")) {
			var index = new Index(name);
			index.getColumns().add("total");
			original.getIndexes().add(index);
		}
		var target = (Mview) original.clone();
		target.setOwnerName("Reporting Owners");
		target.getColumns().get("total").setRemarks(null);
		target.getIndexes().remove(target.getIndexes().get("removed_index"));
		target.getIndexes().get("retained_index").setRemarks("'index'");
		var added = new Index("added_index");
		added.getColumns().add("total");
		target.getIndexes().add(added);
		var registry = DialectHolder.postgreSQL150.createSqlFactoryRegistry();
		registry.getOptions().setDecorateSchemaName(true);
		var operations = registry.createSql(original.diff(target));
		assertEquals(5, operations.size());
		assertTrue(operations.get(0).getSqlText().startsWith("DROP INDEX"));
		assertTrue(operations.get(1).getSqlText().startsWith("CREATE INDEX"));
		assertEquals("COMMENT ON INDEX reports.retained_index IS '''index'''", operations.get(2).getSqlText());
		assertEquals("COMMENT ON COLUMN reports.totals.total IS NULL", operations.get(3).getSqlText());
		assertTrue(operations.getLast().getSqlText().endsWith("OWNER TO \"Reporting Owners\""));
		target.getIndexes().get("retained_index").setUnique(true);
		assertThrows(UnsupportedOperationException.class, () -> registry.createSql(original.diff(target)));
		target.getIndexes().get("retained_index").setUnique(false);
		target.getColumns().add(new Column("unexpected"));
		assertThrows(UnsupportedOperationException.class, () -> registry.createSql(original.diff(target)));
	}

	@Test
	void recreatesTablespaceIndexesAndCommentsBeforeOwnership() throws Exception {
		for (var dialect : List.of(DialectHolder.postgreSQL93, DialectHolder.postgreSQL150,
				DialectHolder.postgreSQL180)) {
			var schema = new Schema("reports");
			schema.setDialect(dialect);
			var view = new Mview("Daily Totals").setOwnerName("Reporting Owners");
			view.setStatement("SELECT 7").setTableSpaceName("Fast Space").setRemarks("view note");
			view.getColumns().add(new Column("Result ID").setRemarks("'column'"));
			var index = new Index("By result");
			index.getColumns().add("Result ID");
			index.setRemarks("'index'");
			view.getIndexes().add(index);
			schema.getMviews().add(view);
			Schema restored = SchemaUtils.readXml(new StringReader(schema.asXml()));
			restored.setDialect(dialect);
			var registry = dialect.createSqlFactoryRegistry();
			registry.getOptions().setDecorateSchemaName(true);
			var operations = registry.createSql(restored.getMviews().get("Daily Totals"), SqlType.CREATE);
			assertEquals(6, operations.size());
			assertTrue(operations.getFirst().getSqlText().contains("TABLESPACE \"Fast Space\""));
			assertTrue(operations.get(1).getSqlText().startsWith("CREATE INDEX"));
			assertEquals("COMMENT ON INDEX reports.\"By result\" IS '''index'''", operations.get(2).getSqlText());
			assertEquals("COMMENT ON COLUMN reports.\"Daily Totals\".\"Result ID\" IS '''column'''",
					operations.get(3).getSqlText());
			assertTrue(operations.get(4).getSqlText().startsWith("COMMENT ON MATERIALIZED VIEW"));
			assertTrue(operations.getLast().getSqlText().endsWith("OWNER TO \"Reporting Owners\""));
			var original = (Mview) view.clone();
			var moved = (Mview) original.clone();
			moved.setTableSpaceName("Archive Space");
			assertTrue(registry.createSql(original.diff(moved)).getFirst().getSqlText()
					.endsWith(" SET TABLESPACE \"Archive Space\""));
			moved.setTableSpaceName(null);
			assertThrows(IllegalArgumentException.class, () -> registry.createSql(original.diff(moved)));
		}
	}

	@Test
	void changesAndClearsCommentsBeforeTransferringOwnership() {
		for (var dialect : List.of(DialectHolder.postgreSQL93, DialectHolder.postgreSQL150,
				DialectHolder.postgreSQL180)) {
			var original = new Schema("reports");
			original.setDialect(dialect);
			var view = new Mview("Daily Totals").setOwnerName("root");
			view.setStatement("SELECT 7").setRemarks("before");
			original.getMviews().add(view);
			var target = original.clone();
			var changed = target.getMviews().get("Daily Totals");
			changed.setRemarks("'quoted'");
			changed.setOwnerName("Reporting Owners");
			var registry = dialect.createSqlFactoryRegistry();
			registry.getOptions().setDecorateSchemaName(true);
			var operations = registry.createSql(original.diff(target));
			assertEquals(2, operations.size());
			assertEquals("COMMENT ON MATERIALIZED VIEW reports.\"Daily Totals\" IS '''quoted'''",
					operations.getFirst().getSqlText());
			assertTrue(operations.getLast().getSqlText().endsWith("OWNER TO \"Reporting Owners\""));
			changed.setOwnerName("root").setRemarks(null);
			var clear = registry.createSql(original.diff(target));
			assertEquals(1, clear.size());
			assertTrue(clear.getFirst().getSqlText().endsWith(" IS NULL"));
			changed.setStatement("SELECT 8");
			assertThrows(UnsupportedOperationException.class, () -> registry.createSql(original.diff(target)));
		}
	}

	@Test
	void dropsMaterializedViewBeforeItsSourceTableInSchemaDiff() {
		var schema = new Schema("reports");
		schema.setDialect(DialectHolder.postgreSQL150);
		var table = new Table("events");
		table.getColumns().add("id", column -> column.setDataType(DataType.BIGINT));
		schema.getTables().add(table);
		var view = new Mview("totals");
		view.setStatement("SELECT count(*) FROM reports.events");
		schema.getMviews().add(view);
		var target = schema.clone();
		target.getMviews().clear();
		target.getTables().clear();
		var drops = DialectHolder.postgreSQL150.createSqlFactoryRegistry().createSql(schema.diff(target)).stream()
				.filter(operation -> operation.getSqlType() == SqlType.DROP).toList();
		assertEquals(2, drops.size());
		assertTrue(drops.getFirst().getSqlText().startsWith("DROP MATERIALIZED VIEW"));
		assertTrue(drops.getLast().getSqlText().startsWith("DROP TABLE"));
	}

	@Test
	void restoresXmlAndChangesSchemaOwnerAcrossSupportedBoundaries() throws Exception {
		for (var dialect : List.of(DialectHolder.postgreSQL93, DialectHolder.postgreSQL150,
				DialectHolder.postgreSQL180)) {
			var schema = new Schema("reports");
			var view = new Mview("Daily Totals").setOwnerName("Reporting Owners");
			view.setStatement("SELECT 7 AS total");
			view.getColumns().add(new Column("Total \"Count\""));
			view.getColumns().add(new Column("rowid").setHidden(true));
			schema.getMviews().add(view);
			Schema restored = SchemaUtils.readXml(new StringReader(schema.asXml()));
			var registry = dialect.createSqlFactoryRegistry();
			registry.getOptions().setDecorateSchemaName(true);
			var create = registry.createSql(restored.getMviews().get("Daily Totals"), SqlType.CREATE);
			assertEquals(2, create.size());
			assertEquals(
					"CREATE MATERIALIZED VIEW reports.\"Daily Totals\" (\"Total \"\"Count\"\"\") AS SELECT 7 AS total",
					create.getFirst().getSqlText().replaceAll("\\s+", " "));
			assertTrue(create.getLast().getSqlText().endsWith("OWNER TO \"Reporting Owners\""));
			var drop = registry.createSql(view, SqlType.DROP);
			assertEquals(1, drop.size());
			assertEquals("DROP MATERIALIZED VIEW reports.\"Daily Totals\"", drop.getFirst().getSqlText());
			var refresh = registry.createSql(view, SqlType.REFRESH);
			assertEquals(1, refresh.size());
			assertEquals("REFRESH MATERIALIZED VIEW reports.\"Daily Totals\"", refresh.getFirst().getSqlText());
			String definition = "CREATE MATERIALIZED VIEW reports.\"Daily Totals\" AS SELECT 8 AS native_total";
			view.setDefinition(definition);
			assertEquals(definition, registry.createSql(view, SqlType.CREATE).getFirst().getSqlText());
			var target = restored.clone();
			target.getMviews().get("Daily Totals").setOwnerName("New Owners");
			var alter = registry.createSql(restored.diff(target));
			assertEquals(1, alter.size());
			assertTrue(alter.getFirst().getSqlText().endsWith("OWNER TO \"New Owners\""));
			assertNotEquals(registry.getSqlFactory(view, SqlType.CREATE).getClass(), DialectHolder.postgreSQL92
					.createSqlFactoryRegistry().getSqlFactory(view, SqlType.CREATE).getClass());
		}
	}

	@Test
	void readsCatalogOwnerAndGeneratesOwnershipSql() throws Exception {
		var metadata = new RowSetMetaDataImpl();
		String[] names = { "matviewname", "schemaname", "tablespace", "definition", "matviewowner", "remarks" };
		metadata.setColumnCount(names.length);
		for (int i = 0; i < names.length; i++) {
			metadata.setColumnName(i + 1, names[i]);
			metadata.setColumnLabel(i + 1, names[i]);
			metadata.setColumnType(i + 1, Types.VARCHAR);
		}
		try (var rows = RowSetProvider.newFactory().createCachedRowSet()) {
			rows.setMetaData(metadata);
			rows.moveToInsertRow();
			rows.updateString("matviewname", "totals");
			rows.updateString("schemaname", "reports");
			rows.updateNull("tablespace");
			rows.updateString("definition", "SELECT 7 AS total");
			rows.updateString("matviewowner", "Report \"Owners\"");
			rows.updateString("remarks", "Reporter's totals");
			rows.insertRow();
			rows.moveToCurrentRow();
			rows.beforeFirst();
			assertTrue(rows.next());
			var view = (Mview) new Postgres93MviewReader(DialectHolder.postgreSQL150)
					.createTable(new ExResultSet(rows));
			assertEquals("Report \"Owners\"", view.getOwnerName());
			assertEquals("Reporter's totals", view.getRemarks());
			var schema = new Schema("reports");
			schema.getMviews().add((Mview) view.clone());
			Schema restored = SchemaUtils.readXml(new StringReader(schema.asXml()));
			assertEquals(view.getRemarks(), restored.getMviews().get("totals").getRemarks());
			var registry = DialectHolder.postgreSQL150.createSqlFactoryRegistry();
			registry.getOptions().setDecorateSchemaName(true);
			var create = registry.createSql(restored.getMviews().get("totals"), SqlType.CREATE);
			assertEquals(3, create.size());
			assertTrue(create.getFirst().getSqlText().startsWith("CREATE MATERIALIZED VIEW"));
			assertEquals("COMMENT ON MATERIALIZED VIEW reports.totals IS 'Reporter''s totals'",
					create.get(1).getSqlText());
			assertEquals("ALTER MATERIALIZED VIEW reports.totals OWNER TO \"Report \"\"Owners\"\"\"",
					create.getLast().getSqlText());
			var target = (Mview) view.clone();
			target.setOwnerName("CURRENT_USER");
			assertTrue(registry.createSql(view.diff(target)).getFirst().getSqlText()
					.endsWith("OWNER TO \"CURRENT_USER\""));
			target.setDialect(new com.sqlapp.data.schemas.Schema("reports").getDialect());
			assertEquals(1, registry.createSql(target.diff(view)).size());
			target.setOwnerName(null);
			assertThrows(IllegalArgumentException.class, () -> registry.createSql(view.diff(target)));
			target.setOwnerName("other").setStatement("SELECT 8");
			assertThrows(UnsupportedOperationException.class, () -> registry.createSql(view.diff(target)));
		}
	}
}
