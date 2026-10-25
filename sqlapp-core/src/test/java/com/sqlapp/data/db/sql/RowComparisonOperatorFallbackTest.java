/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.sql;

import static org.junit.jupiter.api.Assertions.*;
import java.util.LinkedHashSet;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.schemas.*;
import com.sqlapp.util.SqlBuilder;

class RowComparisonOperatorFallbackTest {
	private final Dialect dialect = new Dialect(() -> null) { };
	private Table table() {
		Table table = new Table("target");
		for (String name : List.of("a key", "b key", "c key")) table.getColumns().add(new Column(name).setDataType(DataType.INT));
		return table;
	}
	private Row row(Table table) {
		Row row = table.newRow(); row.put("a key", 1); row.put("b key", 2); row.put("c key", 3); return row;
	}
	@Test void inclusiveComparisonsAreStrictUntilTheLastComponent() {
		for (var operator : List.of(RowComparisonOperator.GREATER_THAN_OR_EQUAL, RowComparisonOperator.LESS_THAN_OR_EQUAL)) {
			Table table = table(); Row row = row(table);
			var holder = new SqlSignature.ColumnsHolder(new LinkedHashSet<>(table.getColumns()), List.of(row));
			var sql = new SqlBuilder(dialect);
			var parameters = operator.addOperator(dialect, holder, row, null, sql);
			String strict = operator == RowComparisonOperator.GREATER_THAN_OR_EQUAL ? ">" : "<";
			assertTrue(sql.toString().contains("\"a key\" " + strict + " ?"), sql.toString());
			assertTrue(sql.toString().contains("\"b key\" " + strict + " ?"), sql.toString());
			assertTrue(sql.toString().contains("\"c key\" " + strict + "= ?"), sql.toString());
			assertEquals(1, sql.toString().split(strict + "=", -1).length - 1);
			assertEquals(List.of(1, 1, 2, 1, 2, 3), parameters.getBindParameters().stream().map(p -> p.getValue()).toList());
		}
	}
	@Test void equalityBindsEveryComponentWithAnd() {
		Table table = table(); Row row = row(table);
		var holder = new SqlSignature.ColumnsHolder(new LinkedHashSet<>(table.getColumns()), List.of(row));
		var sql = new SqlBuilder(dialect);
		var parameters = RowComparisonOperator.EQUAL.addOperator(dialect, holder, row, null, sql);
		assertFalse(sql.toString().contains("IN"), sql.toString());
		assertFalse(sql.toString().contains("OR"), sql.toString());
		assertEquals(2, sql.toString().split("AND", -1).length - 1);
		assertEquals(List.of(1, 2, 3), parameters.getBindParameters().stream().map(p -> p.getValue()).toList());
	}
	@Test void aSingleColumnRetainsTheInclusiveOperator() {
		Table table = table(); Row row = row(table);
		var holder = new SqlSignature.ColumnsHolder(java.util.Set.of(table.getColumns().get(0)), List.of(row));
		var sql = new SqlBuilder(dialect);
		var parameters = RowComparisonOperator.GREATER_THAN_OR_EQUAL.addOperator(dialect, holder, row, null, sql);
		assertTrue(sql.toString().endsWith(">= ?"), sql.toString());
		assertEquals(1, parameters.getBindParameters().size());
	}
}
