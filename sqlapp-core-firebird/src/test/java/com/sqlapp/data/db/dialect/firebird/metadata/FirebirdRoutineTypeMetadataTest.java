/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.firebird.metadata;

import static org.junit.jupiter.api.Assertions.*;
import java.sql.SQLException;
import java.util.*;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.firebird.*;
import com.sqlapp.data.schemas.*;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.SqlParameterCollection;

class FirebirdRoutineTypeMetadataTest {
	private ExResultSet row(Map<String, Object> values) {
		return new ExResultSet(null) {
			@Override
			public boolean contains(String key) {
				return values.containsKey(key);
			}

			@Override
			public String getString(String key) {
				return values.get(key) == null ? null : values.get(key).toString();
			}

			@Override
			public int getInt(String key) {
				return values.get(key) instanceof Number number ? number.intValue() : 0;
			}

			@Override
			public short getShort(String key) {
				return (short) getInt(key);
			}
		};
	}

	@Test
	void inheritedDomainAttributesDoNotBecomeLocalDeclarations() throws Exception {
		var argument = new NamedArgument("A").setDefaultValue("7").setNotNull(true).setCollation("inherited");
		FirebirdRoutineTypeMetadata.apply(argument,
				row(Map.of("TYPE_DOMAIN", "positive domain  ", "TYPE_MECHANISM", 0)));
		assertEquals(FirebirdRoutineTypeReference.domain("positive domain"),
				FirebirdRoutineTypeReference.get(argument.getSpecifics()));
		assertNull(argument.getDefaultValue());
		assertFalse(argument.isNotNull());
		assertNull(argument.getCollation());
		var local = row(Map.of("TYPE_DOMAIN", "positive domain", "TYPE_MECHANISM", 1, "LOCAL_DEFAULT_SOURCE",
				"DEFAULT -1", "LOCAL_NULL_FLAG", 1, "LOCAL_COLLATION_NAME", "explicit"));
		FirebirdRoutineTypeMetadata.apply(argument, local);
		assertEquals(FirebirdRoutineTypeReference.typeOfDomain("positive domain"),
				FirebirdRoutineTypeReference.get(argument.getSpecifics()));
		assertEquals("-1", argument.getDefaultValue());
		assertTrue(argument.isNotNull());
		assertEquals("explicit", argument.getCollation());
	}

	@Test
	void implicitScalarTypesRemainScalarButColumnReferencesRetainIdentity() throws Exception {
		var argument = new NamedArgument("A").setDefaultValue("7").setNotNull(true);
		FirebirdRoutineTypeMetadata.apply(argument, row(Map.of("TYPE_DOMAIN", "RDB$123", "TYPE_MECHANISM", 0)));
		assertNull(FirebirdRoutineTypeReference.get(argument.getSpecifics()));
		assertEquals("7", argument.getDefaultValue());
		FirebirdRoutineTypeMetadata.apply(argument, row(Map.of("TYPE_DOMAIN", "D", "TYPE_MECHANISM", 1, "TYPE_RELATION",
				"quoted table", "TYPE_COLUMN", "quoted column")));
		assertEquals(FirebirdRoutineTypeReference.typeOfColumn("quoted table", "quoted column"),
				FirebirdRoutineTypeReference.get(argument.getSpecifics()));
		assertThrows(SQLException.class,
				() -> FirebirdRoutineTypeMetadata.apply(argument, row(Map.of("TYPE_MECHANISM", 4))));
		assertThrows(SQLException.class,
				() -> FirebirdRoutineTypeMetadata.apply(argument, row(Map.of("TYPE_MECHANISM", 1))));
	}

	@Test
	void olderQueriesNeverAccessColumnReferenceCatalogFields() {
		var reader = new FirebirdProcedureArgumentReader(DialectHolder.defaultDialect30);
		for (int[] version : new int[][] { { 2, 0 }, { 2, 1 }, { 2, 5 }, { 3, 0 }, { 4, 0 }, { 5, 0 } }) {
			var sql = new SqlParameterCollection();
			reader.getSqlSqlNode(new ProductVersionInfo().setMajorVersion(version[0]).setMinorVersion(version[1]))
					.eval(new ParametersContext(), sql);
			assertEquals(version[0] > 2 || version[1] >= 5, sql.getSql().contains("AS TYPE_RELATION"), sql.getSql());
		}
	}

	@Test
	void anExplicitCollationIsNotClearedByASecondEquivalentAssignment() throws Exception {
		var values = new HashMap<String, Object>();
		values.put("TYPE_DOMAIN", "TEXT_D");
		values.put("TYPE_MECHANISM", 1);
		values.put("ROUTINE_NAME", "P");
		values.put("PARAMETER_NAME", "A");
		values.put("RDB$ARGUMENT_NAME", "A");
		values.put("FIELD_TYPE", 37);
		values.put("CHAR_LEN", 10);
		values.put("CHARACTER_SET_NAME", "UTF8");
		values.put("COLLATION_NAME", "UNICODE_CI");
		values.put("LOCAL_COLLATION_NAME", "UNICODE_CI");
		var procedure = new FirebirdProcedureArgumentReader(DialectHolder.defaultDialect25)
				.createNamedArgument(row(values));
		var function = new Firebird30FunctionArgumentReader(DialectHolder.defaultDialect30)
				.createNamedArgument(row(values));
		assertEquals("UNICODE_CI", procedure.getCollation());
		assertEquals("UNICODE_CI", function.getCollation());
	}

	@Test
	void procedureAndFunctionReadersAttachReferencesFromTheirExistingRows() throws Exception {
		var values = new HashMap<String, Object>();
		values.put("TYPE_DOMAIN", "D");
		values.put("TYPE_MECHANISM", 0);
		values.put("ROUTINE_NAME", "P");
		values.put("PARAMETER_NAME", "A");
		values.put("FIELD_TYPE", 8);
		values.put("RDB$ARGUMENT_NAME", "A");
		var procedure = new FirebirdProcedureArgumentReader(DialectHolder.defaultDialect25)
				.createNamedArgument(row(values));
		var function = new Firebird30FunctionArgumentReader(DialectHolder.defaultDialect30)
				.createNamedArgument(row(values));
		assertEquals(FirebirdRoutineTypeReference.domain("D"),
				FirebirdRoutineTypeReference.get(procedure.getSpecifics()));
		assertEquals(FirebirdRoutineTypeReference.domain("D"),
				FirebirdRoutineTypeReference.get(function.getSpecifics()));
	}
}
