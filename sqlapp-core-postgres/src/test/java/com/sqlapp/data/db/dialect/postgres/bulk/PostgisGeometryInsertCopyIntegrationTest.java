/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.postgres.bulk;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.datatype.JdbcTypeHandler;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.geometry.Box;
import com.sqlapp.data.geometry.Circle;
import com.sqlapp.data.geometry.Line;
import com.sqlapp.data.geometry.Lseg;
import com.sqlapp.data.geometry.Path;
import com.sqlapp.data.geometry.Point;
import com.sqlapp.data.geometry.Polygon;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Row;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.rowiterator.DataFormat;
import com.sqlapp.data.schemas.rowiterator.FileRowIteratorFactory;
import com.sqlapp.jdbc.bulk.BulkOption;
import com.sqlapp.jdbc.bulk.JdbcBatchBulkInsertExecutor;
import com.sqlapp.util.file.TextFileWriter;

/**
 * PostgreSQL + PostGIS で、通常の INSERT(JDBC バッチ)と COPY が同じ値を保存すること、 および DB → CSV →
 * DB で意味が保たれることを確認する。
 * <p>
 * 実DBに接続するため、環境変数 SQLAPP_TEST_POSTGIS_URL(と SQLAPP_TEST_POSTGIS_USER /
 * SQLAPP_TEST_POSTGIS_PASSWORD) が設定されている場合だけ実行する。接続先には PostGIS
 * 拡張が必要。テーブルは一意な名前で作成し、終了時に削除する。
 * </p>
 */
@EnabledIfEnvironmentVariable(named = "SQLAPP_TEST_POSTGIS_URL", matches = ".+")
class PostgisGeometryInsertCopyIntegrationTest {

	private static final String[][] COLUMNS = { { "point", "point" }, { "box", "box" }, { "lseg", "lseg" },
			{ "line", "line" }, { "path_open", "path" }, { "path_closed", "path" }, { "polygon", "polygon" },
			{ "circle", "circle" }, { "g", "geometry" } };

	private static final DataType[] TYPES = { DataType.POINT, DataType.BOX, DataType.LSEG, DataType.LINE, DataType.PATH,
			DataType.PATH, DataType.POLYGON, DataType.CIRCLE, DataType.GEOMETRY };

	@TempDir
	File directory;

	private Connection connection;
	private Dialect dialect;
	private final String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
	private final List<String> tables = new ArrayList<>();

	@BeforeEach
	void connect() throws SQLException {
		connection = DriverManager.getConnection(System.getenv("SQLAPP_TEST_POSTGIS_URL"),
				System.getenv("SQLAPP_TEST_POSTGIS_USER"), System.getenv("SQLAPP_TEST_POSTGIS_PASSWORD"));
		connection.setAutoCommit(true);
		dialect = DialectResolver.getInstance().getDialect(connection);
	}

	@AfterEach
	void cleanUp() throws SQLException {
		if (connection == null) {
			return;
		}
		try (Statement statement = connection.createStatement()) {
			for (final String table : tables) {
				statement.execute("DROP TABLE IF EXISTS " + table);
			}
		} finally {
			connection.close();
		}
	}

	private String createTable(final String prefix) throws SQLException {
		final String name = prefix + "_" + suffix;
		final StringBuilder ddl = new StringBuilder("CREATE TABLE " + name + " (id int primary key");
		for (final String[] column : COLUMNS) {
			ddl.append(", ").append(column[0]).append(' ').append(column[1]);
		}
		ddl.append(')');
		try (Statement statement = connection.createStatement()) {
			statement.execute(ddl.toString());
		}
		tables.add(name);
		return name;
	}

	private Table newTable(final String name) {
		final Table table = new Table(name);
		table.setDialect(dialect);
		table.getColumns().add(new Column("id").setDataType(DataType.INT));
		for (int i = 0; i < COLUMNS.length; i++) {
			table.getColumns().add(new Column(COLUMNS[i][0]).setDataType(TYPES[i]));
		}
		return table;
	}

	/**
	 * 1: オブジェクトで指定 / 2: CSV と同じく文字列で指定 / 3: NULL / 4: EMPTY と Z/M
	 */
	private void addRows(final Table table) {
		table.getRows().add(row -> {
			row.put("id", 1);
			row.put("point", new Point(1.5, -2));
			row.put("box", new Box(1, 1, 0, 0));
			row.put("lseg", new Lseg(0, 0, 1, 1));
			row.put("line", new Line(1, 2, 3, 6));
			row.put("path_open", new Path(true, 0, 0, 1, 1, 2, 0));
			row.put("path_closed", new Path(false, 0, 0, 1, 1, 2, 0));
			row.put("polygon", new Polygon(0, 0, 1, 1, 1, 0));
			row.put("circle", new Circle(1, 2, 3));
			row.put("g", "SRID=4326;POINT Z(139.7 35.6 10)");
		});
		table.getRows().add(row -> {
			row.put("id", 2);
			row.put("point", "(1,2)");
			row.put("box", "(1,1),(0,0)");
			row.put("lseg", "[(0,0),(1,1)]");
			row.put("line", "{1,-1,0}");
			row.put("path_open", "[(0,0),(1,1)]");
			row.put("path_closed", "((0,0),(1,1))");
			row.put("polygon", "((0,0),(1,1),(1,0))");
			row.put("circle", "<(0,0),1>");
			row.put("g", "0101000020E610000000000000006061400000000000804140");
		});
		table.getRows().add(row -> row.put("id", 3));
		table.getRows().add(row -> {
			row.put("id", 4);
			row.put("line", new Line(2, 0, 2, 1));
			row.put("g", "POINT ZM(1 2 3 4)");
		});
		table.getRows().add(row -> {
			row.put("id", 5);
			row.put("g", "SRID=3857;MULTIPOLYGON(((0 0,1 0,1 1,0 0)),((5 5,6 5,6 6,5 5)))");
		});
		table.getRows().add(row -> {
			row.put("id", 6);
			row.put("g", "POINT M(1 2 3)");
		});
		table.getRows().add(row -> {
			row.put("id", 7);
			row.put("g", "POINT EMPTY");
		});
	}

	/**
	 * DB 上の値を、型ごとの比較用テキストにして返します。
	 */
	private Map<Integer, Map<String, String>> select(final String table) throws SQLException {
		final StringBuilder sql = new StringBuilder("SELECT id");
		for (final String[] column : COLUMNS) {
			if ("geometry".equals(column[1])) {
				sql.append(", ST_AsEWKT(").append(column[0]).append(") AS ").append(column[0]);
				sql.append(", encode(ST_AsEWKB(").append(column[0]).append("),'hex') AS ").append(column[0])
						.append("_ewkb");
				sql.append(", ST_SRID(").append(column[0]).append(") AS ").append(column[0]).append("_srid");
				sql.append(", ST_Zmflag(").append(column[0]).append(") AS ").append(column[0]).append("_zm");
				sql.append(", ST_IsEmpty(").append(column[0]).append(") AS ").append(column[0]).append("_empty");
			} else {
				sql.append(", ").append(column[0]).append("::text AS ").append(column[0]);
			}
		}
		sql.append(" FROM ").append(table).append(" ORDER BY id");
		final Map<Integer, Map<String, String>> result = new LinkedHashMap<>();
		try (Statement statement = connection.createStatement();
				ResultSet rs = statement.executeQuery(sql.toString())) {
			final int count = rs.getMetaData().getColumnCount();
			while (rs.next()) {
				final Map<String, String> values = new LinkedHashMap<>();
				for (int i = 2; i <= count; i++) {
					values.put(rs.getMetaData().getColumnLabel(i), rs.getString(i));
				}
				result.put(rs.getInt(1), values);
			}
		}
		return result;
	}

	@Test
	void insertAndCopyStoreSameValues() throws Exception {
		final String insertTable = createTable("sqlapp_geo_insert");
		final String copyTable = createTable("sqlapp_geo_copy");
		final Table insert = newTable(insertTable);
		addRows(insert);
		final Table copy = newTable(copyTable);
		addRows(copy);
		assertEquals(7, new JdbcBatchBulkInsertExecutor(dialect).execute(connection, insert, BulkOption.defaults()));
		assertEquals(7, new PostgresBulkInsertExecutor(dialect).execute(connection, copy, BulkOption.defaults()));

		final Map<Integer, Map<String, String>> inserted = select(insertTable);
		final Map<Integer, Map<String, String>> copied = select(copyTable);
		assertEquals(inserted, copied);

		// 期待値を直接確認する
		final Map<String, String> row1 = inserted.get(1);
		assertEquals("(1.5,-2)", row1.get("point"));
		assertEquals("(1,1),(0,0)", row1.get("box"));
		assertEquals("[(0,0),(1,1)]", row1.get("lseg"));
		assertEquals("[(0,0),(1,1),(2,0)]", row1.get("path_open"));
		assertEquals("((0,0),(1,1),(2,0))", row1.get("path_closed"));
		assertEquals("((0,0),(1,1),(1,0))", row1.get("polygon"));
		assertEquals("<(1,2),3>", row1.get("circle"));
		assertEquals("SRID=4326;POINT(139.7 35.6 10)", row1.get("g"));
		assertEquals("4326", row1.get("g_srid"));
		assertEquals("2", row1.get("g_zm")); // Z
		final Map<String, String> row2 = inserted.get(2);
		assertEquals("[(0,0),(1,1)]", row2.get("path_open"));
		assertEquals("((0,0),(1,1))", row2.get("path_closed"));
		assertEquals("SRID=4326;POINT(139 35)", row2.get("g"));
		for (final String value : inserted.get(3).values()) {
			assertNull(value);
		}
		assertEquals("POINT(1 2 3 4)", inserted.get(4).get("g"));
		assertEquals("3", inserted.get(4).get("g_zm")); // ZM
		assertEquals("SRID=3857;MULTIPOLYGON(((0 0,1 0,1 1,0 0)),((5 5,6 5,6 6,5 5)))", inserted.get(5).get("g"));
		assertEquals("POINTM(1 2 3)", inserted.get(6).get("g"));
		assertEquals("1", inserted.get(6).get("g_zm")); // M
		// EMPTY は NULL と区別される
		assertEquals("POINT EMPTY", inserted.get(7).get("g"));
		assertEquals("t", inserted.get(7).get("g_empty"));
		// line は直線として一致すること(係数の表現は PostgreSQL が決める)
		assertLineContains(insertTable, 1, 1, 2, 3, 6);
		assertLineContains(insertTable, 2, 0, 0, 1, 1);
		assertLineContains(insertTable, 4, 2, 0, 2, 1);
	}

	/**
	 * sqlapp の幾何型を GEOMETRY 列に INSERT / COPY すると同じ空間型として保存され、元の型に読み戻せること。
	 */
	@Test
	void sqlappGeometricValuesInGeometryColumn() throws Exception {
		final com.sqlapp.data.geometry.AbstractGeometry[] values = { new Point(1, 2), new Path(false, 0, 0, 1, 1, 2, 0),
				new Path(true, 0, 0, 1, 1), new Polygon(0, 0, 1, 1, 1, 0), new Box(2, 3, 0, 1), new Lseg(0, 0, 1, 1),
				new Circle(1, 2, 3) };
		final String[] expected = { "POINT(1 2)", "LINESTRING(0 0,1 1,2 0,0 0)", "LINESTRING(0 0,1 1)",
				"POLYGON((0 0,1 1,1 0,0 0))", "POLYGON((2 3,0 3,0 1,2 1,2 3))", "LINESTRING(0 0,1 1)" };
		final List<String> names = new ArrayList<>();
		for (final String prefix : new String[] { "sqlapp_geo_sq_ins", "sqlapp_geo_sq_copy" }) {
			final String name = prefix + "_" + suffix;
			try (Statement statement = connection.createStatement()) {
				statement.execute("CREATE TABLE " + name + " (id int primary key, g geometry)");
			}
			tables.add(name);
			names.add(name);
			final Table table = new Table(name);
			table.setDialect(dialect);
			table.getColumns().add(new Column("id").setDataType(DataType.INT));
			table.getColumns().add(new Column("g").setDataType(DataType.GEOMETRY));
			for (int i = 0; i < values.length; i++) {
				final int id = i + 1;
				final Object value = values[i];
				table.getRows().add(row -> {
					row.put("id", id);
					row.put("g", value);
				});
			}
			if (prefix.endsWith("ins")) {
				new JdbcBatchBulkInsertExecutor(dialect).execute(connection, table, BulkOption.defaults());
			} else {
				new PostgresBulkInsertExecutor(dialect).execute(connection, table, BulkOption.defaults());
			}
		}
		final List<List<String>> stored = new ArrayList<>();
		for (final String name : names) {
			final List<String> rows = new ArrayList<>();
			try (Statement statement = connection.createStatement();
					ResultSet rs = statement
							.executeQuery("SELECT ST_AsEWKT(g), ST_NPoints(g) FROM " + name + " ORDER BY id")) {
				while (rs.next()) {
					rows.add(rs.getString(1) + "|" + rs.getInt(2));
				}
			}
			stored.add(rows);
		}
		assertEquals(stored.get(0), stored.get(1));
		for (int i = 0; i < expected.length; i++) {
			assertEquals(expected[i], stored.get(0).get(i).split("\\|")[0], "id=" + (i + 1));
		}
		// Circle は 64 角形の POLYGON(閉じるための点を含めて 65 点)
		assertTrue(stored.get(0).get(6).startsWith("POLYGON(("), stored.get(0).get(6));
		assertEquals("65", stored.get(0).get(6).split("\\|")[1]);
		// sqlapp の型へ読み戻す
		final JdbcTypeHandler handler = dialect.getDbDataTypes().getDbType(DataType.GEOMETRY).getJdbcTypeHandler();
		try (Statement statement = connection.createStatement();
				ResultSet rs = statement
						.executeQuery("SELECT id, g FROM " + names.get(1) + " WHERE id <= 6 ORDER BY id")) {
			while (rs.next()) {
				final int index = rs.getInt(1) - 1;
				final Object geometry = handler.getObject(rs, "g");
				final Class<? extends com.sqlapp.data.geometry.AbstractGeometry> type = values[index].getClass();
				assertEquals(values[index],
						com.sqlapp.data.converter.SpatialGeometryConversions.fromSpatial((Geometry<?>) geometry, type),
						"id=" + (index + 1));
			}
		}
	}

	/**
	 * line 値が2点を通ることを PostgreSQL の演算子で確認する。
	 */
	private void assertLineContains(final String table, final int id, final double x1, final double y1, final double x2,
			final double y2) throws SQLException {
		try (Statement statement = connection.createStatement();
				ResultSet rs = statement.executeQuery("SELECT point '(" + x1 + "," + y1 + ")' <@ line AND point '(" + x2
						+ "," + y2 + ")' <@ line FROM " + table + " WHERE id = " + id)) {
			assertTrue(rs.next());
			assertTrue(rs.getBoolean(1), "line of id " + id);
		}
	}

	/**
	 * DB から読み込み、CSV に書き出し、その CSV を COPY で別テーブルに取り込んでも値が変わらないこと。
	 */
	@Test
	void dbToCsvToDbKeepsValues() throws Exception {
		final String source = createTable("sqlapp_geo_src");
		final String target = createTable("sqlapp_geo_dst");
		final Table insert = newTable(source);
		addRows(insert);
		new JdbcBatchBulkInsertExecutor(dialect).execute(connection, insert, BulkOption.defaults());

		// DB → Table(sqlapp の型ハンドラーで読み込む)
		final Table exported = newTable(source);
		try (Statement statement = connection.createStatement();
				ResultSet rs = statement.executeQuery("SELECT * FROM " + source + " ORDER BY id")) {
			while (rs.next()) {
				final Row row = exported.newRow();
				for (final Column column : exported.getColumns()) {
					final JdbcTypeHandler handler = dialect.getDbDataTypes().getDbType(column.getDataType())
							.getJdbcTypeHandler();
					row.put(column, handler.getObject(rs, column.getName()));
				}
				exported.getRows().add(row);
			}
		}
		assertInstanceOf(Path.class, exported.getRows().get(0).get("path_open"));
		assertTrue(((Path) exported.getRows().get(0).get("path_open")).open);
		assertInstanceOf(Geometry.class, exported.getRows().get(0).get("g"));

		// Table → CSV(ExportDataCommand と同じ書き込み)
		final File file = new File(directory, "geometries.csv");
		try (TextFileWriter writer = DataFormat.CSV.createCsvListWriter(file, "UTF-8")) {
			final List<String> headers = new ArrayList<>();
			for (final Column column : exported.getColumns()) {
				headers.add(column.getName());
			}
			writer.writeHeader(headers.toArray(new String[0]));
			for (final Row row : exported.getRows()) {
				final String[] values = new String[exported.getColumns().size()];
				int i = 0;
				for (final Column column : exported.getColumns()) {
					values[i++] = column.getFormatter().format(row.get(column));
				}
				writer.writeRow(values);
			}
		}

		// CSV → Table(ImportDataCommand と同じ読み込み)→ COPY
		final Table imported = newTable(target);
		imported.setRowIteratorHandler(
				FileRowIteratorFactory.create(file, "UTF-8", 1, 1, null, null, null, (r, c, v) -> v));
		assertEquals(7, new PostgresBulkInsertExecutor(dialect).execute(connection, imported, BulkOption.defaults()));

		final Map<Integer, Map<String, String>> expected = select(source);
		final Map<Integer, Map<String, String>> actual = select(target);
		for (final Integer id : expected.keySet()) {
			for (final Map.Entry<String, String> entry : expected.get(id).entrySet()) {
				if ("line".equals(entry.getKey())) {
					continue; // 2点表現を経由するため係数の表現は変わりうる。下で直線として確認する
				}
				assertEquals(entry.getValue(), actual.get(id).get(entry.getKey()), "id=" + id + " " + entry.getKey());
			}
		}
		assertLineContains(target, 1, 1, 2, 3, 6);
		assertLineContains(target, 2, 0, 0, 1, 1);
		assertLineContains(target, 4, 2, 0, 2, 1);
		assertArrayEquals(new Object[] { null }, new Object[] { actual.get(3).get("line") });
	}
}
