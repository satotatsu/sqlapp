/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.sqlserver.sql;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.sqlserver.resolver.SqlServerDialectResolver;
import com.sqlapp.data.db.sql.*;
import com.sqlapp.data.schemas.*;

class SqlServerReviewRegressionTest {
	private Dialect dialect(int version) { return SqlServerDialectResolver.instance.getDialect(version, 0); }
	private Table table() {
		Table t = new Table("Audit").setSchemaName("dbo");
		t.getColumns().add(new Column("id").setDataType(DataType.INT));
		return t;
	}
	private String create(Dialect d, Table t, SqlType type) {
		return d.createSqlFactoryRegistry().createSql(t, type).stream().map(SqlOperation::getSqlText)
				.collect(Collectors.joining("\n")).replaceAll("\\s+", " ").trim();
	}
	private List<SqlOperation> alter(Dialect d, Table before, Table after) {
		return d.createSqlFactoryRegistry().getSqlFactory(after, SqlType.ALTER).createDiffSql(before.diff(after));
	}
	@Test void nativeTimestampRemainsRowversionAcrossThe2008Boundary() {
		for (int v : new int[] {8, 9, 10, 11, 13, 16}) {
			Dialect d = dialect(v);
			for (String name : new String[] {"timestamp", "rowversion"}) {
				Column c = new Column("rv");
				assertTrue(d.setDbType(name, 8L, 0, c));
				assertEquals(DataType.ROWVERSION, c.getDataType(), "version=" + v);
				assertTrue(d.isOptimisticLockColumn(c));
				assertEquals("TIMESTAMP", d.createSqlBuilder().addTypeDefinition(c).toString());
			}
			if (v >= 10) {
				for (String name : new String[] {"datetime2(3)", "datetimeoffset(3)"}) {
					Column c = new Column("moment"); d.setDbType(name, null, null, c);
					assertEquals(name.startsWith("datetime2") ? DataType.TIMESTAMP : DataType.TIMESTAMP_WITH_TIMEZONE,
							c.getDataType());
					assertEquals(3L, c.getLength());
				}
			}
		}
	}
	@Test void identitySeedAndIncrementArePreserved() {
		for (int v : new int[] {8, 9, 10, 16}) {
			Table t = table(); Column id = t.getColumns().get(0).setIdentity(true);
			assertTrue(create(dialect(v), t, SqlType.CREATE).contains("INT IDENTITY"));
			id.setIdentityStartValue(100).setIdentityStep(-5);
			assertTrue(create(dialect(v), t, SqlType.CREATE).contains("IDENTITY(100,-5)"));
			id.setIdentityStep(0);
			assertThrows(IllegalArgumentException.class, () -> create(dialect(v), t, SqlType.CREATE));
		}
	}
	@Test void filetableEmitsAnOperationWithOptionalEscapedOptions() {
		for (int v : new int[] {11, 12, 13, 16}) {
			Table t = new Table("Files"); t.getSpecifics().put("is_filetable", "true");
			assertEquals("CREATE TABLE Files AS FILETABLE", create(dialect(v), t, SqlType.CREATE));
			t.getSpecifics().put("directory_name", "team's files");
			t.getSpecifics().put("filename_collation_name", "database_default");
			String sql = create(dialect(v), t, SqlType.CREATE);
			assertTrue(sql.contains("team''s files"), sql);
			assertTrue(sql.contains("FILETABLE_COLLATE_FILENAME =database_default")
					|| sql.contains("FILETABLE_COLLATE_FILENAME = database_default"), sql);
		}
	}
	@Test void changeTrackingSupportsBothDirectionsAndColumnTrackingWithoutDuplicateOperations() {
		for (int v : new int[] {10, 11, 13, 16}) {
			Table before = table(), after = before.clone();
			after.getSpecifics().put("HAS_CHANGE_TRACKING", "true");
			var operations = alter(dialect(v), before, after);
			assertEquals(1, operations.size()); assertTrue(operations.get(0).getSqlText().contains("ENABLE CHANGE_TRACKING"));
			assertEquals(1, alter(dialect(v), after, before).size(), alter(dialect(v), after, before).toString());
			assertTrue(alter(dialect(v), after, before).get(0).getSqlText().contains("DISABLE CHANGE_TRACKING"));
			assertTrue(alter(dialect(v), after, after.clone()).isEmpty());
			Table columns = after.clone(); columns.getSpecifics().put("is_track_columns_updated_on", "true");
			assertTrue(alter(dialect(v), after, columns).get(0).getSqlText().contains("TRACK_COLUMNS_UPDATED = ON"));
			columns.getSpecifics().put("HAS_CHANGE_TRACKING", "false");
			assertThrows(IllegalArgumentException.class, () -> alter(dialect(v), after, columns));
		}
	}
	@Test void mergeRowsSeparatesCompositeOutputColumns() {
		Table t = table(); t.getColumns().add(new Column("other").setDataType(DataType.INT));
		t.getColumns().add(new Column("value").setDataType(DataType.INT));
		t.setPrimaryKey(t.getColumns().get(0), t.getColumns().get(1));
		assertTrue(create(dialect(16), t, SqlType.MERGE_ROWS).contains("OUTPUT _source_.id, _source_.other, $action;"));
	}
	@Test void allKeyMergeOmitsUpdateAndIdentityOnlyUsesDefaultValues() {
		for (SqlType type : new SqlType[] {SqlType.MERGE, SqlType.MERGE_TABLE, SqlType.MERGE_ROWS}) {
			Table t = table(); t.setPrimaryKey(t.getColumns().get(0));
			String sql = create(dialect(16), t, type);
			assertFalse(sql.contains("THEN UPDATE"), sql); assertTrue(sql.contains("THEN INSERT"), sql);
			t.getColumns().get(0).setIdentity(true);
			assertTrue(create(dialect(16), t, type).contains("INSERT DEFAULT VALUES"));
		}
	}
	@Test void renameEscapesNamesAndPrecedesCombinedTypeAlteration() {
		for (int v : new int[] {9, 10, 11, 13, 16}) {
			Table before = table(); before.getColumns().get(0).setName("o'ld]");
			Table after = before.clone(); after.getColumns().get(0).setName("new'name").setDataType(DataType.BIGINT);
			var operations = alter(dialect(v), before, after);
			assertEquals(2, operations.size(), operations.toString());
			assertTrue(operations.get(0).getSqlText().contains("EXEC sys.sp_rename"));
			assertTrue(operations.get(0).getSqlText().contains("o''ld]]"));
			assertTrue(operations.get(1).getSqlText().contains("BIGINT"));
		}
	}
	@Test void defaultChangeUsesNamedConstraintOperationsAndLegacyMetadataFallback() {
		for (int v : new int[] {9, 10, 11, 13, 16}) {
			Table before = table(); before.getColumns().get(0).setDefaultValue("1");
			before.getColumns().get(0).getSpecifics().put("default_name", "DF_Audit_id");
			Table after = before.clone(); after.getColumns().get(0).setDefaultValue("2");
			var operations = alter(dialect(v), before, after);
			assertEquals(2, operations.size(), operations.toString());
			assertTrue(operations.get(0).getSqlText().contains("DROP CONSTRAINT DF_Audit_id"));
			assertTrue(operations.get(1).getSqlText().contains("DEFAULT 2 FOR id"));
			before.getColumns().get(0).getSpecifics().remove("default_name");
			after.getColumns().get(0).setDefaultValue(null); after.getColumns().get(0).getSpecifics().remove("default_name");
			assertTrue(alter(dialect(v), before, after).get(0).getSqlText().contains("sys.default_constraints"));
			assertEquals(1, alter(dialect(v), after, before).size(), alter(dialect(v), after, before).toString());
			Table wider = before.clone(); wider.getColumns().get(0).setDataType(DataType.BIGINT);
			var sql = alter(dialect(v), before, wider);
			assertEquals(1, sql.size(), sql.toString()); assertFalse(sql.get(0).getSqlText().contains("DEFAULT"));
			assertEquals("1", wider.getColumns().get(0).getDefaultValue());
		}
	}
	@Test void namedDefaultSurvivesXmlAndCreateAndCanBeRenamed() throws Exception {
		Table before = table(); Column column = before.getColumns().get(0).setDefaultValue("1");
		column.getSpecifics().put("default_name", "DF_o'ld]");
		Table restored = SchemaUtils.readXml(new java.io.StringReader(before.asXml()));
		String create = create(dialect(16), restored, SqlType.CREATE);
		assertTrue(create.contains("CONSTRAINT [DF_o'ld]]] DEFAULT 1"), create);
		Table after = before.clone(); after.getColumns().get(0).getSpecifics().put("default_name", "DF_new");
		var operations = alter(dialect(16), before, after);
		assertEquals(2, operations.size());
		assertTrue(operations.get(1).getSqlText().contains("CONSTRAINT DF_new DEFAULT 1 FOR id"));
		Table removed = before.clone(); removed.getColumns().clear();
		var drop = alter(dialect(16), before, removed);
		assertEquals(2, drop.size());
		assertTrue(drop.get(0).getSqlText().contains("DROP CONSTRAINT"));
		assertTrue(drop.get(1).getSqlText().contains("DROP COLUMN"));
	}
	@Test void disablingTrackingDoesNotRequireDeletingReadColumnOptions() {
		Table before = table(); before.getSpecifics().put("HAS_CHANGE_TRACKING", "true");
		before.getSpecifics().put("is_track_columns_updated_on", "true");
		Table after = before.clone(); after.getSpecifics().put("HAS_CHANGE_TRACKING", "false");
		var operations = alter(dialect(16), before, after);
		assertEquals(1, operations.size());
		assertTrue(operations.get(0).getSqlText().contains("DISABLE CHANGE_TRACKING"));
	}

	@Test void sparseColumnsSurviveCreateAndInvalidCombinationsFail() {
		Table t = table(); t.getColumns().get(0).getSpecifics().put("is_sparse", "true");
		assertTrue(create(dialect(10), t, SqlType.CREATE).contains("INT SPARSE"));
		assertThrows(IllegalArgumentException.class, () -> create(dialect(9), t, SqlType.CREATE));
		t.getColumns().get(0).setNotNull(true);
		assertThrows(IllegalArgumentException.class, () -> create(dialect(16), t, SqlType.CREATE));
	}
}
