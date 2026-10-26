/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.firebird.sql;

import static org.junit.jupiter.api.Assertions.*;
import java.util.LinkedHashSet;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.firebird.DialectHolder;
import com.sqlapp.data.db.sql.*;
import com.sqlapp.data.schemas.*;
import com.sqlapp.util.SqlBuilder;

class FirebirdCapabilitySqlTest {
	@Test
	void capabilitiesNeverAdvertiseUnsupportedTupleOrCascadeSyntax() {
		for (var dialect : List.of(DialectHolder.defaultDialect, DialectHolder.defaultDialect20,
				DialectHolder.defaultDialect25, DialectHolder.defaultDialect30, DialectHolder.defaultDialect40,
				DialectHolder.defaultDialect50)) {
			assertFalse(dialect.supportsDropCascade());
			assertFalse(dialect.supportsRowValueComparison());
			assertFalse(dialect.supportsRowValueComparisonIn());
			String sql = dialect.createSqlFactoryRegistry().createSql(new Table("drop target"), SqlType.DROP).get(0)
					.getSqlText();
			assertEquals("DROP TABLE \"drop target\"", sql);
		}
	}

	@Test
	void withIsConservativeForTheShared20And21Dialect() {
		assertFalse(DialectHolder.defaultDialect.supportsWith());
		assertFalse(DialectHolder.defaultDialect20.supportsWith());
		for (var dialect : List.of(DialectHolder.defaultDialect25, DialectHolder.defaultDialect30,
				DialectHolder.defaultDialect40, DialectHolder.defaultDialect50))
			assertTrue(dialect.supportsWith());
	}

	@Test
	void compositeMembershipUsesTheSharedAndOrFallbackAndStableBindings() {
		Table table = new Table("target");
		table.getColumns().add(new Column("A").setDataType(DataType.INT));
		table.getColumns().add(new Column("B").setDataType(DataType.INT));
		Row first = table.newRow();
		first.put("A", 1);
		first.put("B", 2);
		Row second = table.newRow();
		second.put("A", 3);
		second.put("B", 4);
		var keys = new LinkedHashSet<Column>(table.getColumns());
		var columns = new SqlSignature.ColumnsHolder(keys, List.of(first, second));
		var dialect = DialectHolder.defaultDialect50;
		var builder = new SqlBuilder(dialect);
		var parameters = columns.addInParameters(dialect, List.of(first, second), null, builder);
		assertFalse(builder.toString().contains(" IN "), builder.toString());
		assertTrue(builder.toString().contains("OR"), builder.toString());
		assertTrue(builder.toString().contains("AND"), builder.toString());
		assertEquals(List.of(1, 2, 3, 4), parameters.getBindParameters().stream().map(p -> p.getValue()).toList());
		var greater = new SqlBuilder(dialect);
		var bounds = RowComparisonOperator.GREATER_THAN.addOperator(dialect, columns, first, null, greater);
		assertTrue(greater.toString().contains("OR"), greater.toString());
		assertEquals(List.of(1, 1, 2), bounds.getBindParameters().stream().map(p -> p.getValue()).toList());
	}
}
