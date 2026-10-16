/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.oracle.metadata;

import static org.junit.jupiter.api.Assertions.*;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.oracle.DialectHolder;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.*;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.JdbcQueryHandler;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;

class OracleSchemaParityRegressionTest {
	private ExResultSet row(Map<String, String> values) {
		return new ExResultSet(null) {
			private boolean missing;
			@Override public String getString(String key) { return values.get(key.toUpperCase(java.util.Locale.ROOT)); }
			@Override public Object getObject(String key) { return getString(key); }
			@Override public Long getLongValue(String key) { String value = getString(key); return value == null ? null : Long.valueOf(value); }
			@Override public long getLong(String key) { missing = values.get(key) == null; return missing ? 0 : Long.parseLong(values.get(key)); }
			@Override public int getInt(String key) { return (int) getLong(key); }
			@Override public boolean wasNull() { return missing; }
			@Override public Timestamp getTimestamp(String key) { return null; }
		};
	}
	private String create(Table table) {
		return DialectHolder.oracle26aiDialect.createSqlFactoryRegistry().createSql(table, SqlType.CREATE).get(0).getSqlText().replaceAll("\\s+", " ");
	}
	@Test
	void generatedColumnAlterFailsRatherThanEmittingCreateClausesInModify() {
		Table table = new Table("T"); table.getColumns().add(new Column("ID").setDataType(DataType.INT).setIdentity(true));
		Table target = table.clone(); target.getColumns().get("ID").setIdentityStartValue(10);
		var factory = DialectHolder.oracle26aiDialect.createSqlFactoryRegistry().getSqlFactory(target, SqlType.ALTER);
		assertThrows(IllegalArgumentException.class, () -> factory.createDiffSql(table.diff(target)));
		table.getColumns().get("ID").setDefaultValue("42");
		assertThrows(IllegalArgumentException.class, () -> create(table));
	}
	@Test
	void virtualColumnsUseFormulaRatherThanDefaultAndRespectVersionBoundary() throws Exception {
		Column column = new Oracle11gColumnReader(DialectHolder.oracle11gR2Dialect).createColumn(row(Map.of("OWNER", "APP", "TABLE_NAME", "T", "COLUMN_NAME", "V", "DATA_TYPE", "NUMBER", "VIRTUAL_COLUMN", "YES", "DATA_DEFAULT", "ID*2", "NULLABLE", "Y")));
		assertEquals("ID*2", column.getFormula()); assertNull(column.getDefaultValue());
		Table table = new Table("T"); table.getColumns().add(column);
		Table restored = SchemaUtils.readXml(new java.io.StringReader(table.asXml()));
		String sql = create(restored); assertTrue(sql.contains("GENERATED ALWAYS AS (ID*2) VIRTUAL"), sql); assertFalse(sql.contains("DEFAULT"));
		assertThrows(IllegalArgumentException.class, () -> DialectHolder.oracle10gDialect.createSqlFactoryRegistry().createSql(restored, SqlType.CREATE));
		column.setFormulaPersisted(true); assertThrows(IllegalArgumentException.class, () -> create(table));
	}
	@Test
	void identityAndDefaultOnNullSurviveReadingAndXml() throws Exception {
		for (String generation : List.of("ALWAYS", "BY DEFAULT", "BY DEFAULT ON NULL")) {
			Column column = new Oracle12cColumnReader(DialectHolder.oracle26aiDialect).createColumn(row(Map.of("OWNER", "APP", "TABLE_NAME", "T", "COLUMN_NAME", "ID", "DATA_TYPE", "NUMBER", "GENERATION_TYPE", generation,
					"IDENTITY_OPTIONS", "START WITH: 10, INCREMENT BY: 2, CACHE_SIZE: 0", "NULLABLE", "N")));
			Table table = new Table("T"); table.getColumns().add(column);
			Table restored = SchemaUtils.readXml(new java.io.StringReader(table.asXml()));
			String sql = create(restored); assertTrue(sql.contains("GENERATED " + generation + " AS IDENTITY"), sql);
			assertTrue(sql.contains("START WITH 10 INCREMENT BY 2"), sql); assertTrue(sql.contains("NOCACHE"), sql);
			assertTrue(!sql.contains("NOT NULL") || sql.indexOf("AS IDENTITY") < sql.indexOf("NOT NULL"), sql);
			assertThrows(IllegalArgumentException.class, () -> DialectHolder.oracle11gR2Dialect.createSqlFactoryRegistry().createSql(restored, SqlType.CREATE));
		}
		Column column = new Oracle12cColumnReader(DialectHolder.oracle12cDialect).createColumn(row(Map.of("OWNER", "APP", "TABLE_NAME", "T", "COLUMN_NAME", "ID", "DATA_TYPE", "NUMBER", "DEFAULT_ON_NULL", "YES", "DATA_DEFAULT", "42", "NULLABLE", "N")));
		Table table = new Table("T"); table.getColumns().add(column); assertTrue(create(table).contains("DEFAULT ON NULL 42"));
		assertThrows(IllegalArgumentException.class, () -> DialectHolder.oracle11gR2Dialect.createSqlFactoryRegistry().createSql(table, SqlType.CREATE));
	}
	@Test
	void unconstrainedNumbersAndScaleOnlyNumbersSurviveReadingAndXml() throws Exception {
		for (String scale : List.of("", "2", "-3")) {
			Map<String, String> values = new HashMap<>(Map.of("OWNER", "APP", "TABLE_NAME", "T", "COLUMN_NAME", "N", "DATA_TYPE", "NUMBER", "NULLABLE", "Y"));
			if (!scale.isEmpty()) values.put("DATA_SCALE", scale);
			Column column = new OracleColumnReader(DialectHolder.oracle26aiDialect).createColumn(row(values));
			assertNull(column.getLength());
			Table table = new Table("T"); table.getColumns().add(column);
			Table restored = SchemaUtils.readXml(new java.io.StringReader(table.asXml()));
			String sql = create(restored);
			assertTrue(sql.contains(scale.isEmpty() ? "N NUMBER" : "N NUMBER(*," + scale + ")"), sql);
			assertFalse(sql.contains("NUMBER(0"));
		}
	}
	@Test
	void tableStorageAndTemporaryDurationSurviveReadingAndXml() throws Exception {
		for (String duration : List.of("SYS$SESSION", "SYS$TRANSACTION")) {
			Table table = new OracleTableReader(DialectHolder.oracle26aiDialect).createTable(row(Map.of("OWNER", "APP", "TABLE_NAME", "T", "TEMPORARY", "Y", "DURATION", duration, "STATUS", "VALID")));
			table.getColumns().add(new Column("ID").setDataType(DataType.INT));
			assertFalse(table.isCompression());
			Table restored = SchemaUtils.readXml(new java.io.StringReader(table.asXml()));
			String sql = create(restored);
			assertTrue(sql.startsWith("CREATE GLOBAL TEMPORARY TABLE"), sql);
			assertTrue(sql.contains(duration.equals("SYS$SESSION") ? "ON COMMIT PRESERVE ROWS" : "ON COMMIT DELETE ROWS"), sql);
			assertFalse(sql.contains("oracle.table.duration"));
		}
		Table table = new OracleTableReader(DialectHolder.oracle26aiDialect).createTable(row(Map.of("OWNER", "APP", "TABLE_NAME", "T", "COMPRESSION", "ENABLED", "COMPRESS_FOR", "OLTP", "TABLESPACE_NAME", "Data Space", "STATUS", "VALID")));
		table.getColumns().add(new Column("ID").setDataType(DataType.INT));
		String sql = create(table);
		assertTrue(sql.contains("TABLESPACE \"Data Space\""), sql); assertTrue(sql.contains("COMPRESS FOR OLTP"), sql); assertFalse(sql.contains("COMPRESSFOR"));
		table.getSpecifics().put("COMPRESS_FOR", "invalid"); assertThrows(IllegalArgumentException.class, () -> create(table));
	}
	@Test
	void constraintsRetainStateForCreateAndAlterAddAcrossVersions() throws Exception {
		Table table = new Table("T").setSchemaName("APP"); table.getColumns().add(new Column("ID").setDataType(DataType.INT));
		Table parent = new Table("P").setSchemaName("BASE"); parent.getColumns().add(new Column("ID").setDataType(DataType.INT));
		CheckConstraint check = new CheckConstraint("CK", "ID > 0");
		UniqueConstraint unique = new UniqueConstraint("UK", false); unique.getColumns().add("ID");
		ForeignKeyConstraint fk = new ForeignKeyConstraint("FK", new Column[] {table.getColumns().get("ID")}, new Column[] {parent.getColumns().get("ID")});
		fk.setDeleteRule(CascadeRule.SetNull); fk.setUpdateRule(CascadeRule.None);
		for (Constraint constraint : List.of(check, unique, fk)) {
			constraint.setDeferrability(Deferrability.InitiallyDeferred); constraint.setEnable(false);
			constraint.getSpecifics().put("VALIDATED", "NOT VALIDATED"); constraint.getSpecifics().put("RELY", "RELY");
			table.getConstraints().add(constraint);
		}
		Table restored = SchemaUtils.readXml(new java.io.StringReader(table.asXml()));
		for (var dialect : List.of(DialectHolder.defaultDialect, DialectHolder.oracle11gR2Dialect, DialectHolder.oracle19cDialect, DialectHolder.oracle23aiDialect, DialectHolder.oracle26aiDialect)) {
			var registry = dialect.createSqlFactoryRegistry();
			String create = registry.createSql(restored, SqlType.CREATE).get(0).getSqlText();
			assertEquals(3, create.split("DEFERRABLE INITIALLY DEFERRED RELY DISABLE NOVALIDATE", -1).length - 1, create);
			assertTrue(create.contains("ON DELETE SET NULL"), create);
			for (Constraint constraint : restored.getConstraints()) assertTrue(registry.createSql(constraint, SqlType.CREATE).get(0).getSqlText().contains("DISABLE NOVALIDATE"));
		}
		fk.setUpdateRule(CascadeRule.Cascade); assertThrows(IllegalArgumentException.class, () -> create(table));
		fk.setUpdateRule(CascadeRule.None); fk.setDeleteRule(CascadeRule.SetDefault); assertThrows(IllegalArgumentException.class, () -> create(table));
		fk.setDeleteRule(CascadeRule.None);
		unique.getColumns().get(0).setOrder(Order.Desc); assertThrows(IllegalArgumentException.class, () -> create(table));
	}
	private Map<String, String> constraintValues() {
		return Map.ofEntries(Map.entry("OWNER", "APP"), Map.entry("TABLE_NAME", "T"), Map.entry("CONSTRAINT_NAME", "C"),
				Map.entry("COLUMN_NAME", "ID"), Map.entry("SEARCH_CONDITION", "ID > 0"), Map.entry("STATUS", "DISABLED"),
				Map.entry("DEFERRABLE", "DEFERRABLE"), Map.entry("DEFERRED", "DEFERRED"), Map.entry("VALIDATED", "NOT VALIDATED"),
				Map.entry("RELY", "RELY"), Map.entry("REFERENCE_OWNER", "BASE"), Map.entry("REFERENCE_TABLE_NAME", "P"), Map.entry("REFERENCE_COLUMN_NAME", "ID"));
	}
	private JdbcQueryHandler emit(ResultSetNextHandler handler, AtomicInteger calls) {
		calls.incrementAndGet(); try { handler.handleResultSetNext(row(constraintValues())); } catch (SQLException e) { throw new RuntimeException(e); } return null;
	}
	@Test
	void namedAndDisabledNotNullChecksAreNotDiscarded() {
		var reader = new OracleCheckConstraintReader(DialectHolder.oracle26aiDialect) {
			@Override protected JdbcQueryHandler execute(Connection c, SqlNode n, ParametersContext p, ResultSetNextHandler h) {
				try {
					for (String name : List.of("NAMED", "DISABLED", "GENERATED")) {
						var values = new HashMap<>(constraintValues());
						values.put("CONSTRAINT_NAME", name); values.put("SEARCH_CONDITION", "\"ID\" IS NOT NULL");
						values.put("GENERATED", name.equals("NAMED") ? "USER NAME" : "GENERATED NAME");
						values.put("STATUS", name.equals("DISABLED") ? "DISABLED" : "ENABLED");
						values.put("VALIDATED", "VALIDATED"); values.put("DEFERRABLE", "NOT DEFERRABLE");
						h.handleResultSetNext(row(values));
					}
				} catch (SQLException e) { throw new RuntimeException(e); }
				return null;
			}
		};
		var checks = reader.doGetAll(null, new ParametersContext(), new ProductVersionInfo());
		assertEquals(List.of("NAMED", "DISABLED"), checks.stream().map(CheckConstraint::getName).toList());
	}
	@Test
	void allConstraintReadersKeepValidationAndRelyWithOneQueryEach() {
		AtomicInteger calls = new AtomicInteger();
		var check = new OracleCheckConstraintReader(DialectHolder.oracle26aiDialect) {
			@Override protected JdbcQueryHandler execute(Connection c, SqlNode n, ParametersContext p, ResultSetNextHandler h) { return emit(h, calls); }
		}.doGetAll(null, new ParametersContext(), new ProductVersionInfo()).get(0);
		var unique = new OracleUniqueConstraintReader(DialectHolder.oracle26aiDialect) {
			@Override protected JdbcQueryHandler execute(Connection c, SqlNode n, ParametersContext p, ResultSetNextHandler h) { return emit(h, calls); }
		}.doGetAll(null, new ParametersContext(), new ProductVersionInfo()).get(0);
		var fk = new OracleForeignKeyConstraintReader(DialectHolder.oracle26aiDialect) {
			@Override protected JdbcQueryHandler execute(Connection c, SqlNode n, ParametersContext p, ResultSetNextHandler h) { return emit(h, calls); }
		}.doGetAll(null, new ParametersContext(), new ProductVersionInfo()).get(0);
		for (Constraint constraint : List.of(check, unique, fk)) {
			assertEquals("NOT VALIDATED", constraint.getSpecifics().get("VALIDATED")); assertEquals("RELY", constraint.getSpecifics().get("RELY"));
			assertFalse(constraint.isEnable()); assertEquals(Deferrability.InitiallyDeferred, constraint.getDeferrability());
		}
		assertEquals(3, calls.get()); assertEquals("BASE", fk.getRelatedTableSchemaName());
	}
	@Test
	void foreignKeyQueryDoesNotFilterTheReferencedTableSet() throws Exception {
		try (var stream = OracleForeignKeyConstraintReader.class.getResourceAsStream("foreignKeyConstraints.sql")) {
			String query = new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
			assertTrue(query.contains("INNER JOIN all_constraints rc")); assertFalse(query.contains("INNER JOIN tmp_constraints rc"));
			assertTrue(query.contains("WHERE c.CONSTRAINT_TYPE='R'"));
		}
	}
	@Test
	void indexCreationQualifiesTheIndexOwner() {
		Table table = new Table("T").setSchemaName("MixedOwner"); table.getColumns().add("ID");
		Index index = new Index("MixedIndex"); index.getColumns().add("ID"); table.getIndexes().add(index);
		String sql = DialectHolder.oracle26aiDialect.createSqlFactoryRegistry().createSql(index, SqlType.CREATE).get(0).getSqlText();
		assertTrue(sql.contains("\"MixedOwner\".\"MixedIndex\" ON \"MixedOwner\".T"), sql);
	}
}
