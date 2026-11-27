/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2.metadata;

import static org.junit.jupiter.api.Assertions.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.db2.DialectHolder;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.sql.*;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.*;
import com.sqlapp.jdbc.sql.*;
import com.sqlapp.jdbc.sql.node.SqlNode;

class Db2TablePhysicalOptionsTest {
	private Map<String, String> fields() {
		return new HashMap<>(
				Map.of("table_name", "T", "schema_name", "APP", "TYPE", "T", "TABLEORG", "R", "COMPRESSION", "B",
						"ROWCOMPMODE", "A", "PCTFREE", "15", "APPEND_MODE", "Y", "LOCKSIZE", "T", "VOLATILE", "C"));
	}

	private Table read(Map<String, String> values) throws SQLException {
		return new Db2_1010TableReader(DialectHolder.Db2_1110Dialect).createTable(row(values));
	}

	private com.sqlapp.jdbc.ExResultSet row(Map<String, String> values) {
		return new com.sqlapp.jdbc.ExResultSet(null) {
			@Override
			public String getString(String key) {
				return values.get(key);
			}

			@Override
			public Object getObject(String key) {
				return values.get(key);
			}

			@Override
			public java.sql.Timestamp getTimestamp(String key) {
				return null;
			}

			@Override
			public boolean contains(String key) {
				return false;
			}
		};
	}

	private List<String> create(Table table) throws Exception {
		if (table.getColumns().isEmpty())
			table.getColumns().add("ID", c -> c.setDataType(DataType.INT));
		Table restored = SchemaUtils.readXml(new java.io.StringReader(table.asXml()));
		SqlFactory<Table> factory = DialectHolder.Db2_1110Dialect.createSqlFactoryRegistry().getSqlFactory(restored,
				SqlType.CREATE);
		return factory.createSql(restored).stream().map(o -> o.getSqlText().replaceAll("\\s+", " ")).toList();
	}

	@Test
	void readerXmlAndCreateRetainCompressionAndSeparatePostCreateOptions() throws Exception {
		Table table = read(fields());
		assertTrue(table.isCompression());
		assertEquals("ROW ADAPTIVE VALUE", table.getCompressionType());
		table.setTableSpaceName("Data Space");
		table.setIndexTableSpaceName("IDX");
		table.setLobTableSpaceName("LOB");
		var sql = create(table);
		assertEquals(5, sql.size());
		assertTrue(sql.getFirst().contains("IN \"Data Space\" INDEX IN IDX LONG IN LOB"), sql.toString());
		assertTrue(sql.getFirst().contains("ORGANIZE BY ROW COMPRESS YES ADAPTIVE VALUE COMPRESSION"), sql.toString());
		assertEquals(1, sql.getFirst().split("COMPRESS YES", -1).length - 1);
		assertEquals("ALTER TABLE APP.T PCTFREE 15", sql.get(1));
		assertEquals("ALTER TABLE APP.T APPEND ON", sql.get(2));
		assertEquals("ALTER TABLE APP.T LOCKSIZE TABLE", sql.get(3));
		assertEquals("ALTER TABLE APP.T VOLATILE CARDINALITY", sql.get(4));
	}

	@Test
	void allCompressionKindsAndDisabledStateAreExplicit() throws Exception {
		for (var entry : Map.of("N", "COMPRESS NO", "V", "COMPRESS NO VALUE COMPRESSION", "R", "COMPRESS YES STATIC",
				"B", "COMPRESS YES STATIC VALUE COMPRESSION").entrySet()) {
			var fields = fields();
			fields.put("COMPRESSION", entry.getKey());
			fields.put("ROWCOMPMODE", "S");
			assertTrue(create(read(fields)).getFirst().contains(entry.getValue()));
		}
		Table table = read(fields());
		table.setCompression(false);
		assertTrue(create(table).getFirst().endsWith("COMPRESS NO"));
		assertTrue(create(new Table("SIMPLE").setCompression(true)).getFirst().contains("COMPRESS YES ADAPTIVE"));
		assertTrue(create(new Table("SIMPLE").setCompression(true).setCompressionType("row static")).getFirst()
				.contains("COMPRESS YES STATIC"));
	}

	@Test
	void physicalOptionsPrecedeIndexesAndCommentsAndFullDefinitionsOwnTheirOptions() throws Exception {
		Table table = read(fields());
		table.getColumns().add("ID", c -> c.setDataType(DataType.INT));
		table.getIndexes().add("IX", table.getColumns().get("ID"));
		table.setRemarks("remark");
		var sql = create(table);
		assertTrue(sql.get(4).contains("VOLATILE"));
		assertTrue(sql.get(5).startsWith("CREATE INDEX"));
		assertTrue(sql.get(6).startsWith("COMMENT ON TABLE"));
		table.setDefinition("CREATE TABLE APP.T (ID INTEGER) ORGANIZE BY COLUMN");
		table.getSpecifics().put("TABLEORG", "C");
		sql = create(table);
		assertEquals(3, sql.size());
		assertFalse(sql.stream().anyMatch(s -> s.startsWith("ALTER")));
	}

	@Test
	void unsupportedOrganizationsLockModesAndMalformedOptionsFailClearly() throws Exception {
		for (var entry : Map.of("TABLEORG", "C", "CLUSTERED", "Y", "LOCKSIZE", "I", "PCTFREE", "100", "APPEND_MODE",
				"?", "VOLATILE", "?").entrySet()) {
			var fields = fields();
			fields.put(entry.getKey(), entry.getValue());
			assertThrows(IllegalArgumentException.class, () -> create(read(fields)));
		}
		var fields = fields();
		fields.remove("ROWCOMPMODE");
		assertThrows(IllegalArgumentException.class, () -> create(read(fields)));
		fields.put("ROWCOMPMODE", "A");
		fields.put("PCTFREE", "-1");
		assertFalse(create(read(fields)).stream().anyMatch(s -> s.contains("PCTFREE")));
	}

	@Test
	void twentyTablesUseOneHeaderQueryAndLegacyRepresentationIsUnchanged() throws Exception {
		AtomicInteger queries = new AtomicInteger();
		var reader = new Db2TableReader(DialectHolder.Db2_1110Dialect) {
			@Override
			protected JdbcQueryHandler execute(Connection c, SqlNode sql, ParametersContext p,
					ResultSetNextHandler handler) {
				queries.incrementAndGet();
				for (int i = 0; i < 20; i++)
					try {
						var values = fields();
						values.put("table_name", "T" + i);
						handler.handleResultSetNext(row(values));
					} catch (SQLException e) {
						throw new IllegalStateException(e);
					}
				return null;
			}
		};
		var tables = reader.doGetAll(null, new ParametersContext(), new ProductVersionInfo());
		assertEquals(1, queries.get());
		assertEquals(20, tables.size());
		assertTrue(tables.stream().allMatch(t -> "ROW ADAPTIVE VALUE".equals(t.getCompressionType())));
		var old = new Db2TableReader(DialectHolder.Db2_1050Dialect).createTable(row(fields()));
		assertFalse(old.getSpecifics().containsKey("TABLEORG"));
	}
}
