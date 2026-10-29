/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.informix.metadata;

import static org.junit.jupiter.api.Assertions.*;

import java.io.StringReader;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.dialect.informix.DialectHolder;
import com.sqlapp.data.db.metadata.ReaderOptions;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.Function;
import com.sqlapp.data.schemas.Procedure;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.SchemaUtils;
import com.sqlapp.data.schemas.View;
import com.sqlapp.jdbc.sql.JdbcQueryHandler;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;

class InformixSqlTextReaderTest {
	private static class Rows {
		final List<Map<String, Object>> values = new ArrayList<>();
		int queries;

		JdbcQueryHandler feed(ResultSetNextHandler handler) {
			queries++;
			try {
				for (var value : values)
					handler.handleResultSetNext(InformixMetadataReaderTest.row(value));
			} catch (SQLException e) {
				throw new RuntimeException(e);
			}
			return null;
		}

		InformixViewReader views() {
			final var reader = new InformixViewReader(DialectHolder.defaultDialect) {
				@Override
				protected JdbcQueryHandler execute(Connection c, SqlNode n, ParametersContext p,
						ResultSetNextHandler h) {
					return feed(h);
				}
			};
			reader.setReaderOptions(new ReaderOptions());
			return reader;
		}

		InformixFunctionReader functions() {
			final var reader = new InformixFunctionReader(DialectHolder.defaultDialect) {
				@Override
				protected JdbcQueryHandler execute(Connection c, SqlNode n, ParametersContext p,
						ResultSetNextHandler h) {
					return feed(h);
				}
			};
			reader.setReaderOptions(new ReaderOptions());
			return reader;
		}

		InformixProcedureReader procedures() {
			final var reader = new InformixProcedureReader(DialectHolder.defaultDialect) {
				@Override
				protected JdbcQueryHandler execute(Connection c, SqlNode n, ParametersContext p,
						ResultSetNextHandler h) {
					return feed(h);
				}
			};
			reader.setReaderOptions(new ReaderOptions());
			return reader;
		}

		void add(int id, String owner, String text) {
			final Map<String, Object> row = new HashMap<>();
			row.put("routine_id", id);
			row.put("catalog_name", "db");
			row.put("schema_name", owner);
			row.put("table_name", "sample");
			row.put("routine_name", "sample");
			row.put("specific_name", "sample_" + id);
			row.put("routine_definition", text);
			row.put("routine_text_kind", "T");
			row.put("view_definition", text);
			values.add(row);
		}
	}

	@Test
	void preservesViewChunkSpacesAndOwnerIdentityInOneQueryThroughXml() throws Exception {
		final Rows rows = new Rows();
		for (int i = 0; i < 20; i++) {
			rows.add(i, "owner" + i, "CREATE VIEW sample(label) AS SELECT 'words ");
			rows.add(i, "owner" + i, "separated' FROM items;   ");
		}
		final var views = rows.views().doGetAll(null, new ParametersContext(), null);
		assertEquals(1, rows.queries);
		assertEquals(20, views.size());
		for (int i = 0; i < 20; i++) {
			final View view = (View) views.get(i);
			assertEquals("owner" + i, view.getSchemaName());
			final Schema schema = new Schema(view.getSchemaName());
			schema.getViews().add(view);
			final Schema restored = SchemaUtils.readXml(new StringReader(schema.asXml()));
			assertSql(restored.getViews().get(0), "CREATE VIEW sample(label) AS SELECT 'words separated' FROM items;");
		}
	}

	@Test
	void preservesOverloadedRoutineTextAndSpecificIdentityInOneQueryPerKind() throws Exception {
		final Rows functions = new Rows();
		final Rows procedures = new Rows();
		for (int i = 0; i < 20; i++) {
			functions.add(i, "owner", "CREATE FUNCTION sample(p INTEGER) RETURNING LVARCHAR(100); RETURN 'words ");
			functions.add(i, "owner", "separated'; END FUNCTION;   ");
			procedures.add(i, "owner", "CREATE PROCEDURE sample(p INTEGER); INSERT INTO audit VALUES('words ");
			procedures.add(i, "owner", "separated'); END PROCEDURE;   ");
		}
		final var fs = functions.functions().doGetAll(null, new ParametersContext(), null);
		final var ps = procedures.procedures().doGetAll(null, new ParametersContext(), null);
		assertEquals(1, functions.queries);
		assertEquals(1, procedures.queries);
		assertEquals(20, fs.size());
		assertEquals(20, ps.size());
		for (int i = 0; i < 20; i++) {
			assertEquals("sample_" + i, fs.get(i).getSpecificName());
			assertEquals("sample_" + i, ps.get(i).getSpecificName());
			assertEquals("p", fs.get(i).getArguments().get(0).getName());
			final Schema schema = new Schema("owner");
			schema.getFunctions().add(fs.get(i));
			schema.getProcedures().add(ps.get(i));
			final Schema restored = SchemaUtils.readXml(new StringReader(schema.asXml()));
			assertSql(restored.getFunctions().get(0),
					"CREATE FUNCTION sample(p INTEGER) RETURNING LVARCHAR(100); RETURN 'words separated'; END FUNCTION;");
			assertSql(restored.getProcedures().get(0),
					"CREATE PROCEDURE sample(p INTEGER); INSERT INTO audit VALUES('words separated'); END PROCEDURE;");
		}
	}

	@Test
	void retainsAlterHistorySeparatelyInOneQueryAndGeneratesOrderedOperationsThroughXml() throws Exception {
		final Rows rows = new Rows();
		for (int i = 0; i < 20; i++) {
			rows.add(i, "owner", "CREATE FUNCTION sample(p INTEGER) RETURNING INTEGER; RETURN p; END FUNCTION;");
			rows.add(i, "owner", "ALTER FUNCTION sample(INTEGER) WITH (ADD NOT ");
			rows.values.getLast().put("routine_text_kind", "A");
			rows.add(i, "owner", "VARIANT)    ALTER FUNCTION sample(INTEGER) WITH (MODIFY VARIANT)");
			rows.values.getLast().put("routine_text_kind", "A");
		}
		final var functions = rows.functions().doGetAll(null, new ParametersContext(), null);
		assertEquals(1, rows.queries);
		assertEquals(20, functions.size());
		for (var function : functions) {
			assertFalse(String.join("\n", function.getDefinition()).contains("ALTER"));
			final Schema schema = new Schema("owner");
			schema.getFunctions().add(function);
			final Schema restored = SchemaUtils.readXml(new StringReader(schema.asXml()));
			final var operations = DialectHolder.defaultDialect.createSqlFactoryRegistry()
					.createSql(restored.getFunctions().get(0), SqlType.CREATE);
			assertEquals(3, operations.size());
			assertEquals(SqlType.CREATE, operations.get(0).getSqlType());
			assertEquals(SqlType.ALTER, operations.get(1).getSqlType());
			assertEquals("ALTER FUNCTION sample(INTEGER) WITH (ADD NOT VARIANT)", operations.get(1).getSqlText());
			assertEquals("ALTER FUNCTION sample(INTEGER) WITH (MODIFY VARIANT)", operations.get(2).getSqlText());
		}
		final var reader = rows.procedures();
		final var procedure = reader.doGetAll(null, new ParametersContext(), null).get(0);
		assertEquals(3,
				DialectHolder.defaultDialect.createSqlFactoryRegistry().createSql(procedure, SqlType.CREATE).size());
		reader.getReaderOptions().setReadStatement(false);
		assertFalse(reader.doGetAll(null, new ParametersContext(), null).get(0).getSpecifics()
				.containsKey(com.sqlapp.data.db.dialect.informix.Informix.ROUTINE_ALTER));
	}

	@Test
	void identifiesUnnamedOverloadsByTypesAndKeepsLiteralSpecificNames() {
		final Rows rows = new Rows();
		for (String type : List.of("integer", "lvarchar")) {
			rows.add(rows.values.size() + 1, "owner",
					"CREATE FUNCTION sample(p " + type + ") RETURNING INTEGER; RETURN 1; END FUNCTION;");
			final var row = rows.values.getLast();
			row.put("specific_name", null);
			row.put("argument_count", 1);
			row.put("routine_parameter_types", type);
		}
		final var functions = rows.functions().doGetAll(null, new ParametersContext(), null);
		final var procedures = rows.procedures().doGetAll(null, new ParametersContext(), null);
		assertEquals("sample(integer)", functions.get(0).getSpecificName());
		assertEquals("sample(lvarchar)", functions.get(1).getSpecificName());
		assertEquals("sample(integer)", procedures.get(0).getSpecificName());
		assertEquals("sample(lvarchar)", procedures.get(1).getSpecificName());
		final Schema schema = new Schema("owner");
		schema.getFunctions().addAll(functions);
		assertEquals(2, schema.getFunctions().size());
		rows.values.getFirst().put("routine_parameter_types", null);
		assertThrows(IllegalStateException.class, () -> rows.functions().doGetAll(null, new ParametersContext(), null));
		rows.values.getFirst().put("specific_name", "literal.name");
		assertEquals("literal.name",
				rows.functions().doGetAll(null, new ParametersContext(), null).get(0).getSpecificName());
	}

	@Test
	void honorsTextOptionsAndRejectsUnavailableText() {
		final Rows rows = new Rows();
		rows.add(1, "owner", null);
		assertThrows(IllegalStateException.class, () -> rows.views().doGetAll(null, new ParametersContext(), null));
		assertThrows(IllegalStateException.class, () -> rows.functions().doGetAll(null, new ParametersContext(), null));
		assertThrows(IllegalStateException.class,
				() -> rows.procedures().doGetAll(null, new ParametersContext(), null));
		rows.values.clear();
		rows.add(1, "owner", "CREATE FUNCTION sample() RETURNING INTEGER; RETURN 1; END FUNCTION;");
		final var functionReader = rows.functions();
		functionReader.getReaderOptions().setReadDefinition(true);
		functionReader.getReaderOptions().setReadStatement(false);
		assertFalse(functionReader.doGetAll(null, new ParametersContext(), null).get(0).getDefinition().isEmpty());
		functionReader.getReaderOptions().setReadDefinition(false);
		assertTrue(functionReader.doGetAll(null, new ParametersContext(), null).get(0).getDefinition().isEmpty());
		final var procedureReader = rows.procedures();
		procedureReader.getReaderOptions().setReadStatement(false);
		assertTrue(procedureReader.doGetAll(null, new ParametersContext(), null).get(0).getDefinition().isEmpty());
		final var viewReader = rows.views();
		viewReader.getReaderOptions().setReadStatement(false);
		assertTrue(viewReader.doGetAll(null, new ParametersContext(), null).get(0).getDefinition().isEmpty());
		assertThrows(IllegalArgumentException.class, () -> DialectHolder.defaultDialect.createSqlFactoryRegistry()
				.createSql(new Function("f"), SqlType.CREATE));
		assertThrows(IllegalArgumentException.class, () -> DialectHolder.defaultDialect.createSqlFactoryRegistry()
				.createSql(new Procedure("p"), SqlType.CREATE));
	}

	private void assertSql(Object object, String expected) {
		final var operations = DialectHolder.defaultDialect.createSqlFactoryRegistry().createSql(object,
				SqlType.CREATE);
		assertEquals(1, operations.size());
		assertEquals(expected, operations.get(0).getSqlText());
	}
}
