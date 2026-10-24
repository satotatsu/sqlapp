/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.saphana.sql;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.saphana.DialectHolder;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.*;

class SapHanaPartitionExpressionTest {
	private Table table(String expression, String low, String high) {
		var table = new Table("T");
		table.getColumns().add("OddD", c -> c.setDataType(DataType.TIMESTAMP));
		table.toPartitioning().getPartitioning().setPartitioningType(PartitioningType.Range);
		table.getPartitioning().getPartitioningColumns().add(expression);
		table.getPartitioning().getPartitions().add(new Partition("P").setLowValue(low).setHighValue(high));
		table.getPartitioning().getPartitions().add(new Partition("OTHER"));
		return table;
	}

	private String sql(Table table) {
		return DialectHolder.defaultDialect.createSqlFactoryRegistry().createSql(table, SqlType.CREATE).getFirst()
				.getSqlText();
	}

	@Test
	void rendersDocumentedFunctionsWithTheirBoundaryTypes() {
		for (String function : new String[] { "YEAR", "MONTH" }) {
			String sql = sql(table(function + "(\"OddD\")", "2024", "2025"));
			assertTrue(sql.contains(function + "( \"OddD\")"), sql);
			assertTrue(sql.contains("2024 <= VALUES < 2025"), sql);
		}
		String hour = sql(table("hour ( \"OddD\" )", "2024-01-01 00", "2024-01-02 00"));
		assertTrue(hour.contains("HOUR( \"OddD\")"), hour);
		assertTrue(hour.contains("'2024-01-01 00' <= VALUES < '2024-01-02 00'"), hour);
	}

	@Test
	void rendersHashAndHeterogeneousExpressionKeys() {
		var table = table("YEAR(\"OddD\")", "2024", "2025");
		table.getSpecifics().put("LOAD_UNIT", "PAGE");
		table.getPartitioning().getPartitions().getFirst().getSpecifics().put("LOAD_UNIT", "COLUMN");
		assertTrue(sql(table).contains("YEAR( \"OddD\")"));
		table.getPartitioning().setPartitioningType(PartitioningType.Hash).setPartitionSize(2);
		table.getPartitioning().getPartitions().clear();
		table.getPartitioning().setPartitionSize(2);
		assertTrue(sql(table).contains("HASH (YEAR( \"OddD\")) PARTITIONS 2"));
	}

	@Test
	void rejectsUnknownFunctionsCompoundExpressionsAndNonDateColumns() {
		for (String expression : new String[] { "DAY(\"OddD\")", "YEAR(\"OddD\")+1", "YEAR(\"OddD\"); DROP TABLE T",
				"YEAR(MISSING)" })
			assertThrows(IllegalArgumentException.class, () -> sql(table(expression, "1", "2")), expression);
		var table = table("YEAR(\"OddD\")", "1", "2");
		table.getColumns().getFirst().setDataType(DataType.INT);
		assertThrows(IllegalArgumentException.class, () -> sql(table));
		table.getColumns().getFirst().setDataType(DataType.TIME);
		assertThrows(IllegalArgumentException.class, () -> sql(table));
	}

	@Test
	void rejectsFunctionLikeLiteralIdentifiers() {
		var table = table("\"YEAR(OddD)\"", "1", "2");
		table.getColumns().add("YEAR(OddD)", c -> c.setDataType(DataType.INT));
		assertThrows(IllegalArgumentException.class, () -> sql(table));
		var literal = table("YEAR(D)", "1", "2");
		literal.getColumns().add("YEAR(D)", c -> c.setDataType(DataType.INT));
		assertThrows(IllegalArgumentException.class, () -> sql(literal));
		var ambiguous = table("YEAR(D)", "1", "2");
		ambiguous.getColumns().add("D", c -> c.setDataType(DataType.DATE));
		ambiguous.getColumns().add("YEAR(D)", c -> c.setDataType(DataType.INT));
		assertThrows(IllegalArgumentException.class, () -> sql(ambiguous));
	}

	@Test
	void rejectsWhitespaceFunctionArgumentsAndUsesFixedTimestampSyntax() {
		var table = table("YEAR(\"Odd D\")", "2024", "2025");
		table.getColumns().getFirst().setName("Odd D");
		assertTrue(assertThrows(IllegalArgumentException.class, () -> sql(table)).getMessage().contains("whitespace"));
		for (var dialect : java.util.List.of(DialectHolder.defaultDialect, DialectHolder.cloudDialect)) {
			var timestamp = new Column("D").setDataType(DataType.TIMESTAMP).setLength(27);
			String definition = dialect.createSqlBuilder().definition(timestamp).toString();
			assertTrue(definition.contains("TIMESTAMP"), definition);
			assertFalse(definition.contains("TIMESTAMP("), definition);
			timestamp.setLength(3);
			assertThrows(IllegalArgumentException.class, () -> dialect.createSqlBuilder().definition(timestamp));
		}
	}

	@Test
	void normalizesAlignedCatalogTimestampBoundsForEachFunction() {
		String year = sql(table("YEAR(\"OddD\")", "2024-01-01 00:00:00.0000000", "2025-01-01 00:00:00.0000000"));
		assertTrue(year.contains("2024 <= VALUES < 2025"), year);
		String month = sql(table("MONTH(\"OddD\")", "2024-01-01 00:00:00.0000000", "2025-01-01 00:00:00.0000000"));
		assertTrue(month.contains("202401 <= VALUES < 202501"), month);
		String hour = sql(table("HOUR(\"OddD\")", "2024-01-01 00:00:00.0000000", "2024-01-01 01:00:00.0000000"));
		assertTrue(hour.contains("'2024010100' <= VALUES < '2024010101'"), hour);
		assertThrows(IllegalArgumentException.class,
				() -> sql(table("YEAR(\"OddD\")", "2024-02-01 00:00:00.0000000", "2025")));
	}
}
