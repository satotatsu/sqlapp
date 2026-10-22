/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.saphana.sql;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.saphana.DialectHolder;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.*;
import com.sqlapp.jdbc.sql.ParameterDirection;

class SapHanaRoutineSqlTest {
	@Test
	void createsTableTypesAfterXmlRoundTrip() throws Exception {
		for (var dialect : List.of(DialectHolder.defaultDialect, DialectHolder.cloudDialect)) {
			Type type = new Type("Table Type").setSchemaName("APP");
			type.getColumns().add(new TypeColumn("A B").setDataType(DataType.INT));
			type.getColumns().add(new TypeColumn("LABEL").setDataType(DataType.NVARCHAR).setLength(20));
			var restored = (Type) SchemaUtils.readXml(new java.io.StringReader(type.asXml()));
			var registry = dialect.createSqlFactoryRegistry();
			registry.getOptions().setDecorateSchemaName(true);
			String sql = registry.createSql(restored, SqlType.CREATE).getFirst().getSqlText().replaceAll("\\s+", " ");
			assertTrue(sql.contains("CREATE TYPE APP.\"Table Type\" AS TABLE ( \"A B\" INTEGER, LABEL NVARCHAR(20))"), sql);
			assertThrows(IllegalArgumentException.class, () -> registry.createSql(new Type("EMPTY"), SqlType.CREATE));
		}
	}

	@Test
	void createsModeledTableFunctionsWithQuotedColumns() throws Exception {
		for (var dialect : List.of(DialectHolder.defaultDialect, DialectHolder.cloudDialect)) {
			Function function = new Function("TABLE_FN").setSchemaName("Odd Schema");
			function.getArguments().add(new NamedArgument("Input Rows").setDataType(DataType.OTHER)
					.setDataTypeName("TABLE (\"A B\" INTEGER)"));
			function.getReturning().toTable().getTable().getColumns().add("A B", c -> c.setDataType(DataType.INT));
			function.setStatement("BEGIN RETURN SELECT * FROM :\"Input Rows\"; END");
			var registry = dialect.createSqlFactoryRegistry();
			registry.getOptions().setDecorateSchemaName(true);
			String sql = registry.createSql(function, SqlType.CREATE).getFirst().getSqlText().replaceAll("\\s+", " ");
			assertTrue(sql.contains("FUNCTION \"Odd Schema\".TABLE_FN(IN \"Input Rows\" TABLE(\"A B\" INTEGER))"), sql);
			assertTrue(sql.contains("RETURNS TABLE ( \"A B\" INTEGER)"), sql);
			assertTrue(sql.contains("LANGUAGE SQLSCRIPT AS BEGIN RETURN"), sql);
			var restored = (Function) SchemaUtils.readXml(new java.io.StringReader(function.asXml()));
			assertEquals(sql, registry.createSql(restored, SqlType.CREATE).getFirst().getSqlText().replaceAll("\\s+", " "));
		}
	}

	@Test
	void emitsDirectionsOnceAndKeepsScalarOutputsOutOfInputList() {
		for (var dialect : List.of(DialectHolder.defaultDialect, DialectHolder.cloudDialect)) {
			Procedure procedure = new Procedure("P");
			procedure.getArguments().add(new NamedArgument("Input Value").setDataType(DataType.INT).setDefaultValue("1"));
			procedure.getArguments().add(new NamedArgument("RESULT").setDataType(DataType.NVARCHAR).setLength(20)
					.setDirection(ParameterDirection.Output));
			procedure.setStatement("BEGIN RESULT = 'ok'; END");
			String sql = dialect.createSqlFactoryRegistry().createSql(procedure, SqlType.CREATE).getFirst().getSqlText();
			assertTrue(sql.contains("IN \"Input Value\" INTEGER DEFAULT 1"), sql);
			assertTrue(sql.contains("OUT RESULT NVARCHAR(20)"), sql);
			assertFalse(sql.contains("NVARCHAR(20) OUT"), sql);
			Function function = new Function("F");
			function.getArguments().add(new NamedArgument("N").setDataType(DataType.INT));
			function.getArguments().add(new NamedArgument("R").setDataType(DataType.INT).setDirection(ParameterDirection.Output));
			function.setStatement("BEGIN R = :N + 1; END");
			sql = dialect.createSqlFactoryRegistry().createSql(function, SqlType.CREATE).getFirst().getSqlText().replaceAll("\\s+", " ");
			assertTrue(sql.contains("F(IN N INTEGER) RETURNS R INTEGER LANGUAGE SQLSCRIPT AS"), sql);
		}
	}

	@Test
	void supportsEmptyArgumentsAndRejectsMissingReturnOrBody() {
		var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
		Procedure procedure = new Procedure("P").setStatement("BEGIN END");
		assertTrue(registry.createSql(procedure, SqlType.CREATE).getFirst().getSqlText().contains("P()"));
		Function function = new Function("F").setStatement("BEGIN RETURN 1; END");
		assertThrows(IllegalArgumentException.class, () -> registry.createSql(function, SqlType.CREATE));
		function.getReturning().setDataType(DataType.INT);
		function.setStatement((String) null);
		assertThrows(IllegalArgumentException.class, () -> registry.createSql(function, SqlType.CREATE));
	}
}
