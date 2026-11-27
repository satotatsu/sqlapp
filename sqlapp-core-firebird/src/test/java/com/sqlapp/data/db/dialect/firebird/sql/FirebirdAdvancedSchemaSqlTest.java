/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.firebird.sql;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.firebird.DialectHolder;
import com.sqlapp.data.db.dialect.firebird.util.FirebirdSqlBuilder;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.*;

class FirebirdAdvancedSchemaSqlTest {
	@Test
	void putsConstraintIndexDirectionOutsideTheColumnList() {
		Table table = new Table("keyed");
		table.getColumns().add(new Column("a").setDataType(DataType.INT));
		table.getColumns().add(new Column("b").setDataType(DataType.INT));
		UniqueConstraint constraint = new UniqueConstraint("pk_keyed", true);
		constraint.getColumns().add(table.getColumns().get("a"), Order.Desc);
		constraint.getColumns().add(table.getColumns().get("b"), Order.Desc);
		constraint.getSpecifics().put("firebird.indexName", "ix_keyed");
		table.getConstraints().add(constraint);
		String ddl = sql(DialectHolder.defaultDialect25, table, SqlType.CREATE);
		assertTrue(ddl.contains("USING DESCENDING INDEX"), ddl);
		assertFalse(ddl.contains("a DESC"), ddl);
		constraint.getColumns().get(1).setOrder(Order.Asc);
		assertThrows(IllegalArgumentException.class, () -> sql(DialectHolder.defaultDialect25, table, SqlType.CREATE));
		constraint.getColumns().get(1).setOrder(Order.Desc);
		constraint.setDeferrability(Deferrability.InitiallyDeferred);
		assertThrows(IllegalArgumentException.class, () -> sql(DialectHolder.defaultDialect25, table, SqlType.CREATE));
	}

	@Test
	void altersRoutineSignaturesAndRejectsUnrenderedArgumentChanges() {
		Procedure original = new Procedure("signature_change");
		original.getArguments().add(new NamedArgument("value").setDataType(DataType.INT));
		original.setStatement("BEGIN END");
		Procedure target = original.clone();
		target.getArguments().get("value").setDataType(DataType.BIGINT).setDefaultValue("7");
		target.getArguments().add(new NamedArgument("extra").setDataType(DataType.BIGINT).setDefaultValue("2"));
		var registry = DialectHolder.defaultDialect40.createSqlFactoryRegistry();
		String ddl = registry.createSql(original.diff(target), SqlType.ALTER).get(0).getSqlText();
		assertTrue(ddl.startsWith("ALTER PROCEDURE"), ddl);
		assertTrue(ddl.contains("BIGINT DEFAULT 7"), ddl);
		assertTrue(ddl.contains("BIGINT DEFAULT 2"), ddl);
		target.getArguments().get("value").setArrayDimension(1);
		assertThrows(UnsupportedOperationException.class,
				() -> registry.createSql(original.diff(target), SqlType.ALTER));
	}

	@Test
	void altersFunctionReturnTypeAndValidatesParameterDirection() {
		Function original = new Function("signature_function");
		original.getReturning().setDataType(DataType.INT);
		original.setStatement("BEGIN RETURN 1; END");
		Function target = original.clone();
		target.getReturning().setDataType(DataType.BIGINT);
		target.getReturning().getSpecifics().put("firebird.notNull", "true");
		var registry = DialectHolder.defaultDialect40.createSqlFactoryRegistry();
		String ddl = registry.createSql(original.diff(target), SqlType.ALTER).get(0).getSqlText();
		assertTrue(ddl.contains("RETURNS BIGINT NOT NULL"), ddl);
		target.getArguments().add(new NamedArgument("out_value").setDataType(DataType.INT)
				.setDirection(com.sqlapp.jdbc.sql.ParameterDirection.Output));
		assertThrows(IllegalArgumentException.class, () -> registry.createSql(original.diff(target), SqlType.ALTER));
	}

	@Test
	void recreatesTemporaryTablesThroughTheNormalCreateEntryPoint() {
		Table table = new Table("session_values").setTableType(Table.TableType.Temporary);
		table.getColumns().add(new Column("id").setDataType(DataType.INT));
		table.getSpecifics().put("firebird.onCommit", "DELETE ROWS");
		String ddl = sql(DialectHolder.defaultDialect25, table, SqlType.CREATE);
		assertTrue(ddl.startsWith("CREATE GLOBAL TEMPORARY TABLE"), ddl);
		assertTrue(ddl.contains("ON COMMIT DELETE ROWS"), ddl);
		assertTrue(
				sql(DialectHolder.defaultDialect25, table, SqlType.CREATE_TEMPORARY).contains("ON COMMIT DELETE ROWS"));
		table.getSpecifics().put("firebird.sqlSecurity", "INVOKER");
		var operations = DialectHolder.defaultDialect40.createSqlFactoryRegistry().createSql(table, SqlType.CREATE);
		assertEquals(2, operations.size());
		assertFalse(operations.get(0).getSqlText().contains("SQL SECURITY"));
		assertTrue(operations.get(1).getSqlText().startsWith("ALTER TABLE"));
		assertTrue(operations.get(1).getSqlText().contains("SQL SECURITY INVOKER"));
		table.getSpecifics().remove("firebird.sqlSecurity");
		assertThrows(IllegalArgumentException.class, () -> sql(DialectHolder.defaultDialect, table, SqlType.CREATE));
		table.getSpecifics().put("firebird.onCommit", "INVALID");
		assertThrows(IllegalArgumentException.class, () -> sql(DialectHolder.defaultDialect25, table, SqlType.CREATE));
		table.setTableType((Table.TableType) null);
		assertThrows(IllegalArgumentException.class, () -> sql(DialectHolder.defaultDialect25, table, SqlType.CREATE));
	}

	@Test
	void createsPackageHeaderAndBodyWithExecutableDefinitions() {
		var header = new com.sqlapp.data.schemas.Package("pkg");
		header.setStatement("BEGIN FUNCTION value_of() RETURNS INTEGER; END");
		header.getSpecifics().put("firebird.sqlSecurity", "DEFINER");
		String ddl = sql(DialectHolder.defaultDialect40, header, SqlType.CREATE);
		assertTrue(ddl.startsWith("CREATE PACKAGE"), ddl);
		assertFalse(ddl.contains("OR ALTER"), ddl);
		assertTrue(ddl.contains("SQL SECURITY DEFINER"), ddl);
		var body = new PackageBody("pkg");
		assertThrows(IllegalArgumentException.class, () -> sql(DialectHolder.defaultDialect30, body, SqlType.CREATE));
		body.setStatement("BEGIN FUNCTION value_of() RETURNS INTEGER AS BEGIN RETURN 7; END END");
		assertTrue(sql(DialectHolder.defaultDialect30, body, SqlType.CREATE).startsWith("CREATE PACKAGE BODY"));
		body.setDefinition("CREATE PACKAGE BODY pkg AS BEGIN END");
		assertEquals("CREATE PACKAGE BODY pkg AS BEGIN END", sql(DialectHolder.defaultDialect30, body, SqlType.CREATE));
	}

	@Test
	void retainsFunctionReturnCharacterSetCollationAndNullability() {
		Function function = new Function("text_result");
		function.getReturning().setDataType(DataType.VARCHAR).setLength(12).setCharacterSet("UTF8")
				.setCollation("UNICODE");
		function.getReturning().getSpecifics().put("firebird.notNull", "true");
		function.setStatement("BEGIN RETURN 'hello'; END");
		String ddl = sql(DialectHolder.defaultDialect30, function, SqlType.CREATE);
		assertTrue(ddl.contains("RETURNS VARCHAR(12) CHARACTER SET UTF8 NOT NULL COLLATE UNICODE"), ddl);
	}

	@Test
	void recreatesLegacyExternalFunctionAndRejectsUnknownMechanisms() {
		Function function = new Function("legacy_abs");
		function.getSpecifics().put("firebird.udf.module", "ib_udf");
		function.getSpecifics().put("firebird.udf.entrypoint", "IB_UDF_abs");
		function.getReturning().setDataType(DataType.DOUBLE);
		NamedArgument argument = new NamedArgument("value").setDataType(DataType.DOUBLE);
		function.getArguments().add(argument);
		String ddl = sql(DialectHolder.defaultDialect25, function, SqlType.CREATE);
		assertTrue(ddl.startsWith("DECLARE EXTERNAL FUNCTION"), ddl);
		assertTrue(ddl.contains("RETURNS DOUBLE PRECISION BY VALUE"), ddl);
		argument.getSpecifics().put("firebird.udf.mechanism", "99");
		assertThrows(IllegalArgumentException.class,
				() -> sql(DialectHolder.defaultDialect25, function, SqlType.CREATE));
	}

	@Test
	void supportsScalarArrayInputsButRejectsArrayReturnsAndInputFreeIt() {
		Function function = new Function("array_sum");
		function.getSpecifics().put("firebird.udf.module", "array_fixture");
		function.getSpecifics().put("firebird.udf.entrypoint", "array_sum");
		function.getReturning().setDataType(DataType.INT);
		NamedArgument argument = new NamedArgument("values").setDataType(DataType.INT);
		argument.getSpecifics().put("firebird.udf.mechanism", "4");
		function.getArguments().add(argument);
		assertTrue(sql(DialectHolder.defaultDialect25, function, SqlType.CREATE).contains("INTEGER BY SCALAR_ARRAY"));
		argument.getSpecifics().put("firebird.udf.mechanism", "-4");
		assertThrows(IllegalArgumentException.class,
				() -> sql(DialectHolder.defaultDialect25, function, SqlType.CREATE));
		argument.getSpecifics().put("firebird.udf.mechanism", "4");
		for (String mechanism : new String[] { "4", "-4" }) {
			function.getSpecifics().put("firebird.udf.returnMechanism", mechanism);
			assertThrows(IllegalArgumentException.class,
					() -> sql(DialectHolder.defaultDialect25, function, SqlType.CREATE));
		}
		function.getSpecifics().put("firebird.udf.returnPosition", "1");
		assertThrows(IllegalArgumentException.class,
				() -> sql(DialectHolder.defaultDialect25, function, SqlType.CREATE));
	}

	@Test
	void validatesExternalEngineAndVersion() {
		Procedure procedure = new Procedure("external_rows");
		procedure.getSpecifics().put("firebird.external.engine", "UDR");
		assertThrows(IllegalArgumentException.class,
				() -> sql(DialectHolder.defaultDialect30, procedure, SqlType.CREATE));
		procedure.getSpecifics().put("firebird.external.entrypoint", "example!rows");
		String ddl = sql(DialectHolder.defaultDialect30, procedure, SqlType.CREATE);
		assertTrue(ddl.contains("EXTERNAL NAME 'example!rows' ENGINE UDR"), ddl);
		assertThrows(IllegalArgumentException.class,
				() -> sql(DialectHolder.defaultDialect25, procedure, SqlType.CREATE));
	}

	private String sql(Dialect dialect, DbObject<?> obj, SqlType type) {
		return dialect.createSqlFactoryRegistry().createSql(obj, type).get(0).getSqlText();
	}

	@Test
	void preservesExplicitIdentityOptionsAndRejectsUnsupportedVersions() {
		Table table = new Table("identity_options");
		var column = new Column("id").setDataType(DataType.BIGINT).setIdentity(true)
				.setIdentityGenerationType(IdentityGenerationType.Always).setIdentityStartValue(100).setIdentityStep(5);
		table.getColumns().add(column);
		String ddl = sql(DialectHolder.defaultDialect40, table, SqlType.CREATE);
		assertTrue(ddl.contains("GENERATED ALWAYS AS IDENTITY"), ddl);
		assertTrue(ddl.contains("START WITH 100 INCREMENT BY 5"), ddl);
		assertThrows(IllegalArgumentException.class, () -> sql(DialectHolder.defaultDialect30, table, SqlType.CREATE));
		column.setIdentityGenerationType(IdentityGenerationType.ByDefault).setIdentityStep(1);
		assertTrue(sql(DialectHolder.defaultDialect30, table, SqlType.CREATE).contains("START WITH 100"));
		column.setIdentityStep(0);
		assertThrows(IllegalArgumentException.class, () -> sql(DialectHolder.defaultDialect40, table, SqlType.CREATE));
	}

	@Test
	void rendersComputedColumnsAndMultidimensionalArrayBounds() {
		Table table = new Table("computed_arrays");
		table.getColumns().add(new Column("result").setDataType(DataType.INT).setFormula("id * 2"));
		Column array = new Column("matrix").setDataType(DataType.INT).setArrayDimension(2);
		array.getSpecifics().put(FirebirdSqlBuilder.ARRAY_BOUNDS, "-2:-1,3:5");
		table.getColumns().add(array);
		String ddl = sql(DialectHolder.defaultDialect25, table, SqlType.CREATE);
		assertTrue(ddl.contains("COMPUTED BY (id * 2)"), ddl);
		assertTrue(ddl.contains("INTEGER[-2:-1,3:5]"), ddl);
		array.getSpecifics().put(FirebirdSqlBuilder.ARRAY_BOUNDS, "-2:-1,5:3");
		assertThrows(IllegalArgumentException.class, () -> sql(DialectHolder.defaultDialect25, table, SqlType.CREATE));
		array.getSpecifics().remove(FirebirdSqlBuilder.ARRAY_BOUNDS);
		assertThrows(IllegalArgumentException.class, () -> sql(DialectHolder.defaultDialect25, table, SqlType.CREATE));
	}

	@Test
	void altersBodiesInPlaceAndPreservesSecurity() {
		Procedure original = new Procedure("lookup");
		original.setStatement("BEGIN END");
		original.setSqlSecurity(SqlSecurity.Definer);
		Procedure target = original.clone();
		target.setStatement("BEGIN POST_EVENT 'changed'; END");
		var registry = DialectHolder.defaultDialect40.createSqlFactoryRegistry();
		var operations = registry.createSql(original.diff(target), SqlType.ALTER);
		assertEquals(1, operations.size());
		String ddl = operations.get(0).getSqlText();
		assertTrue(ddl.startsWith("ALTER PROCEDURE"), ddl);
		assertTrue(ddl.contains("SQL SECURITY DEFINER"), ddl);
		assertFalse(ddl.contains("DROP"), ddl);
		assertThrows(IllegalArgumentException.class, () -> sql(DialectHolder.defaultDialect30, target, SqlType.CREATE));
		target.setName("renamed");
		assertThrows(UnsupportedOperationException.class,
				() -> registry.createSql(original.diff(target), SqlType.ALTER));
	}

	@Test
	void recreatesAndAltersParameterCommentsWithoutRecompilingTheBody() {
		Procedure original = new Procedure("quoted proc");
		original.getArguments().add(new NamedArgument("quoted arg").setDataType(DataType.INT));
		original.setStatement("BEGIN END");
		Procedure target = original.clone();
		target.getArguments().get(0).setRemarks("it's 日本語");
		var registry = DialectHolder.defaultDialect25.createSqlFactoryRegistry();
		String comment = "COMMENT ON PARAMETER \"quoted proc\".\"quoted arg\" IS 'it''s 日本語'";
		var operations = registry.createSql(original.diff(target), SqlType.ALTER);
		assertEquals(1, operations.size());
		assertEquals(comment, operations.get(0).getSqlText());
		assertEquals(comment, registry.createSql(target, SqlType.CREATE).get(1).getSqlText());
		assertEquals(comment.replace("'it''s 日本語'", "NULL"),
				registry.createSql(target.diff(original), SqlType.ALTER).get(0).getSqlText());
		target.setStatement("BEGIN POST_EVENT 'changed'; END");
		assertEquals(2, registry.createSql(original.diff(target), SqlType.ALTER).size());
		assertThrows(IllegalArgumentException.class,
				() -> DialectHolder.defaultDialect.createSqlFactoryRegistry().createSql(target, SqlType.CREATE));
	}

	@Test
	void recreatesFunctionParameterCommentsAndRetainsThemAfterSignatureAlter() {
		Function original = new Function("calculate");
		original.getReturning().setDataType(DataType.INT);
		original.getArguments().add(new NamedArgument("input").setDataType(DataType.INT).setRemarks("value"));
		original.setStatement("BEGIN RETURN input; END");
		var registry = DialectHolder.defaultDialect30.createSqlFactoryRegistry();
		assertEquals("COMMENT ON FUNCTION PARAMETER \"calculate\".\"input\" IS 'value'",
				registry.createSql(original, SqlType.CREATE).get(1).getSqlText());
		Function target = original.clone();
		target.getArguments().get(0).setDataType(DataType.BIGINT);
		assertEquals(2, registry.createSql(original.diff(target), SqlType.ALTER).size());
		target = original.clone();
		target.getArguments().get(0).setRemarks(null);
		assertEquals(1, registry.createSql(original.diff(target), SqlType.ALTER).size());
		assertTrue(registry.createSql(original.diff(target), SqlType.ALTER).get(0).getSqlText().endsWith(" IS NULL"));
	}

	@Test
	void emitsOnlyACommentForCommentChanges() {
		Function original = new Function("calculate");
		original.getReturning().setDataType(DataType.INT);
		original.setStatement("BEGIN RETURN 1; END");
		Function target = original.clone();
		target.setRemarks("new remark");
		var operations = DialectHolder.defaultDialect40.createSqlFactoryRegistry().createSql(original.diff(target),
				SqlType.ALTER);
		assertEquals(1, operations.size());
		assertTrue(operations.get(0).getSqlText().startsWith("COMMENT ON FUNCTION"));
	}
}
