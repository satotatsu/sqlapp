/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.oracle.sql;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.oracle.DialectHolder;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Function;
import com.sqlapp.data.schemas.NamedArgument;
import com.sqlapp.data.schemas.Procedure;
import com.sqlapp.jdbc.sql.ParameterDirection;

class OracleRoutineArgumentsTest {

	@Test
	void procedureArgumentsRetainModesDefaultsAndQuotedNames() {
		for (var dialect : java.util.List.of(DialectHolder.defaultDialect, DialectHolder.oracle10gDialect,
				DialectHolder.oracle26aiDialect)) {
			Procedure procedure = new Procedure("P");
			procedure.setStatement("IS BEGIN NULL; END;");
			procedure.getArguments().add(new NamedArgument("input value").setDataType(DataType.VARCHAR).setLength(20)
					.setDefaultValue("'a''b'"));
			NamedArgument output = new NamedArgument("RESULT").setDataType(DataType.INT);
			output.setDirection(ParameterDirection.Output);
			procedure.getArguments().add(output);
			NamedArgument inout = new NamedArgument("STATE").setDataType(DataType.INT);
			inout.setDirection(ParameterDirection.Inout);
			procedure.getArguments().add(inout);
			var registry = dialect.createSqlFactoryRegistry();
			String sql = registry.createSql(procedure, SqlType.CREATE).get(0).getSqlText();
			assertTrue(sql.contains("\"input value\" VARCHAR2 DEFAULT 'a''b'"), sql);
			assertTrue(sql.contains("RESULT OUT NUMBER"), sql);
			assertTrue(sql.contains("STATE IN OUT NUMBER"), sql);
			assertFalse(sql.contains("INOUT"), sql);
			assertFalse(sql.contains("VARCHAR2("), sql);
		}
	}

	@Test
	void functionArgumentsPreserveDefaultExpressionsAndUnconstrainedReturnType() {
		Function function = new Function("F");
		function.setStatement("IS BEGIN RETURN N; END;");
		function.getReturning().setDataType(DataType.INT);
		function.getArguments().add(new NamedArgument("N").setDataType(DataType.INT).setDefaultValue("1 + 2"));
		var registry = DialectHolder.oracle26aiDialect.createSqlFactoryRegistry();
		String sql = registry.createSql(function, SqlType.CREATE).get(0).getSqlText();
		assertTrue(sql.contains("N NUMBER DEFAULT 1 + 2"), sql);
		assertTrue(sql.contains("RETURN NUMBER"), sql);
	}

	@Test
	void outputDefaultsAndMissingNamesOrTypesFailClearly() {
		var builder = new com.sqlapp.data.db.dialect.oracle.util.OracleSqlBuilder(DialectHolder.defaultDialect);
		NamedArgument argument = new NamedArgument("P").setDataType(DataType.INT).setDefaultValue("1");
		for (var direction : java.util.List.of(ParameterDirection.Output, ParameterDirection.Inout)) {
			argument.setDirection(direction);
			assertThrows(IllegalArgumentException.class, () -> builder.argument(argument));
		}
		assertThrows(IllegalArgumentException.class, () -> builder.argument(new NamedArgument()));
		assertThrows(IllegalArgumentException.class,
				() -> builder.argument(new NamedArgument("P").setDataType(DataType.OTHER)));
	}
}
