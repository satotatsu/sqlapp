/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.phoenix.sql;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.dialect.phoenix.DialectHolder;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.*;

class PhoenixPagingTest {
	private String sql(Dialect dialect, Object row, Object offset, boolean count) {
		final var table = new Table("Order Details").setSchemaName("Sales");
		table.getColumns().add(new Column("ID").setDataType(DataType.INT));
		final var registry = dialect.createSqlFactoryRegistry();
		registry.getOptions().setDecorateSchemaName(true);
		final var context = new ParametersContext();
		context.put("_row", row);
		context.put("_offset", offset);
		context.put("_countSql", count);
		return registry.createSqlNodes(table, SqlType.SELECT_FOR_APP).get(0).eval(context).getSql().replaceAll("\\s+",
				" ");
	}

	@Test
	void generatesNativePagingAndOmitsItForCountAndUnpagedQueries() {
		for (var dialect : List.of(DialectHolder.defaultDialect4_8, DialectHolder.defaultDialect4_9,
				DialectHolder.defaultDialect5_3, DialectHolder.defaultDialect5_3_1)) {
			final var paged = sql(dialect, 10, 20, false);
			assertTrue(paged.contains("FROM \"Sales\".\"Order Details\""), paged);
			assertTrue(paged.contains("LIMIT ? OFFSET ?"), paged);
			assertTrue(sql(dialect, 0, null, false).contains("LIMIT ?"));
			assertTrue(sql(dialect, null, 2, false).contains("OFFSET ?"));
			assertFalse(sql(dialect, 10, 20, true).contains("LIMIT"));
			assertFalse(sql(dialect, 10, 20, true).contains("OFFSET"));
			assertFalse(sql(dialect, null, null, false).contains("LIMIT"));
		}
	}

	@Test
	void preservesVersionBoundariesAndRejectsInvalidOrUnsupportedPaging() {
		assertFalse(DialectResolver.getInstance().getDialect("Phoenix", 4, 7, 9).supportsLimitOffset());
		assertTrue(DialectResolver.getInstance().getDialect("Phoenix", 4, 8, 0).supportsLimitOffset());
		for (var dialect : List.of(DialectHolder.defaultDialect, DialectHolder.defaultDialect5_3_1)) {
			assertTrue(dialect.supportsLimit());
			assertTrue(dialect.supportsSequence());
			for (Object invalid : List.of(-1, "1.5", "bad", "9223372036854775808")) {
				assertThrows(RuntimeException.class, () -> sql(dialect, invalid, null, false));
				assertThrows(RuntimeException.class, () -> sql(dialect, null, invalid, false));
			}
		}
		assertTrue(sql(DialectHolder.defaultDialect, 10, 0, false).contains("LIMIT ?"));
		assertFalse(sql(DialectHolder.defaultDialect, 10, 0, false).contains("OFFSET"));
		assertThrows(RuntimeException.class, () -> sql(DialectHolder.defaultDialect, 10, 2, false));
	}
}
