/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.duckdb;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.SchemaUtils;
import com.sqlapp.data.schemas.UniqueConstraint;
import com.sqlapp.data.schemas.rowiterator.JdbcDynamicRowIteratorHandler;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.bulk.BulkInsertMethod;
import com.sqlapp.jdbc.bulk.BulkInsertResolver;
import com.sqlapp.jdbc.bulk.BulkOption;
import com.sqlapp.jdbc.bulk.BulkUpsertOption;
import com.sqlapp.jdbc.bulk.BulkUpsertResolver;
import com.sqlapp.jdbc.sql.JdbcQueryHandler;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;

/** Regression tests use only isolated embedded in-memory DuckDB databases. */
class DuckDbReviewRegressionTest {

	@Test
	void mixedUnionVariantValuesRetainTagsAndDynamicTypes() throws Exception {
		try (var source = DriverManager.getConnection("jdbc:duckdb:");
				var target = DriverManager.getConnection("jdbc:duckdb:")) {
			final var dialect = DialectResolver.getInstance().getDialect(source);
			assumeTrue(dialect instanceof DuckDbVersions.DuckDb20);
			execute(source,
					"CREATE TABLE mixed(id INTEGER PRIMARY KEY, u UNION(v VARIANT,s VARCHAR), nested STRUCT(\"a/b~\" UNION(v VARIANT,s VARCHAR)[]), bag MAP(VARCHAR, UNION(v VARIANT,s VARCHAR)), fixed UNION(v VARIANT,s VARCHAR)[2])");
			execute(source,
					"""
							INSERT INTO mixed VALUES
							(1, union_value(v:=42::SMALLINT::VARIANT), {'a/b~':[union_value(v:=42::BIGINT::VARIANT)]}, map(['k'],[union_value(v:=42::SMALLINT::VARIANT)]), [union_value(v:=42::SMALLINT::VARIANT),union_value(v:=NULL::VARIANT)]),
							(2, union_value(s:='42'), {'a/b~':[union_value(s:='42')]}, map(['k'],[union_value(s:='42')]), [union_value(s:='42'),NULL]),
							(3, union_value(v:=NULL::VARIANT), {'a/b~':[union_value(v:=NULL::VARIANT)]}, map([],[]), NULL),
							(4, NULL, NULL, NULL, NULL)
							""");
			final var reader = dialect.getCatalogReader().getSchemaReader();
			reader.setSchemaName("main");
			final Schema schema = SchemaUtils.readXml(new StringReader(reader.getAllFull(source).getFirst().asXml()));
			schema.setDialect(dialect);
			final var table = schema.getTables().get("mixed");
			for (var operation : dialect.createSqlFactoryRegistry().createSql(table, SqlType.CREATE))
				execute(target, operation.getSqlText());
			table.setRowIteratorHandler(new JdbcDynamicRowIteratorHandler(source));
			assertEquals(4, BulkInsertResolver.execute(target, table, BulkOption.defaults()));
			final String check = "SELECT id, union_tag(u), variant_typeof(union_extract(u,'v')), to_json(u), to_json(nested), variant_typeof(union_extract(nested.\"a/b~\"[1],'v')) FROM mixed ORDER BY id";
			assertEquals(values(source, check), values(target, check));
			final String exact = "SELECT " + table.getColumns().stream()
					.map(column -> dialect.getDbDataType(column).getJdbcTypeHandler()
							.readExpression(dialect.quote(column.getName()), column))
					.collect(java.util.stream.Collectors.joining(", ")) + " FROM mixed ORDER BY id";
			assertEquals(values(source, exact), values(target, exact));
			final var column = table.getColumns().get("u");
			final var handler = dialect.getDbDataType(column).getJdbcTypeHandler();
			try (var statement = source.createStatement();
					var rs = statement
							.executeQuery("SELECT " + handler.readExpression("u", column) + " FROM mixed WHERE id=1")) {
				assertTrue(rs.next());
				final Object portable = handler.getObject(rs, 1, column);
				assertEquals(List.of("INT16"), values(target, "SELECT variant_typeof(union_extract("
						+ dialect.getSqlValueDefinition(column, portable) + ", 'v'))"));
			}
			assertThrows(SQLException.class, () -> values(target,
					"SELECT " + dialect.getSqlValueDefinition(column, "{\"tag\":\"missing\",\"value\":null}")));
			assertEquals(4, BulkUpsertResolver.execute(target, table, BulkUpsertOption.defaults()));
			assertEquals(values(source, check), values(target, check));
			assertEquals(values(source, exact), values(target, exact));
			assertEquals(4, BulkUpsertResolver.execute(target, table,
					BulkUpsertOption.builder().insertWhenNotMatched(false).build()));
			assertEquals(values(source, exact), values(target, exact));
		}
	}

	@Test
	void taggedAndWideNativeValuesSurviveBatchAndUpsert() throws Exception {
		try (var source = DriverManager.getConnection("jdbc:duckdb:");
				var target = DriverManager.getConnection("jdbc:duckdb:")) {
			final var dialect = DialectResolver.getInstance().getDialect(source);
			assumeTrue(dialect instanceof DuckDbVersions.DuckDb20, "native tagged fixture targets DuckDB 2.0");
			execute(source, """
					CREATE TABLE t(id INTEGER PRIMARY KEY, u UNION(num INTEGER, str VARCHAR), v VARIANT,
					 b BIGNUM, bits BIT, fixed INTEGER[3], nested STRUCT(items VARCHAR[], m MAP(VARCHAR,INTEGER)),
					 un UNION(num INTEGER, str VARCHAR)[], vn STRUCT(x VARIANT))
					""");
			execute(source,
					"""
							INSERT INTO t VALUES
							(1, union_value(num:=42), 42::INTEGER::VARIANT, '12345678901234567890123456789012345678901234567890'::BIGNUM,
							 '00101'::BIT, [1,NULL,3], {'items':['NULL',NULL,'a,b','it''s'],'m':map(['x'],[42])},
							 [union_value(num:=42),union_value(num:=NULL::INTEGER)], {'x':42::SMALLINT::VARIANT}),
							(2, union_value(str:='42'), {'x':42::BIGINT,'s':'42'}::VARIANT, -1, '0'::BIT, [0,0,0], NULL, NULL, {'x':'42'::VARIANT}),
							(3, union_value(num:=NULL::INTEGER), [1::VARIANT,'x'::VARIANT,NULL::VARIANT]::VARIANT, 0, '1'::BIT, NULL, NULL, [], {'x':NULL::VARIANT}),
							(4, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL)
							""");
			final var reader = dialect.getCatalogReader().getSchemaReader();
			reader.setSchemaName("main");
			final Schema schema = SchemaUtils.readXml(new StringReader(reader.getAllFull(source).getFirst().asXml()));
			schema.setDialect(dialect);
			final var table = schema.getTables().get("t");
			for (var operation : dialect.createSqlFactoryRegistry().createSql(table, SqlType.CREATE))
				execute(target, operation.getSqlText());
			table.setRowIteratorHandler(new JdbcDynamicRowIteratorHandler(source));
			assertEquals(4, BulkInsertResolver.execute(target, table, BulkOption.defaults()));
			final String check = "SELECT id, union_tag(u), to_json(u), v::VARCHAR, variant_typeof(v), variant_to_parquet_variant(v)::VARCHAR, b, bits, fixed, nested, to_json(un), variant_typeof(vn.x), variant_to_parquet_variant(vn.x)::VARCHAR FROM t ORDER BY id";
			assertEquals(values(source, check), values(target, check));
			final Column variant = table.getColumns().get("v");
			final var handler = dialect.getDbDataType(variant).getJdbcTypeHandler();
			final Object portable;
			try (var statement = source.createStatement();
					var rs = statement
							.executeQuery("SELECT " + handler.readExpression("v", variant) + " FROM t WHERE id=1")) {
				assertTrue(rs.next());
				portable = handler.getObject(rs, 1, variant);
				assertEquals(List.of("INT32"), values(target,
						"SELECT variant_typeof(" + dialect.getSqlValueDefinition(variant, portable) + ")"));
			}
			assertThrows(SQLException.class,
					() -> values(target, "SELECT " + dialect.getSqlValueDefinition(variant, "42")));
			final Column combined = new Column("combined").setDataType(DataType.OTHER)
					.setDataTypeName("UNION(a VARIANT, b INTEGER)");
			assertTrue(handler.readExpression("combined", combined).contains("union_tag"));
			execute(target,
					"CREATE TABLE history(id INTEGER, v VARIANT, valid_from TIMESTAMP NOT NULL, valid_to TIMESTAMP, is_current BOOLEAN NOT NULL)");
			final var history = new Table("history");
			history.setDialect(dialect);
			history.getColumns().add(new Column("id").setDataType(DataType.INT));
			history.getColumns().add(variant.clone());
			history.getColumns().add(new Column("valid_from").setDataType(DataType.TIMESTAMP));
			history.getColumns().add(new Column("valid_to").setDataType(DataType.TIMESTAMP));
			history.getColumns().add(new Column("is_current").setDataType(DataType.BOOLEAN));
			final var definition = new com.sqlapp.data.schemas.migration.MigrationSnapshotDefinition("source",
					"history", List.of("id"), List.of("v"), "valid_from", "valid_to", "is_current", true);
			final var result = com.sqlapp.jdbc.bulk.SetBasedMigrationSnapshotResolver.resolve(target).execute(target,
					history, definition, java.time.Instant.parse("2026-01-01T00:00:00Z"),
					List.of(java.util.Map.of("id", 1, "v", portable)), 2);
			assertEquals(1, result.insertedRows());
			assertEquals(List.of("INT32"), values(target, "SELECT variant_typeof(v) FROM history"));
			assertEquals(4, BulkUpsertResolver.execute(target, table, BulkUpsertOption.defaults()));
			assertEquals(values(source, check), values(target, check));
			assertEquals(4, BulkUpsertResolver.execute(target, table,
					BulkUpsertOption.builder().insertWhenNotMatched(false).build()));
			assertEquals(values(source, check), values(target, check));
		}
	}

	@Test
	void temporalSqlLiteralsUseNativeCastsAndPreservePrecision() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:duckdb:")) {
			final Dialect dialect = DialectResolver.getInstance().getDialect(connection);
			final Column date = new Column("d").setDataType(DataType.DATE);
			assertEquals(List.of("0001-01-01 (BC)"), values(connection,
					"SELECT (" + dialect.getSqlValueDefinition(date, java.time.LocalDate.of(0, 1, 1)) + ")::VARCHAR"));
			assertEquals(List.of("-infinity"),
					values(connection, "SELECT (" + dialect.getSqlValueDefinition(date, "-infinity") + ")::VARCHAR"));
			final Column time = new Column("t").setDataType(DataType.TIME);
			assertEquals(List.of("12:34:00.000001"),
					values(connection,
							"SELECT ("
									+ dialect.getSqlValueDefinition(time, java.time.LocalTime.parse("12:34:00.000001"))
									+ ")::VARCHAR"));
			final Column timetz = new Column("tz").setDataType(DataType.TIME_WITH_TIMEZONE);
			assertEquals(List.of("12:34:00+05:30"), values(connection, "SELECT ("
					+ dialect.getSqlValueDefinition(timetz, java.time.OffsetTime.parse("12:34+05:30")) + ")::VARCHAR"));
			assertEquals("NULL", dialect.getSqlValueDefinition(timetz, null));
		}
	}

	private static void execute(final Connection connection, final String sql) throws SQLException {
		try (var statement = connection.createStatement()) {
			statement.execute(sql);
		}
	}

	private static List<String> values(final Connection connection, final String query) throws SQLException {
		final List<String> result = new ArrayList<>();
		try (var statement = connection.createStatement(); var rows = statement.executeQuery(query)) {
			while (rows.next()) {
				for (int i = 1; i <= rows.getMetaData().getColumnCount(); i++) {
					result.add(rows.getString(i));
				}
			}
		}
		return result;
	}

	private static void row(final Table table, final int id, final String value) {
		table.getRows().clear();
		final var row = table.newRow();
		row.put("id", id);
		row.put("v", value);
		table.getRows().add(row);
	}

	@Test
	void attachedCatalogIsPreservedAcrossAppenderBatchUpsertAndUpdate() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:duckdb:")) {
			final Dialect dialect = DialectResolver.getInstance().getDialect(connection);
			execute(connection, "ATTACH ':memory:' AS aux");
			execute(connection, "CREATE TABLE main.t(id INTEGER PRIMARY KEY, v VARCHAR)");
			execute(connection, "CREATE TABLE aux.main.t(id INTEGER PRIMARY KEY, v VARCHAR)");
			final Table table = new Table("t").setCatalogName("aux").setSchemaName("main");
			table.setDialect(dialect);
			table.getColumns().add(new Column("id").setDataType(DataType.INT));
			table.getColumns().add(new Column("v").setDataType(DataType.VARCHAR));
			table.setPrimaryKey(table.getColumns().get("id"));
			row(table, 1, "appender");
			assertEquals(1, BulkInsertResolver.execute(connection, table, BulkOption.defaults()));
			row(table, 2, "batch");
			assertEquals(1, BulkInsertResolver.execute(connection, table,
					BulkOption.builder().insertMethod(BulkInsertMethod.JDBC_BATCH).build()));
			row(table, 1, "upsert");
			assertEquals(1, BulkUpsertResolver.execute(connection, table, BulkUpsertOption.defaults()));
			row(table, 2, "update");
			assertEquals(1, BulkUpsertResolver.execute(connection, table,
					BulkUpsertOption.builder().insertWhenNotMatched(false).build()));
			assertEquals(List.of("1", "upsert", "2", "update"),
					values(connection, "SELECT id, v FROM aux.main.t ORDER BY id"));
			assertEquals(List.of("0"), values(connection, "SELECT count(*) FROM memory.main.t"));
		}
	}

	@Test
	void nativeTypesDatesAndOffsetsSurviveSchemaAndRowTransfer() throws Exception {
		try (var source = DriverManager.getConnection("jdbc:duckdb:");
				var target = DriverManager.getConnection("jdbc:duckdb:")) {
			final Dialect dialect = DialectResolver.getInstance().getDialect(source);
			assumeTrue(((DuckDb) dialect).supportsAlterTableAddUnique(), "wide native fixture uses DuckDB 2.0");
			execute(source, """
					CREATE TABLE src(id INTEGER, d DATE, tz TIMETZ, a INTEGER[], m MAP(VARCHAR, INTEGER),
					  s STRUCT(x INTEGER, "Mixed  Case" VARCHAR), ns TIMESTAMP_NS, u UHUGEINT, span INTERVAL)
					""");
			execute(source, """
					INSERT INTO src VALUES
					(1, DATE '0000-01-01', TIMETZ '12:34:56.123456+05:30', [1,NULL,3], map(['a'],[42]),
					 {'x':42,'Mixed  Case':'it''s'}, TIMESTAMP_NS '2020-01-01 00:00:00.123456789',
					 340282366920938463463374607431768211455::UHUGEINT, INTERVAL '24 hours'),
					(2, DATE 'infinity', TIMETZ '01:02:03.000001-04:00', [], map([],[]),
					 {'x':NULL,'Mixed  Case':NULL}, TIMESTAMP_NS '1970-01-01 00:00:00.000000001', 0,
					 INTERVAL '-1 month 2 days -00:00:01.5'),
					(3, NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL),
					(4, DATE '-infinity',NULL,NULL,NULL,NULL,NULL,NULL,INTERVAL '5124096 hours')
					""");
			final var reader = dialect.getCatalogReader().getSchemaReader();
			reader.setSchemaName("main");
			final Schema schema = SchemaUtils.readXml(new StringReader(reader.getAllFull(source).getFirst().asXml()));
			schema.setDialect(dialect);
			final Table table = schema.getTables().get("src");
			assertEquals("STRUCT(x INTEGER, \"Mixed  Case\" VARCHAR)", table.getColumns().get("s").getDataTypeName());
			for (final var operation : dialect.createSqlFactoryRegistry().createSql(table, SqlType.CREATE)) {
				execute(target, operation.getSqlText());
			}
			table.setRowIteratorHandler(new JdbcDynamicRowIteratorHandler(source));
			assertEquals(4, BulkInsertResolver.execute(target, table, BulkOption.defaults()));
			assertEquals(values(source, "SELECT * FROM src ORDER BY id"),
					values(target, "SELECT * FROM src ORDER BY id"));
			assertEquals(values(source, "SELECT d::VARCHAR, isfinite(d) FROM src ORDER BY id"),
					values(target, "SELECT d::VARCHAR, isfinite(d) FROM src ORDER BY id"));
			for (final Column column : table.getColumns()) {
				if (column.getDataType() == DataType.OTHER) {
					final String text = values(source,
							"SELECT " + dialect.quote(column.getName()) + " FROM src WHERE id=1").getFirst();
					assertEquals(List.of(text),
							values(target, "SELECT " + dialect.getSqlValueDefinition(column, text)));
				}
			}
		}
	}

	@Test
	void dateOnlyAppenderKeepsBcAndInfiniteDates() throws Exception {
		try (var source = DriverManager.getConnection("jdbc:duckdb:");
				var target = DriverManager.getConnection("jdbc:duckdb:")) {
			final Dialect dialect = DialectResolver.getInstance().getDialect(source);
			assumeTrue(((DuckDb) dialect).supportsConstraintMetadata());
			execute(source, "CREATE TABLE t(d DATE)");
			execute(source, "INSERT INTO t VALUES (DATE '0000-01-01'), (DATE 'infinity'), (DATE '-infinity'), (NULL)");
			execute(target, "CREATE TABLE t(d DATE)");
			final var reader = dialect.getCatalogReader().getSchemaReader();
			reader.setSchemaName("main");
			final Table table = reader.getAllFull(source).getFirst().getTables().get("t");
			table.setRowIteratorHandler(new JdbcDynamicRowIteratorHandler(source));
			assertEquals(4, BulkInsertResolver.execute(target, table, BulkOption.defaults()));
			assertEquals(values(source, "SELECT d FROM t ORDER BY d"), values(target, "SELECT d FROM t ORDER BY d"));
		}
	}

	@Test
	void renameWorksInBothDirectionsAndCheckChangesFailBeforeExecution() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:duckdb:")) {
			final Dialect dialect = DialectResolver.getInstance().getDialect(connection);
			final var registry = dialect.createSqlFactoryRegistry();
			execute(connection, "CREATE SCHEMA s1");
			execute(connection, "CREATE TABLE s1.before_name(id INTEGER)");
			final Table table = new Table("before_name").setSchemaName("s1");
			table.getColumns().add(new Column("id").setDataType(DataType.INT));
			final Table renamed = table.clone().setName("after name");
			for (final var op : registry.createSql(table.diff(renamed), SqlType.ALTER))
				execute(connection, op.getSqlText());
			for (final var op : registry.createSql(renamed.diff(table), SqlType.ALTER))
				execute(connection, op.getSqlText());
			assertTrue(registry.createSql(table.diff(table.clone()), SqlType.ALTER).isEmpty());
			final Table checked = table.clone();
			checked.getColumns().get("id").setCheck("id > 0");
			final Table stronger = checked.clone();
			stronger.getColumns().get("id").setCheck("id > 10");
			assertThrows(IllegalArgumentException.class, () -> registry.createSql(table.diff(checked), SqlType.ALTER));
			assertThrows(IllegalArgumentException.class,
					() -> registry.createSql(checked.diff(stronger), SqlType.ALTER));
			assertThrows(IllegalArgumentException.class, () -> registry.createSql(checked.diff(table), SqlType.ALTER));
		}
	}

	@Test
	void addUniqueIsVersionGatedAndEnforcesUniqueness() throws Exception {
		final Table table = new Table("t");
		table.getColumns().add(new Column("id").setDataType(DataType.INT));
		final Table unique = table.clone();
		unique.getConstraints().add(new UniqueConstraint("uq", false, unique.getColumns().get("id")));
		assertThrows(IllegalArgumentException.class,
				() -> DialectHolder.duckdb14.createSqlFactoryRegistry().createSql(table.diff(unique), SqlType.ALTER));
		try (var connection = DriverManager.getConnection("jdbc:duckdb:")) {
			final Dialect dialect = DialectResolver.getInstance().getDialect(connection);
			assumeTrue(((DuckDb) dialect).supportsAlterTableAddUnique());
			execute(connection, "CREATE TABLE t(id INTEGER)");
			for (final var op : dialect.createSqlFactoryRegistry().createSql(table.diff(unique), SqlType.ALTER))
				execute(connection, op.getSqlText());
			execute(connection, "INSERT INTO t VALUES (1)");
			assertThrows(SQLException.class, () -> execute(connection, "INSERT INTO t VALUES (1)"));
		}
	}

	@Test
	void generatedPagingWorksWithOffsetZeroLimitAndCount() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:duckdb:")) {
			final Dialect dialect = DialectResolver.getInstance().getDialect(connection);
			execute(connection, "CREATE TABLE t(id INTEGER)");
			execute(connection, "INSERT INTO t SELECT * FROM range(5)");
			final Table table = new Table("t");
			table.getColumns().add(new Column("id").setDataType(DataType.INT));
			final var node = dialect.createSqlFactoryRegistry().createSqlNodes(table, SqlType.SELECT_FOR_APP)
					.getFirst();
			assertNotNull(node.getOffsetParameter());
			assertNotNull(node.getRowParameter());
			for (final int offset : new int[] { 0, 1, 5 }) {
				final List<Integer> actual = new ArrayList<>();
				final var context = new ParametersContext();
				context.put("_countSql", false);
				context.put("_orderBy", "id");
				new JdbcQueryHandler(node, new ResultSetNextHandler() {
					@Override
					public void handleResultSetNext(final ExResultSet rs) throws SQLException {
						actual.add(rs.getInt(1));
					}
				}).execute(connection, context, 2, offset);
				assertEquals(offset == 5 ? List.of() : List.of(offset, offset + 1), actual);
			}
			final var count = new ParametersContext();
			count.put("_countSql", true);
			count.put("_row", 2);
			count.put("_offset", 1);
			assertFalse(node.eval(count).getSql().contains("LIMIT"));
			assertFalse(node.eval(count).getSql().contains("OFFSET"));
		}
	}
}
