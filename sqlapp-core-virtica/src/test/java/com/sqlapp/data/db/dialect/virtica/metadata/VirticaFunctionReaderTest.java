/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-virtica.
 */
package com.sqlapp.data.db.dialect.virtica.metadata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.*;

import java.sql.SQLException;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.dialect.virtica.DialectHolder;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.schemas.NamedArgument;

class VirticaFunctionReaderTest {

	private final VirticaFunctionReader reader = new VirticaFunctionReader(DialectHolder.defaultDialect);

	private com.sqlapp.jdbc.ExResultSet catalogRow(String kind, String arguments, String returns) {
		return new com.sqlapp.jdbc.ExResultSet(null) {
			@Override
			public String getString(String name) {
				return switch (name.toUpperCase(java.util.Locale.ROOT)) {
				case "FUNCTION_NAME" -> "shared_name";
				case "SCHEMA_NAME" -> "app";
				case "PROCEDURE_TYPE" -> kind;
				case "FUNCTION_ARGUMENT_TYPE" -> arguments;
				case "FUNCTION_RETURN_TYPE" -> returns;
				default -> null;
				};
			}

			@Override
			public Object getObject(String name) {
				return null;
			}
		};
	}

	@Test
	void bulkReadKeepsSameSignatureAcrossKindsWithOneQuery() throws Exception {
		int[] queries = { 0 };
		var bulkReader = new VirticaFunctionReader(DialectHolder.defaultDialect) {
			@Override
			protected com.sqlapp.jdbc.sql.JdbcQueryHandler execute(java.sql.Connection connection,
					com.sqlapp.jdbc.sql.node.SqlNode node, com.sqlapp.data.parameter.ParametersContext context,
					com.sqlapp.jdbc.sql.ResultSetNextHandler handler) {
				queries[0]++;
				try {
					for (String kind : List.of("User Defined Function", "User Defined Transform",
							"User Defined Aggregate", "User Defined Analytic"))
						handler.handleResultSetNext(catalogRow(kind, "Integer", "Integer"));
				} catch (SQLException exception) {
					throw new IllegalStateException(exception);
				}
				return null;
			}
		};
		var functions = bulkReader.doGetAll(null, new com.sqlapp.data.parameter.ParametersContext(), null);
		assertEquals(1, queries[0]);
		assertEquals(4, functions.size());
		assertEquals(4L, functions.stream().map(f -> f.getSpecificName()).distinct().count());
		assertTrue(functions.stream().anyMatch(f -> "shared_name(Integer)".equals(f.getSpecificName())));
		assertTrue(functions.stream().allMatch(f -> f.getArguments().get(0).getDataType() == DataType.BIGINT));
		var schema = new com.sqlapp.data.schemas.Schema("app");
		schema.getFunctions().addAll(functions);
		com.sqlapp.data.schemas.Schema restored = com.sqlapp.data.schemas.SchemaUtils
				.readXml(new java.io.StringReader(schema.asXml()));
		assertEquals(4, restored.getFunctions().size());
		assertEquals(4L, restored.getFunctions().stream().map(f -> f.getSpecificName()).distinct().count());
	}

	@Test
	void transformRetainsRawMultipleReturnsAndAnyThroughXml() throws Exception {
		var function = reader.createFunction(catalogRow("User Defined Transform", "Any", "Integer, Varchar(80)"));
		com.sqlapp.data.schemas.Function restored = com.sqlapp.data.schemas.SchemaUtils
				.readXml(new java.io.StringReader(function.asXml()));
		assertEquals("Any", restored.getSpecifics().get("FUNCTION_ARGUMENT_TYPE"));
		assertEquals("Integer, Varchar(80)", restored.getSpecifics().get("FUNCTION_RETURN_TYPE"));
		assertNull(restored.getReturning().getDataType());
		assertNull(restored.getReturning().getDataTypeName());
		assertEquals(1, restored.getArguments().size());
		assertEquals(function.getArguments().get(0).getName(), restored.getArguments().get(0).getName());
		var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
		String drop = registry.createSql(restored, com.sqlapp.data.db.sql.SqlType.DROP).get(0).getSqlText();
		assertTrue(drop.contains("TRANSFORM FUNCTION"), drop);
		assertTrue(drop.contains("\"shared_name\"()"), drop);
		assertThrows(IllegalArgumentException.class,
				() -> registry.createSql(restored, com.sqlapp.data.db.sql.SqlType.CREATE));
	}

	@Test
	void loadFunctionRetainsKindWithNullSignatureThroughXml() throws Exception {
		var function = reader.createFunction(new com.sqlapp.jdbc.ExResultSet(null) {
			@Override
			public String getString(String name) {
				return switch (name.toUpperCase(java.util.Locale.ROOT)) {
				case "FUNCTION_NAME" -> "parser";
				case "SCHEMA_NAME" -> "app";
				case "PROCEDURE_TYPE" -> "User Defined Parser";
				default -> null;
				};
			}

			@Override
			public Object getObject(String name) {
				return null;
			}
		});
		com.sqlapp.data.schemas.Function restored = com.sqlapp.data.schemas.SchemaUtils
				.readXml(new java.io.StringReader(function.asXml()));
		assertEquals("User Defined Parser", restored.getSpecifics().get("PROCEDURE_TYPE"));
		assertTrue(restored.getSpecificName().contains("User Defined Parser"));
		assertTrue(restored.getArguments().isEmpty());
		assertNull(restored.getReturning().getDataType());
	}

	@Test
	void parsesMultiCharacterArgumentName() throws SQLException {
		NamedArgument argument = reader.createNamedArgument("input_value Integer");

		assertEquals("input_value", argument.getName());
		assertEquals(DataType.BIGINT, argument.getDataType());
	}

	@Test
	void parsesUnnamedUdxArgumentType() throws SQLException {
		NamedArgument argument = reader.createNamedArgument("Numeric");

		assertNull(argument.getName());
		assertEquals(DataType.NUMERIC, argument.getDataType());
	}

	@Test
	void unnamedMultiwordTypesAreNotMistakenForArgumentNames() throws Exception {
		for (String type : List.of("DOUBLE PRECISION", "LONG VARCHAR(100000)", "LONG VARBINARY(100000)", "TIMESTAMP(6)",
				"TIMESTAMP WITH TIME ZONE", "TIME WITH TIME ZONE")) {
			NamedArgument unnamed = reader.createNamedArgument(type);
			assertNull(unnamed.getName(), type);
			assertNotEquals(DataType.OTHER, unnamed.getDataType(), type);
			NamedArgument named = reader.createNamedArgument("input_value " + type);
			assertEquals("input_value", named.getName(), type);
			assertEquals(unnamed.getDataType(), named.getDataType(), type);
		}
		assertEquals(100000L, reader.createNamedArgument("LONG VARBINARY(100000)").getLength());
	}

	@Test
	void splitsArgumentsWithoutSplittingTypePrecision() {
		assertEquals(List.of("amount Numeric(10,2)", "label Varchar(100)"),
				reader.splitArguments("amount Numeric(10,2), label Varchar(100)"));
	}

	@Test
	void quotedArgumentNamesAndMalformedListsAreHandled() throws Exception {
		assertEquals(List.of("\"order,value\" Numeric(12,2)", "label Varchar(30)"),
				reader.splitArguments("\"order,value\" Numeric(12,2), label Varchar(30)"));
		assertEquals("order value", reader.createNamedArgument("\"order value\" Integer").getName());
		assertEquals("label\"x", reader.createNamedArgument("\"label\"\"x\" Varchar(30)").getName());
		for (String invalid : List.of("a Numeric(12,2", "a Integer)", "\"a Integer", "a Integer,", ",a Integer"))
			assertThrows(IllegalArgumentException.class, () -> reader.splitArguments(invalid));
	}

	@Test
	void catalogReturnBodyIsRecreatedWithArgumentsThroughXml() throws Exception {
		var function = reader.createFunction(new com.sqlapp.jdbc.ExResultSet(null) {
			@Override
			public String getString(String name) {
				return switch (name.toUpperCase(java.util.Locale.ROOT)) {
				case "FUNCTION_NAME" -> "add_one";
				case "SCHEMA_NAME" -> "app";
				case "FUNCTION_DEFINITION" -> "RETURN \"order value\" + 1";
				case "FUNCTION_RETURN_TYPE" -> "Numeric(12,2)";
				case "FUNCTION_ARGUMENT_TYPE" -> "\"order value\" Numeric(12,2)";
				default -> null;
				};
			}

			@Override
			public Object getObject(String name) {
				return null;
			}
		});
		assertTrue(function.getDefinition() == null || function.getDefinition().isEmpty());
		com.sqlapp.data.schemas.Function restored = com.sqlapp.data.schemas.SchemaUtils
				.readXml(new java.io.StringReader(function.asXml()));
		assertEquals("order value", restored.getArguments().get(0).getName());
		String sql = DialectHolder.defaultDialect.createSqlFactoryRegistry()
				.createSql(restored, com.sqlapp.data.db.sql.SqlType.CREATE).get(0).getSqlText();
		assertTrue(sql.contains("CREATE FUNCTION"), sql);
		assertTrue(sql.contains("\"order value\""), sql);
		assertTrue(sql.contains("AS BEGIN"), sql);
		assertTrue(sql.contains("RETURN \"order value\" + 1;"), sql);
		assertTrue(sql.trim().endsWith("END;"), sql);
	}

	@Test
	void acceptsNoArguments() {
		assertEquals(List.of(), reader.splitArguments(null));
		assertEquals(List.of(), reader.splitArguments("  "));
		assertEquals("zero_args()", reader.createSpecificName("zero_args", null));
	}

	@Test
	void createsOverloadSpecificNameFromArguments() {
		assertEquals("calculate(amount Numeric(10,2), label Varchar(100))",
				reader.createSpecificName("calculate", "amount Numeric(10,2), label Varchar(100)"));
	}
}
