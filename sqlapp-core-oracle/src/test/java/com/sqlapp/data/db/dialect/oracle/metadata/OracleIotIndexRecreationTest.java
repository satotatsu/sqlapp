/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.oracle.metadata;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Map;
import java.sql.Connection;
import java.sql.SQLException;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.oracle.DialectHolder;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.*;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.JdbcQueryHandler;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;

class OracleIotIndexRecreationTest {
	private ExResultSet row(Map<String, String> values) {
		return new ExResultSet(null) {
			@Override public String getString(String key) { return values.get(key.toUpperCase(java.util.Locale.ROOT)); }
			@Override public Object getObject(String key) { return getString(key); }
			@Override public int getInt(String key) { String v = getString(key); return v == null ? 0 : Integer.parseInt(v); }
			@Override public boolean wasNull() { return false; }
			@Override public Long getLongValue(String key) { return null; }
		};
	}
	private String create(Table table) { return DialectHolder.oracle26aiDialect.createSqlFactoryRegistry().createSql(table, SqlType.CREATE).get(0).getSqlText().replaceAll("\\s+", " "); }
	private Table readIot(String complex) throws Exception {
		Table table = new OracleTableReader(DialectHolder.oracle26aiDialect).createTable(row(Map.of("OWNER", "APP", "TABLE_NAME", "T", "IOT_TYPE", "IOT", "IOT_COMPLEX", complex, "IOT_TABLESPACE_NAME", "Data Space", "STATUS", "VALID")));
		table.getColumns().add(new Column("ID").setDataType(DataType.INT).setNullable(false));
		UniqueConstraint pk = new UniqueConstraint("PK", true); pk.getColumns().add("ID"); table.getConstraints().add(pk); return table;
	}
	@Test
	void simpleIotPreservesOrganizationRootTablespaceAndXml() throws Exception {
		Table table = SchemaUtils.readXml(new java.io.StringReader(readIot("NO").asXml()));
		String sql = create(table); assertTrue(sql.contains("ORGANIZATION INDEX TABLESPACE \"Data Space\""), sql);
		assertTrue(sql.contains("PRIMARY KEY")); assertFalse(sql.contains("oracle.table.organization"));
	}
	@Test
	void complexIotAndMissingPrimaryKeyFailRatherThanBecomingHeapTables() throws Exception {
		assertThrows(IllegalArgumentException.class, () -> create(readIot("YES")));
		Table table = readIot("NO"); table.getConstraints().clear(); assertThrows(IllegalArgumentException.class, () -> create(table));
	}
	private Index readIndex(String compression, String prefix) {
		var values = Map.of("OWNER", "APP", "INDEX_NAME", "IX", "TABLE_NAME", "T", "COLUMN_NAME", "ID", "INDEX_TYPE", "NORMAL", "COMPRESSION", compression, "PREFIX_LENGTH", prefix);
		var reader = new OracleIndexReader(DialectHolder.oracle26aiDialect) {
			@Override protected JdbcQueryHandler execute(Connection c, SqlNode n, ParametersContext p, ResultSetNextHandler h) {
				try { h.handleResultSetNext(row(values)); } catch (SQLException e) { throw new RuntimeException(e); } return null;
			}
		};
		Index index = reader.doGetAll(null, new ParametersContext(), new ProductVersionInfo()).get(0);
		Table table = new Table("T").setSchemaName("APP");
		table.getColumns().add(new Column("ID").setDataType(DataType.INT));
		table.getIndexes().add(index);
		return index;
	}
	private String createIndex(Index index) { return DialectHolder.oracle26aiDialect.createSqlFactoryRegistry().createSql(index, SqlType.CREATE).get(0).getSqlText(); }
	@Test
	void prefixAndAdvancedCompressionSurviveMetadataAndXml() throws Exception {
		for (String mode : java.util.List.of("ENABLED", "ADVANCED LOW", "ADVANCED HIGH")) {
			Index index = readIndex(mode, "1");
			Table restored = SchemaUtils.readXml(new java.io.StringReader(index.getTable().asXml()));
			String sql = createIndex(restored.getIndexes().get("IX")); assertTrue(sql.endsWith(mode.equals("ENABLED") ? "COMPRESS 1" : "COMPRESS " + mode), sql);
		}
	}
	@Test
	void advancedCompressionVersionAndInvalidPrefixAreRejected() {
		Index index = readIndex("ADVANCED HIGH", "1");
		assertThrows(IllegalArgumentException.class, () -> DialectHolder.oracle12cDialect.createSqlFactoryRegistry().createSql(index, SqlType.CREATE));
		Index invalid = readIndex("ENABLED", "2"); assertThrows(IllegalArgumentException.class, () -> createIndex(invalid));
	}
}
