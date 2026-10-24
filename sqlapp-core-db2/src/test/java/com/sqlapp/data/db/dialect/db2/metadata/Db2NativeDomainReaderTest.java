/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2.metadata;

import static org.junit.jupiter.api.Assertions.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.db2.*;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.*;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.*;
import com.sqlapp.jdbc.sql.node.SqlNode;

class Db2NativeDomainReaderTest {
	private static ExResultSet row(Map<String, String> values) {
		return new ExResultSet(null) {
			@Override public String getString(String name) { return values.get(name); }
			@Override public Object getObject(String name) { return values.get(name); }
			@Override public Timestamp getTimestamp(String name) { return null; }
			@Override public Long getLongValue(String name) { return values.get(name) == null ? null : Long.valueOf(values.get(name)); }
			@Override public Integer getInteger(String name) { return values.get(name) == null ? null : Integer.valueOf(values.get(name)); }
			@Override public int getInt(String name) { return Integer.parseInt(values.getOrDefault(name, "0")); }
		};
	}
	@Test void twentyTypesUseOneQueryAndPreserveZeroScaleAndWeakTypeConstraints() {
		AtomicInteger count = new AtomicInteger();
		var reader = new Db2DomainReader(DialectHolder.Db2_1110Dialect) {
			@Override protected JdbcQueryHandler execute(Connection c, SqlNode node, ParametersContext context, ResultSetNextHandler handler) {
				count.incrementAndGet(); assertTrue(node.toString().contains("TYPEMODULEID IS NULL")); assertTrue(node.toString().contains("d.OWNERTYPE <> 'S'"));
				for (int i=0; i<20; i++) try {
					var values = new HashMap<String,String>(Map.of("domain_name", "D"+i, "schema_name", "APP", "METATYPE", "T", "NULLS", "N", "sourcename", "DECIMAL", "SOURCENAME", "DECIMAL", "LENGTH", "10", "SCALE", "0", "TYPERULES", "W", "CONSTRAINT_TEXT", "VALUE >= 0"));
					values.put("SOURCESCHEMA", "SYSIBM"); handler.handleResultSetNext(row(values));
				} catch(SQLException e) { throw new IllegalStateException(e); } return null;
			}
		};
		var types = reader.doGetAll(null, new ParametersContext(), new ProductVersionInfo());
		assertEquals(1, count.get()); assertEquals(20, types.size()); assertEquals(0, types.getFirst().getScale());
		assertTrue(types.getFirst().isNotNull()); assertEquals("VALUE >= 0", types.getFirst().getCheck());
		assertEquals("DATA_TYPE", types.getFirst().getSpecifics().get("DB2_OBJECT_TYPE"));
	}
	@Test void associativeIndexLengthIsNotMistakenForCardinalityAndLegacyQueriesStaySeparate() throws Exception {
		var reader = new Db2DomainReader(DialectHolder.Db2_1110Dialect);
		var values = Map.of("domain_name", "A", "schema_name", "APP", "METATYPE", "L", "NULLS", "Y", "ARRAYINDEXTYPELENGTH", "12", "ARRAYINDEXTYPENAME", "VARCHAR", "sourcename", "INTEGER");
		var type = reader.createDomain(row(values));
		assertEquals(1, type.getArrayDimension()); assertEquals(0, type.getArrayDimensionUpperBound());
		assertEquals("12", type.getSpecifics().get("ARRAYINDEXTYPELENGTH"));
		assertFalse(new Db2DomainReader(DialectHolder.Db2_1050Dialect).getSqlNode(new ProductVersionInfo()).toString().contains("TYPEMODULEID"));
	}
}
