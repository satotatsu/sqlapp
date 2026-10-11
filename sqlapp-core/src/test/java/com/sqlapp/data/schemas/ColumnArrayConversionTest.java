/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.schemas;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.datatype.DataType;

class ColumnArrayConversionTest {
	@Test
	void keepsArrayValuesWhenScalarElementTypeHasArrayDimensions() {
		Table table = new Table("arrays");
		Column strings = new Column("strings").setDataType(DataType.CLOB);
		strings.getConverter(); // Invalidate a converter resolved before array metadata was set.
		strings.setArrayDimension(1);
		table.getColumns().add(strings);
		table.getColumns().add(new Column("matrix").setDataType(DataType.INT).setArrayDimension(2));
		table.getColumns().add(new Column("scalar").setDataType(DataType.INT));
		String[] values = {"one", "two,three", null};
		Integer[][] matrix = {{1, 2}, {3, 4}};
		Row row = table.newRow();
		row.put("strings", values);
		row.put("matrix", matrix);
		row.put("scalar", "12");
		assertSame(values, row.get("strings"));
		assertSame(matrix, row.get("matrix"));
		assertEquals(Integer.valueOf(12), (Object) row.get("scalar"));
		assertSame(values, strings.clone().getConverter().convertObject(values));
		strings.setArrayDimension(0);
		assertEquals("text", strings.getConverter().convertObject("text"));
	}
}
