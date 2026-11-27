/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.mysql.metadata;

import static org.junit.jupiter.api.Assertions.*;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Types;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.sql.rowset.CachedRowSet;
import javax.sql.rowset.RowSetMetaDataImpl;
import javax.sql.rowset.RowSetProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.dialect.mysql.MySqlGeometryJdbcTypeHandler;
import com.sqlapp.data.db.dialect.mysql.util.MySqlSpatialReference;
import com.sqlapp.data.db.dialect.mysql.util.MySqlSqlBuilder;
import com.sqlapp.data.db.sql.SqlFactory;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.JdbcQueryHandler;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;

class MySqlSpatialColumnTest {
	@ParameterizedTest
	@CsvSource({ "5,5", "5,7", "8,0", "8,4", "9,0" })
	void spatialSubtypesRetainNamesAndTheGeometryJdbcHandler(int major, int minor) {
		var dialect = dialect(major, minor);
		assertEquals("GEOMETRY", dialect.getDbDataTypes().getDbType(DataType.GEOMETRY).getTypeName());
		for (String type : List.of("GEOMETRY", "POINT", "LINESTRING", "POLYGON", "MULTIPOINT", "MULTILINESTRING",
				"MULTIPOLYGON", "GEOMETRYCOLLECTION", "GEOMCOLLECTION")) {
			Column column = new Column("location");
			dialect.setDbType(type.toLowerCase(java.util.Locale.ROOT), null, null, column);
			assertEquals(DataType.GEOMETRY, column.getDataType(), type);
			assertInstanceOf(MySqlGeometryJdbcTypeHandler.class, dialect.getDbDataType(column).getJdbcTypeHandler());
			String sql = create(dialect, table(dialect, column));
			assertTrue(sql.contains("location " + ("GEOMCOLLECTION".equals(type) ? "GEOMETRYCOLLECTION" : type)), sql);
		}
	}

	@ParameterizedTest
	@CsvSource({ "8,0,2,false", "8,0,3,true", "8,4,0,true", "9,0,0,true" })
	void metadataUsesActualVersionAndOneBatch(int major, int minor, int revision, boolean supported) {
		var dialect = dialect(8, 0);
		int[] queries = { 0 };
		var reader = new MySqlColumn800Reader(dialect) {
			@Override
			protected JdbcQueryHandler execute(Connection connection, SqlNode node, ParametersContext context,
					ResultSetNextHandler handler) {
				queries[0]++;
				assertTrue(node.toString().contains("information_schema.columns"));
				for (int i = 0; i < 12; i++) {
					try (var rows = rows("POINT", "4326")) {
						handler.handleResultSetNext(new ExResultSet(rows) {
							@Override
							public String getString(String name) throws SQLException {
								if ("SRS_ID".equals(name))
									assertTrue(supported, "Old servers must not access SRS_ID");
								return super.getString(name);
							}
						});
					} catch (SQLException e) {
						throw new IllegalStateException(e);
					}
				}
				return null;
			}
		};
		var columns = reader.doGetAll(null, new ParametersContext(), version(major, minor, revision));
		assertEquals(12, columns.size());
		assertEquals(1, queries[0]);
		for (Column column : columns) {
			assertEquals(supported ? "4326" : null, column.getSpecifics().get(MySqlSpatialReference.SRID));
			assertEquals(supported, create(dialect, table(dialect, column)).contains("SRID 4326"));
		}
	}

	@Test
	void nullZeroAndMaximumSridAndGeneratedColumnsRecreate() throws Exception {
		var dialect = dialect(8, 4);
		for (String value : new String[] { null, "0", "4326", "4294967295" }) {
			try (var rows = rows("GEOMETRY", value)) {
				Column column = new MySqlColumn800Reader(dialect).createColumn(null, new ExResultSet(rows),
						version(8, 4, 0));
				assertEquals(value, column.getSpecifics().get(MySqlSpatialReference.SRID));
				Table table = table(dialect, column);
				String sql = create(dialect, table);
				assertEquals(value != null, sql.contains("SRID "), sql);
				if (value != null)
					assertTrue(sql.contains("SRID " + value), sql);
				String alterDefinition = new MySqlSqlBuilder(dialect).definitionForAlterColumn(column).toString();
				assertEquals(value != null, alterDefinition.contains("SRID "), alterDefinition);
				column.setFormula("ST_GeomFromText('POINT(1 2)', 4326)");
				column.setFormulaPersisted(true);
				String generated = create(dialect, table.clone());
				assertTrue(generated.contains("GENERATED ALWAYS AS (ST_GeomFromText('POINT(1 2)', 4326)) STORED"),
						generated);
				assertEquals(value != null, generated.contains("SRID "), generated);
			}
		}
	}

	@Test
	void routineSpatialTypesUseTheSameSubtypeNames() {
		var dialect = dialect(8, 4);
		var function = new com.sqlapp.data.schemas.Function("f");
		function.setDialect(dialect);
		dialect.setDbType("POINT", null, null, function.getReturning());
		var argument = new com.sqlapp.data.schemas.NamedArgument("shape");
		dialect.setDbType("MULTIPOLYGON", null, null, argument);
		function.getArguments().add(argument);
		function.setStatement("RETURN ST_Centroid(shape)");
		SqlFactory<com.sqlapp.data.schemas.Function> factory = dialect.createSqlFactoryRegistry()
				.getSqlFactory(function, SqlType.CREATE);
		String sql = factory.createSql(function).get(0).getSqlText();
		assertTrue(sql.contains("shape MULTIPOLYGON"), sql);
		assertTrue(sql.contains("RETURNS POINT"), sql);
	}

	@Test
	void alteringOnlySridChangesAndRemovingItRegeneratesTheWholeColumn() {
		var dialect = dialect(8, 4);
		Column column = new Column("location");
		dialect.setDbType("POINT", null, null, column);
		column.getSpecifics().put(MySqlSpatialReference.SRID, "4326");
		Table original = table(dialect, column);
		Table target = original.clone();
		target.getColumns().get(0).getSpecifics().put(MySqlSpatialReference.SRID, "0");
		SqlFactory<Table> alter = dialect.createSqlFactoryRegistry().getSqlFactory(target, SqlType.ALTER);
		String sql = alter.createDiffSql(original.diff(target)).get(0).getSqlText();
		assertTrue(sql.contains("MODIFY location POINT"), sql);
		assertTrue(sql.contains("SRID 0"), sql);
		target.getColumns().get(0).getSpecifics().remove(MySqlSpatialReference.SRID);
		sql = alter.createDiffSql(original.diff(target)).get(0).getSqlText();
		assertTrue(sql.contains("MODIFY location POINT"), sql);
		assertFalse(sql.contains("SRID"), sql);
	}

	@Test
	void rejectsInvalidNumbersNonSpatialColumnsAndOlderDialects() {
		var dialect = dialect(8, 4);
		Column column = new Column("location").setDataType(DataType.GEOMETRY);
		for (String value : List.of("-1", "4294967296", "", "1.5", "4326; SELECT 1", "999999999999999999999")) {
			column.getSpecifics().put(MySqlSpatialReference.SRID, value);
			assertThrows(IllegalArgumentException.class, () -> new MySqlSqlBuilder(dialect).definition(column));
		}
		column.getSpecifics().put(MySqlSpatialReference.SRID, "4326");
		assertThrows(IllegalArgumentException.class, () -> new MySqlSqlBuilder(dialect(5, 7)).definition(column));
		column.setDataType(DataType.INT);
		assertThrows(IllegalArgumentException.class, () -> new MySqlSqlBuilder(dialect).definition(column));
	}

	private Dialect dialect(int major, int minor) {
		return DialectResolver.getInstance().getDialect("MySql", major, minor, 0);
	}

	private ProductVersionInfo version(int major, int minor, int revision) {
		return new ProductVersionInfo().setMajorVersion(major).setMinorVersion(minor).setRevision(revision);
	}

	private Table table(Dialect dialect, Column column) {
		Table table = new Table("places");
		table.setDialect(dialect);
		table.setSchemaName("app");
		table.getColumns().add(column);
		return table;
	}

	private String create(Dialect dialect, Table table) {
		SqlFactory<Table> factory = dialect.createSqlFactoryRegistry().getSqlFactory(table, SqlType.CREATE);
		return factory.createSql(table).get(0).getSqlText();
	}

	private CachedRowSet rows(String type, String srid) throws SQLException {
		Map<String, Object> values = new LinkedHashMap<>();
		String[] pairs = { "COLUMN_NAME", "location", "TABLE_CATALOG", "def", "TABLE_SCHEMA", "app", "TABLE_NAME",
				"places", "DATA_TYPE", type, "COLUMN_TYPE", type.toLowerCase(java.util.Locale.ROOT), "EXTRA", "",
				"IS_NULLABLE", "YES", "COLUMN_COMMENT", "" };
		for (int i = 0; i < pairs.length; i += 2)
			values.put(pairs[i], pairs[i + 1]);
		values.put("SRS_ID", srid);
		RowSetMetaDataImpl metadata = new RowSetMetaDataImpl();
		metadata.setColumnCount(values.size());
		int i = 0;
		for (String key : values.keySet()) {
			metadata.setColumnName(++i, key);
			metadata.setColumnType(i, Types.VARCHAR);
		}
		CachedRowSet rows = RowSetProvider.newFactory().createCachedRowSet();
		rows.setMetaData(metadata);
		rows.moveToInsertRow();
		i = 0;
		for (Object value : values.values())
			rows.updateObject(++i, value);
		rows.insertRow();
		rows.moveToCurrentRow();
		rows.beforeFirst();
		rows.next();
		return rows;
	}
}
