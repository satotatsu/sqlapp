/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.spanner.metadata;

import static org.junit.jupiter.api.Assertions.*;
import java.sql.Connection;
import java.util.*;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.spanner.DialectHolder;
import com.sqlapp.data.db.dialect.spanner.util.SpannerSqlBuilder;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.*;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.*;
import com.sqlapp.jdbc.sql.node.SqlNode;

class SpannerColumnReaderTest {
	private class Reader extends SpannerColumnReader {
		final List<Map<String, String>> rows = new ArrayList<>();
		int queries;

		Reader() {
			super(DialectHolder.defaultDialect);
		}

		@Override
		protected JdbcQueryHandler execute(Connection c, SqlNode n, ParametersContext p, ResultSetNextHandler h) {
			queries++;
			try {
				for (var values : rows)
					h.handleResultSetNext(new ExResultSet(null) {
						private String last;

						@Override
						public String getString(String name) {
							return values.get(name);
						}

						@Override
						public Object getObject(String name) {
							return values.get(name);
						}

						@Override
						public long getLong(String name) {
							last = values.get(name);
							return last == null ? 0 : Long.parseLong(last);
						}

						@Override
						public boolean wasNull() {
							return last == null;
						}
					});
			} catch (java.sql.SQLException exception) {
				throw new IllegalStateException(exception);
			}
			return null;
		}
	}

	private Map<String, String> row(String schema, String name) {
		return new HashMap<>(Map.of("table_catalog", "", "table_schema", schema, "table_name", "Orders", "column_name",
				name, "spanner_type", "INT64", "is_nullable", "NO", "IS_GENERATED", "NEVER"));
	}

	@Test
	void readsFortyColumnsInOneQueryAndRetainsDefaultsAndGeneratedExpressionsThroughXml() throws Exception {
		final var reader = new Reader();
		for (String schema : List.of("a", "b"))
			for (int i = 0; i < 20; i++) {
				final var values = row(schema, "c" + i);
				if (i % 2 == 0)
					values.put("column_default", "1 +\n 2");
				else {
					values.put("IS_GENERATED", "ALWAYS");
					values.put("GENERATION_EXPRESSION", "c0 + -- keep this boundary\n 1");
					values.put("IS_STORED", "YES");
				}
				reader.rows.add(values);
			}
		final var columns = reader.doGetAll(null, new ParametersContext(), null);
		assertEquals(40, columns.size());
		assertEquals(1, reader.queries);
		for (String schema : List.of("a", "b")) {
			final var table = new Table("Orders").setSchemaName(schema);
			columns.stream().filter(c -> schema.equals(c.getSchemaName())).forEach(table.getColumns()::add);
			final Table restored = SchemaUtils.readXml(new java.io.StringReader(table.asXml()));
			assertEquals(20, restored.getColumns().size());
			final String sql = DialectHolder.defaultDialect.createSqlFactoryRegistry()
					.createSql(restored, SqlType.CREATE).get(0).getSqlText();
			for (var column : table.getColumns()) {
				final var copy = restored.getColumns().get(column.getName());
				assertEquals(column.getDataType(), copy.getDataType());
				assertTrue(copy.isNotNull());
				assertEquals(column.getDefaultValue(), copy.getDefaultValue());
				assertEquals(column.getFormula(), copy.getFormula());
				assertEquals(column.isFormulaPersisted(), copy.isFormulaPersisted());
				assertTrue(sql.contains(column.getFormula() == null ? column.getDefaultValue() : column.getFormula()),
						sql);
			}
		}
	}

	@Test
	void retainsAnnotatedAndOptionVectorLengthsThroughXmlAndDdl() throws Exception {
		final var reader = new Reader();
		final var annotated = row("app", "embedding");
		annotated.put("spanner_type", "ARRAY<FLOAT32>(vector_length=>3)");
		final var option = row("app", "other_embedding");
		option.put("spanner_type", "ARRAY<FLOAT64>");
		option.put("vector_length", "5");
		reader.rows.addAll(List.of(annotated, option));
		final var table = new Table("Orders");
		reader.doGetAll(null, new ParametersContext(), null).forEach(table.getColumns()::add);
		assertEquals(1, reader.queries);
		final Table restored = SchemaUtils.readXml(new java.io.StringReader(table.asXml()));
		final String sql = DialectHolder.defaultDialect.createSqlFactoryRegistry()
				.createSql(restored, SqlType.CREATE).get(0).getSqlText();
		assertTrue(sql.contains("ARRAY<FLOAT32> (vector_length=>3)"), sql);
		assertTrue(sql.contains("ARRAY<FLOAT64> (vector_length=>5)"), sql);
	}

	@Test
	void rejectsConflictingAndInvalidVectorLengthMetadata() {
		for (String value : List.of("4", "-1", "2147483648")) {
			final var reader = new Reader();
			final var annotated = row("app", "embedding");
			annotated.put("spanner_type", "ARRAY<FLOAT32>(vector_length=>3)");
			annotated.put("vector_length", value);
			reader.rows.add(annotated);
			assertThrows(IllegalArgumentException.class, () -> reader.doGetAll(null, new ParametersContext(), null));
		}
	}

	@Test
	void rejectsMissingNamesTypesNullabilityAndGeneratedExpressions() {
		for (String field : List.of("table_name", "column_name", "spanner_type", "is_nullable",
				"GENERATION_EXPRESSION"))
			for (String value : Arrays.asList(null, "", "  ")) {
				final var reader = new Reader();
				final var values = row("app", "id");
				if (field.equals("GENERATION_EXPRESSION"))
					values.put("IS_GENERATED", "ALWAYS");
				values.put(field, value);
				reader.rows.add(values);
				assertThrows(IllegalArgumentException.class,
						() -> reader.doGetAll(null, new ParametersContext(), null));
			}
		final var reader = new Reader();
		final var values = row("app", "id");
		values.put("is_nullable", "unknown");
		reader.rows.add(values);
		assertThrows(IllegalArgumentException.class, () -> reader.doGetAll(null, new ParametersContext(), null));
	}

	@Test
	void rejectsDuplicateColumnIdentities() {
		final var reader = new Reader();
		reader.rows.add(row("app", "id"));
		reader.rows.add(row("app", "id"));
		assertThrows(IllegalArgumentException.class, () -> reader.doGetAll(null, new ParametersContext(), null));
		assertEquals(1, reader.queries);
	}

	@Test
	void retainsIdentityGenerationAndOptionsThroughXmlAndDdl() throws Exception {
		for (String generation : List.of("BY DEFAULT", "BY_DEFAULT", "ALWAYS")) {
			final var reader = new Reader();
			final var values = row("app", "id");
			values.put("IS_IDENTITY", "YES");
			values.put("IDENTITY_GENERATION", generation);
			values.put("IDENTITY_KIND", "BIT_REVERSED_POSITIVE_SEQUENCE");
			values.put("IDENTITY_START_WITH_COUNTER", "3000");
			values.put("IDENTITY_SKIP_RANGE_MIN", "100");
			values.put("IDENTITY_SKIP_RANGE_MAX", "200");
			reader.rows.add(values);
			final var column = reader.doGetAll(null, new ParametersContext(), null).get(0);
			assertTrue(column.isIdentity());
			assertEquals(generation.equals("ALWAYS") ? IdentityGenerationType.Always : IdentityGenerationType.ByDefault,
					column.getIdentityGenerationType());
			final var table = new Table("Orders");
			table.getColumns().add(column);
			final Table restored = SchemaUtils.readXml(new java.io.StringReader(table.asXml()));
			assertEquals(column.getIdentityGenerationType(), restored.getColumns().get(0).getIdentityGenerationType());
			assertEquals(Long.valueOf(3000), restored.getColumns().get(0).getIdentityStartValue());
			final String sql = DialectHolder.defaultDialect.createSqlFactoryRegistry()
					.createSql(restored, SqlType.CREATE).get(0).getSqlText();
			assertTrue(
					sql.contains(
							"GENERATED " + (generation.equals("ALWAYS") ? "ALWAYS" : "BY DEFAULT") + " AS IDENTITY"),
					sql);
			assertTrue(sql.contains("BIT_REVERSED_POSITIVE"), sql);
			assertTrue(sql.contains("START COUNTER WITH 3000"), sql);
			assertTrue(sql.contains("SKIP RANGE 100, 200"), sql);
			assertEquals(1, reader.queries);
		}
	}

	@Test
	void rejectsUnknownIdentityFlagsGenerationAndKinds() {
		for (String field : List.of("IS_IDENTITY", "IDENTITY_GENERATION", "IDENTITY_KIND"))
			for (String invalid : List.of("unknown", "", "NOT DEFAULT")) {
				final var reader = new Reader();
				final var values = row("app", "id");
				values.put("IS_IDENTITY", "YES");
				values.put(field, invalid);
				reader.rows.add(values);
				assertThrows(IllegalArgumentException.class,
						() -> reader.doGetAll(null, new ParametersContext(), null));
			}
	}

	@Test
	void preservesMissingOptionalIdentityMetadata() {
		final var reader = new Reader();
		reader.rows.add(row("app", "plain"));
		final var values = row("app", "identity");
		values.put("IS_IDENTITY", "YES");
		reader.rows.add(values);
		final var columns = reader.doGetAll(null, new ParametersContext(), null);
		assertFalse(columns.get(0).isIdentity());
		assertTrue(columns.get(1).isIdentity());
		assertNull(columns.get(1).getIdentityGenerationType());
		assertNull(columns.get(1).getSpecifics().get(SpannerSqlBuilder.IDENTITY_BIT_REVERSED_POSITIVE));
	}
}
