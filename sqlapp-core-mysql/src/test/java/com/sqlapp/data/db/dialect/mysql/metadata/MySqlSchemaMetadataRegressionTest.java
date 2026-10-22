/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.mysql.metadata;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Types;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
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
import com.sqlapp.data.db.sql.SqlFactory;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.View;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.JdbcQueryHandler;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;
import com.sqlapp.jdbc.sql.SqlParameterCollection;

class MySqlSchemaMetadataRegressionTest {

	@Test
	void columnPrivilegesAreFilteredBySchema() {
		MySqlColumnPrivilegeReader reader = new MySqlColumnPrivilegeReader(dialect(8, 4, 0));
		ParametersContext context = new ParametersContext();
		context.put("schemaName", "only_app");
		SqlParameterCollection parameters = new SqlParameterCollection();
		reader.getSqlSqlNode(version(8, 4, 0)).eval(context, parameters);
		assertTrue(parameters.getSql().contains("table_schema IN"), parameters.getSql());
	}

	@Test
	void expressionDefaultsAndTimestampAttributesRemainExecutable() throws Exception {
		Dialect dialect = dialect(8, 4, 0);
		var expression = values("COLUMN_NAME", "token", "TABLE_CATALOG", "def", "TABLE_SCHEMA", "app", "TABLE_NAME",
				"orders", "DATA_TYPE", "varchar", "COLUMN_TYPE", "varchar(40)", "CHARACTER_MAXIMUM_LENGTH", 40L,
				"EXTRA", "DEFAULT_GENERATED", "COLUMN_DEFAULT", "uuid()", "IS_NULLABLE", "YES");
		try (var rows = rows(List.of(expression))) {
			rows.next();
			Column column = new MySqlColumn800Reader(dialect).createColumn(null, new ExResultSet(rows));
			assertEquals("(uuid())", column.getDefaultValue());
			Table table = table(dialect);
			table.getColumns().add(column);
			assertTrue(create(dialect, table).contains("DEFAULT (uuid())"));
		}
		var timestamp = values("COLUMN_NAME", "modified", "TABLE_CATALOG", "def", "TABLE_SCHEMA", "app", "TABLE_NAME",
				"orders", "DATA_TYPE", "datetime", "COLUMN_TYPE", "datetime(6)", "DATETIME_PRECISION", 6L, "EXTRA",
				"DEFAULT_GENERATED on update CURRENT_TIMESTAMP(6) INVISIBLE", "COLUMN_DEFAULT", "CURRENT_TIMESTAMP(6)",
				"IS_NULLABLE", "YES");
		try (var rows = rows(List.of(timestamp))) {
			rows.next();
			Column column = new MySqlColumn800Reader(dialect).createColumn(null, new ExResultSet(rows));
			assertEquals("CURRENT_TIMESTAMP(6)", column.getDefaultValue());
			assertEquals("CURRENT_TIMESTAMP(6)", column.getOnUpdate());
			Table table = table(dialect);
			table.getColumns().add(column);
			String sql = create(dialect, table);
			assertTrue(sql.contains("DEFAULT CURRENT_TIMESTAMP(6)"), sql);
			assertTrue(sql.contains("ON UPDATE CURRENT_TIMESTAMP(6)"), sql);
		}
		// A literal with function-like text remains a literal without
		// DEFAULT_GENERATED.
		expression.put("EXTRA", null);
		try (var rows = rows(List.of(expression))) {
			rows.next();
			Column column = new MySqlColumn800Reader(dialect).createColumn(null, new ExResultSet(rows));
			assertEquals("'uuid()'", column.getDefaultValue());
		}
	}

	@ParameterizedTest
	@CsvSource({ "5,5,3", "5,6,4", "5,7,9", "8,0,15", "8,0,16", "8,4,0", "9,0,0" })
	void viewsRetainSecurityChecksAndOutputNames(int major, int minor, int revision) throws Exception {
		Dialect dialect = dialect(major, minor, revision);
		MySqlViewReader reader = (MySqlViewReader) dialect.getCatalogReader().getSchemaReader().getViewReader();
		assertEquals(dialect.getCatalogReader().getSchemaReader().getTableReader().getColumnReader().getClass(),
				reader.getColumnReader().getClass());
		for (String check : new String[] { "NONE", "LOCAL", "CASCADED" }) {
			try (var rows = rows(List.of(values("TABLE_NAME", "order view", "TABLE_SCHEMA", "app", "TABLE_CATALOG",
					"def", "IS_UPDATABLE", "YES", "VIEW_DEFINITION", "SELECT id FROM orders", "CHECK_OPTION", check,
					"SECURITY_TYPE", "INVOKER")))) {
				rows.next();
				View view = (View) reader.createTable(new ExResultSet(rows));
				view.getColumns().add("order id", column -> column.setDataType(DataType.INT));
				String sql = create(dialect, view);
				assertTrue(sql.contains("SQL SECURITY INVOKER VIEW"), sql);
				assertTrue(sql.contains("`order view` ( `order id`)"), sql);
				assertTrue(sql.contains("SELECT id FROM orders"), sql);
				assertEquals(!"NONE".equals(check), sql.contains("CHECK OPTION"), sql);
				if (!"NONE".equals(check)) {
					assertTrue(sql.contains("WITH " + check + " CHECK OPTION"), sql);
				}
				assertFalse(view.getReadonly());
			}
		}
		try (var rows = rows(List.of(values("TABLE_NAME", "v", "TABLE_SCHEMA", "app", "TABLE_CATALOG", "def",
				"VIEW_DEFINITION", "SELECT 1", "IS_UPDATABLE", null)))) {
			rows.next();
			assertNotNull(reader.createTable(new ExResultSet(rows)));
		}
	}

	@Test
	void rejectsInvalidViewOptionsAndHonorsCompleteDefinition() {
		Dialect dialect = dialect(8, 4, 0);
		View view = new View("v");
		view.setDialect(dialect);
		view.setStatement("SELECT 1");
		view.getSpecifics().put("security_type", "owner");
		assertThrows(IllegalArgumentException.class, () -> create(dialect, view));
		view.getSpecifics().clear();
		view.getSpecifics().put("check_option", "typo");
		assertThrows(IllegalArgumentException.class, () -> create(dialect, view));
		view.setDefinition("CREATE VIEW v AS SELECT 2");
		assertEquals("CREATE VIEW v AS SELECT 2", create(dialect, view));
	}

	@ParameterizedTest
	@CsvSource({ "5,7,9,0", "8,0,0,0", "8,0,15,0", "8,0,16,1", "8,4,0,1", "9,0,0,1" })
	void checkCatalogIsOnlyReadFrom8016(int major, int minor, int revision, int expectedQueries) {
		int[] queries = { 0 };
		MySqlCheckConstraintReader reader = new MySqlCheckConstraintReader(dialect(major, minor, revision)) {
			@Override
			protected JdbcQueryHandler execute(Connection connection, SqlNode sql, ParametersContext context,
					ResultSetNextHandler handler) {
				queries[0]++;
				assertTrue(sql.toString().contains("information_schema.CHECK_CONSTRAINTS"));
				return null;
			}
		};
		assertTrue(reader.doGetAll(null, new ParametersContext(), version(major, minor, revision)).isEmpty());
		assertEquals(expectedQueries, queries[0]);
	}

	@Test
	void readsCompositeUniquePrefixAndDirectionInOneQuery() throws Exception {
		Dialect dialect = dialect(8, 4, 0);
		int[] queries = { 0 };
		var first = values("constraint_catalog", "def", "constraint_schema", "app", "TABLE_NAME", "orders",
				"CONSTRAINT_NAME", "uq_code", "constraint_type", "UNIQUE", "COLUMN_NAME", "code", "SUB_PART", 8L,
				"ASC_OR_DESC", "D", "INDEX_TYPE", "BTREE", "INDEX_COMMENT", "unique prefix", "IS_VISIBLE", "NO");
		var second = new LinkedHashMap<>(first);
		second.put("COLUMN_NAME", "id");
		second.put("SUB_PART", null);
		second.put("ASC_OR_DESC", "A");
		MySqlUniqueConstraintReader reader = new MySqlUniqueConstraintReader(dialect) {
			@Override
			protected JdbcQueryHandler execute(Connection connection, SqlNode sql, ParametersContext context,
					ResultSetNextHandler handler) {
				queries[0]++;
				assertTrue(sql.toString().contains("expression_part.COLUMN_NAME IS NULL"));
				feed(handler, List.of(first, second));
				return null;
			}
		};
		var constraints = reader.doGetAll(null, new ParametersContext(), version(8, 4, 0));
		assertEquals(1, queries[0]);
		assertEquals(1, constraints.size());
		var unique = constraints.get(0);
		assertEquals(2, unique.getColumns().size());
		assertEquals(8L, unique.getColumns().get(0).getLength());
		Table table = table(dialect);
		table.getConstraints().add(unique);
		String sql = create(dialect, table);
		assertTrue(sql.contains("code(8) DESC"), sql);
		assertTrue(sql.contains("COMMENT 'unique prefix'"), sql);
		assertTrue(sql.contains("INVISIBLE"), sql);
	}

	@Test
	void crossSchemaCompositeForeignKeyUsesReferencedOwnerInOneQuery() {
		Dialect dialect = dialect(8, 4, 0);
		int[] queries = { 0 };
		var first = values("constraint_catalog", "def", "constraint_schema", "app", "TABLE_NAME", "orders",
				"CONSTRAINT_NAME", "fk_parent", "COLUMN_NAME", "id", "referenced_table_catalog", "def",
				"referenced_table_schema", "other app", "referenced_table_name", "parents", "referenced_column_name",
				"id", "MATCH_OPTION", "NONE", "UPDATE_RULE", "CASCADE", "DELETE_RULE", "RESTRICT");
		var second = new LinkedHashMap<>(first);
		second.put("COLUMN_NAME", "code");
		second.put("referenced_column_name", "code");
		MySqlForeignKeyConstraintReader reader = new MySqlForeignKeyConstraintReader(dialect) {
			@Override
			protected JdbcQueryHandler execute(Connection connection, SqlNode sql, ParametersContext context,
					ResultSetNextHandler handler) {
				queries[0]++;
				assertTrue(sql.toString().contains("kc.referenced_table_schema"));
				feed(handler, List.of(first, second));
				return null;
			}
		};
		var keys = reader.doGetAll(null, new ParametersContext(), version(8, 4, 0));
		assertEquals(1, queries[0]);
		assertEquals(2, keys.get(0).getRelatedColumns().size());
		assertEquals("other app", keys.get(0).getRelatedColumns().get(0).getSchemaName());
		Table table = table(dialect);
		table.getConstraints().add(keys.get(0));
		String sql = create(dialect, table);
		assertTrue(sql.contains("REFERENCES `other app`.parents"), sql);
		assertTrue(sql.contains("ON UPDATE CASCADE"), sql);
	}

	@Test
	void generatedColumnMetadataSurvivesTurkishLocaleAndRecreates() throws Exception {
		Dialect dialect = dialect(8, 4, 0);
		Locale previous = Locale.getDefault();
		try {
			Locale.setDefault(Locale.forLanguageTag("tr-TR"));
			for (boolean stored : new boolean[] { false, true }) {
				var metadata = values("COLUMN_NAME", "calculated", "TABLE_CATALOG", "def", "TABLE_SCHEMA", "app",
						"TABLE_NAME", "orders", "DATA_TYPE", "int", "COLUMN_TYPE", "int", "EXTRA",
						stored ? "stored generated invisible" : "virtual generated invisible", "GENERATION_EXPRESSION",
						"(`id` + 1)", "IS_NULLABLE", "YES", "COLUMN_COMMENT", "generated");
				try (var rows = rows(List.of(metadata))) {
					rows.next();
					Column column = new MySqlColumn800Reader(dialect).createColumn(null, new ExResultSet(rows));
					assertEquals(stored, column.isFormulaPersisted());
					assertTrue(column.isHidden());
					Table table = table(dialect);
					table.getColumns().add(column);
					String sql = create(dialect, table);
					assertTrue(sql.contains("GENERATED ALWAYS AS ((`id` + 1)) " + (stored ? "STORED" : "VIRTUAL")),
							sql);
					assertTrue(sql.contains("INVISIBLE"), sql);
				}
			}
		} finally {
			Locale.setDefault(previous);
		}
	}

	private static Table table(Dialect dialect) {
		Table table = new Table("orders");
		table.setDialect(dialect);
		table.setSchemaName("app");
		table.getColumns().add("id", c -> c.setDataType(DataType.INT));
		table.getColumns().add("code", c -> c.setDataType(DataType.VARCHAR).setLength(30));
		return table;
	}

	private static String create(Dialect dialect, Table object) {
		SqlFactory<Table> factory = dialect.createSqlFactoryRegistry().getSqlFactory(object, SqlType.CREATE);
		return factory.createSql(object).get(0).getSqlText();
	}

	private static Dialect dialect(int major, int minor, int revision) {
		return DialectResolver.getInstance().getDialect("MySql", major, minor, revision);
	}

	private static ProductVersionInfo version(int major, int minor, int revision) {
		return new ProductVersionInfo().setMajorVersion(major).setMinorVersion(minor).setRevision(revision);
	}

	private static Map<String, Object> values(Object... pairs) {
		Map<String, Object> map = new LinkedHashMap<>();
		for (int i = 0; i < pairs.length; i += 2) {
			map.put((String) pairs[i], pairs[i + 1]);
		}
		return map;
	}

	private static void feed(ResultSetNextHandler handler, List<Map<String, Object>> values) {
		try (var rows = rows(values)) {
			ExResultSet result = new ExResultSet(rows);
			while (rows.next()) {
				handler.handleResultSetNext(result);
			}
		} catch (SQLException e) {
			throw new IllegalStateException(e);
		}
	}

	private static CachedRowSet rows(List<Map<String, Object>> values) throws SQLException {
		RowSetMetaDataImpl metadata = new RowSetMetaDataImpl();
		var first = values.get(0);
		metadata.setColumnCount(first.size());
		int index = 0;
		for (var entry : first.entrySet()) {
			metadata.setColumnName(++index, entry.getKey());
			metadata.setColumnLabel(index, entry.getKey());
			metadata.setColumnType(index, entry.getValue() instanceof Number ? Types.BIGINT : Types.VARCHAR);
		}
		CachedRowSet rows = RowSetProvider.newFactory().createCachedRowSet();
		rows.setMetaData(metadata);
		// CachedRowSet inserts before the current row, so preserve the catalog ordering
		// explicitly.
		for (int i = values.size() - 1; i >= 0; i--) {
			rows.moveToInsertRow();
			index = 0;
			for (Object value : values.get(i).values()) {
				rows.updateObject(++index, value);
			}
			rows.insertRow();
			rows.moveToCurrentRow();
		}
		rows.beforeFirst();
		return rows;
	}
}
