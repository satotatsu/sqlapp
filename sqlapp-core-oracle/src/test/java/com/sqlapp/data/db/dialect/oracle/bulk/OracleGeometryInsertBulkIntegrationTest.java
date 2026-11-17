/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.oracle.bulk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.geolatte.geom.Geometry;
import org.geolatte.geom.codec.Wkt;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.datatype.JdbcTypeHandler;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Row;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.rowiterator.DataFormat;
import com.sqlapp.data.schemas.rowiterator.FileRowIteratorFactory;
import com.sqlapp.jdbc.bulk.BulkInsertResolver;
import com.sqlapp.jdbc.bulk.BulkOption;
import com.sqlapp.jdbc.bulk.JdbcBatchBulkInsertExecutor;
import com.sqlapp.util.file.TextFileWriter;

/**
 * Oracle の GEOMETRY 列で、通常の INSERT(JDBC バッチ)と BulkInsert(JDBC バッチ)が同じ値を保存すること、
 * DB → CSV → DB で値が保たれること、Oracle で表現できない値は変換せずにエラーになることを確認する。
 * <p>
 * 実DBに接続するため、環境変数 SQLAPP_TEST_ORACLE_URL(と SQLAPP_TEST_ORACLE_USER / SQLAPP_TEST_ORACLE_PASSWORD)が設定されている場合だけ実行する。 Oracle Spatial(SDO_GEOMETRY)が必要。
 * テーブルは一意な名前で作成し、終了時に削除する。
 * </p>
 */
@EnabledIfEnvironmentVariable(named = "SQLAPP_TEST_ORACLE_URL", matches = ".+")
class OracleGeometryInsertBulkIntegrationTest {

	/** 保存できる値と、読み戻したときの EWKT */
	private static final String[][] SUPPORTED = {
			{ "SRID=4326;POINT(139.7 35.6)", "SRID=4326;POINT(139.7 35.6)" },
			{ "POINT(1 2)", "POINT(1 2)" },
			{ null, null },
			{ "SRID=3857;MULTIPOLYGON(((0 0,1 0,1 1,0 0)),((5 5,6 5,6 6,5 5)))",
					"SRID=3857;MULTIPOLYGON(((0 0,1 0,1 1,0 0)),((5 5,6 5,6 6,5 5)))" },
			{ "LINESTRING(0 0,1 1)", "LINESTRING(0 0,1 1)" },
			{ "GEOMETRYCOLLECTION(POINT(1 2),LINESTRING(0 0,1 1))", "GEOMETRYCOLLECTION(POINT(1 2),LINESTRING(0 0,1 1))" },
			{ "POINT Z(1 2 3)", "POINT(1 2 3)" },
			{ "POINT M(1 2 3)", "POINTM(1 2 3)" },
			{ "SRID=4326;LINESTRING Z(139 35 1,140 36 2)", "SRID=4326;LINESTRING(139 35 1,140 36 2)" },
	};

	/** Oracle で表現できないため、エラーになる値 */
	private static final String[] UNSUPPORTED = { "POINT EMPTY", "GEOMETRYCOLLECTION EMPTY" };

	@TempDir
	File directory;

	private final String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
	private final List<String> tables = new ArrayList<>();
	private final List<Connection> connections = new ArrayList<>();

	private Connection connect() throws SQLException {
		final Connection connection = DriverManager.getConnection(System.getenv("SQLAPP_TEST_ORACLE_URL"),
				System.getenv("SQLAPP_TEST_ORACLE_USER"), System.getenv("SQLAPP_TEST_ORACLE_PASSWORD"));
		connections.add(connection);
		return connection;
	}

	@AfterEach
	void cleanUp() throws SQLException {
		try (Connection connection = connect(); Statement statement = connection.createStatement()) {
			for (final String table : tables) {
				statement.execute("BEGIN EXECUTE IMMEDIATE 'DROP TABLE " + table + "'; EXCEPTION WHEN OTHERS THEN NULL; END;");
			}
		} finally {
			for (final Connection connection : connections) {
				try {
					connection.close();
				} catch (final SQLException e) {
					// 接続が切断されている場合がある
				}
			}
		}
	}

	private String createTable(final Connection connection, final String prefix) throws SQLException {
		final String name = prefix + "_" + suffix;
		try (Statement statement = connection.createStatement()) {
			statement.execute("CREATE TABLE " + name + " (ID number(10) primary key, G sdo_geometry)");
		}
		tables.add(name);
		return name;
	}

	private static Table newTable(final Dialect dialect, final String name) {
		final Table table = new Table(name);
		table.setDialect(dialect);
		table.getColumns().add(new Column("ID").setDataType(DataType.INT));
		table.getColumns().add(new Column("G").setDataType(DataType.GEOMETRY));
		return table;
	}

	private static Table supportedRows(final Dialect dialect, final String name) {
		final Table table = newTable(dialect, name);
		for (int i = 0; i < SUPPORTED.length; i++) {
			final int id = i + 1;
			// 偶数行は CSV と同じく文字列、奇数行は Geometry オブジェクトで渡す
			final Object value = SUPPORTED[i][0] == null ? null
					: (id % 2 == 0 ? SUPPORTED[i][0] : Wkt.fromWkt(SUPPORTED[i][0]));
			table.getRows().add(row -> {
				row.put("ID", id);
				row.put("G", value);
			});
		}
		return table;
	}

	/** DB 側の関数で文字列化した値 */
	private static Map<Integer, List<String>> selectText(final Connection connection, final String table)
			throws SQLException {
		final Map<Integer, List<String>> result = new LinkedHashMap<>();
		try (Statement statement = connection.createStatement();
				ResultSet rs = statement.executeQuery("SELECT x.ID, SDO_UTIL.TO_WKTGEOMETRY(x.G), x.G.SDO_SRID, x.G.SDO_GTYPE FROM " + table + " x ORDER BY x.ID")) {
			while (rs.next()) {
				final List<String> values = new ArrayList<>();
				for (int i = 2; i <= rs.getMetaData().getColumnCount(); i++) {
					values.add(rs.getString(i));
				}
				result.put(rs.getInt(1), values);
			}
		}
		return result;
	}

	/** sqlapp の型ハンドラーで読み戻した値 */
	private static Map<Integer, Object> readBack(final Connection connection, final Dialect dialect, final String table)
			throws SQLException {
		final JdbcTypeHandler handler = dialect.getDbDataTypes().getDbType(DataType.GEOMETRY).getJdbcTypeHandler();
		final Map<Integer, Object> result = new LinkedHashMap<>();
		try (Statement statement = connection.createStatement();
				ResultSet rs = statement.executeQuery("SELECT ID, G FROM " + table + " ORDER BY ID")) {
			while (rs.next()) {
				result.put(rs.getInt("ID"), handler.getObject(rs, "G"));
			}
		}
		return result;
	}

	private static void assertReadBack(final Map<Integer, Object> values) {
		assertEquals(SUPPORTED.length, values.size());
		for (int i = 0; i < SUPPORTED.length; i++) {
			final Object value = values.get(i + 1);
			if (SUPPORTED[i][1] == null) {
				assertEquals(null, value, "id=" + (i + 1));
			} else {
				final Geometry<?> geometry = assertInstanceOf(Geometry.class, value, "id=" + (i + 1));
				assertEquals(SUPPORTED[i][1], Wkt.toWkt(geometry), "id=" + (i + 1));
			}
		}
	}

	@Test
	void insertAndBulkStoreSameValues() throws Exception {
		final Connection connection = connect();
		final Dialect dialect = DialectResolver.getInstance().getDialect(connection);
		final String insertTable = createTable(connection, "SQLAPP_GEO_INS");
		final String bulkTable = createTable(connection, "SQLAPP_GEO_BLK");
		assertEquals(SUPPORTED.length, new JdbcBatchBulkInsertExecutor(dialect).execute(connection,
				supportedRows(dialect, insertTable), BulkOption.defaults()));
		assertEquals(SUPPORTED.length, BulkInsertResolver.resolve(dialect).execute(connection,
				supportedRows(dialect, bulkTable), BulkOption.defaults()));
		commit(connection);
		assertEquals(selectText(connection, insertTable), selectText(connection, bulkTable));
		assertReadBack(readBack(connection, dialect, insertTable));
		assertReadBack(readBack(connection, dialect, bulkTable));
	}

	@Test
	void dbToCsvToDbKeepsValues() throws Exception {
		final Connection connection = connect();
		final Dialect dialect = DialectResolver.getInstance().getDialect(connection);
		final String source = createTable(connection, "SQLAPP_GEO_SRC");
		final String target = createTable(connection, "SQLAPP_GEO_DST");
		new JdbcBatchBulkInsertExecutor(dialect).execute(connection, supportedRows(dialect, source),
				BulkOption.defaults());
		commit(connection);
		final Map<Integer, Object> exported = readBack(connection, dialect, source);
		final Table table = newTable(dialect, source);
		final File file = new File(directory, "geometries.csv");
		try (TextFileWriter writer = DataFormat.CSV.createCsvListWriter(file, "UTF-8")) {
			writer.writeHeader("ID", "G");
			for (final Map.Entry<Integer, Object> entry : exported.entrySet()) {
				writer.writeRow(table.getColumns().get("ID").getFormatter().format(entry.getKey()),
						table.getColumns().get("G").getFormatter().format(entry.getValue()));
			}
		}
		final Table imported = newTable(dialect, target);
		imported.setRowIteratorHandler(
				FileRowIteratorFactory.create(file, "UTF-8", 1, 1, null, null, null, (r, c, v) -> v));
		assertEquals(SUPPORTED.length,
				BulkInsertResolver.resolve(dialect).execute(connection, imported, BulkOption.defaults()));
		commit(connection);
		assertEquals(selectText(connection, source), selectText(connection, target));
		assertReadBack(readBack(connection, dialect, target));
	}

	@Test
	void unsupportedValuesAreRejected() throws Exception {
		for (final String value : UNSUPPORTED) {
			for (final boolean bulk : new boolean[] { false, true }) {
				final Connection connection = connect();
				final Dialect dialect = DialectResolver.getInstance().getDialect(connection);
				final String name = createTable(connection, "SQLAPP_GEO_NG" + tables.size());
				final Table table = newTable(dialect, name);
				table.getRows().add(row -> {
					row.put("ID", 1);
					row.put("G", value);
				});
				final Exception exception = assertThrows(Exception.class,
						() -> (bulk ? BulkInsertResolver.resolve(dialect) : new JdbcBatchBulkInsertExecutor(dialect))
								.execute(connection, table, BulkOption.defaults()),
						value);
				assertTrue(hasIllegalArgument(exception), value + ": " + exception);
				// 別の接続で確認する(Oracle の BulkInsert は失敗時に接続が使えなくなる場合がある)
				try (Connection check = connect(); Statement statement = check.createStatement();
						ResultSet rs = statement.executeQuery("SELECT COUNT(*) FROM " + name)) {
					assertTrue(rs.next());
					assertEquals(0, rs.getInt(1), value);
				}
			}
		}
	}

	private static boolean hasIllegalArgument(final Throwable throwable) {
		for (Throwable current = throwable; current != null; current = current.getCause()) {
			if (current instanceof IllegalArgumentException) {
				return true;
			}
			if (current.getCause() == current) {
				break;
			}
		}
		return false;
	}

	private static void commit(final Connection connection) throws SQLException {
		if (!connection.getAutoCommit()) {
			connection.commit();
		}
	}
}
