package com.sqlapp.data.db.command.generator.factory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.command.generator.factory.ColumnMaxValue;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.schemas.Column;

class ColumnMaxValueTest {

	private ColumnMaxValue func = new ColumnMaxValue();

	@Test
	void testBIGINT() {
		Column column = new Column();
		column.setName("col");
		column.setDataType(DataType.BIGINT);
		assertEquals("9223372036854775807", func.apply(column));
	}

	@Test
	void testBOOLEAN() {
		Column column = new Column();
		column.setName("col");
		column.setDataType(DataType.BOOLEAN);
		assertNull(func.apply(column));
	}

	@Test
	void testVARCHAR() {
		Column column = new Column();
		column.setDataType(DataType.VARCHAR);
		column.setLength(10);
		assertNull(func.apply(column));
	}

	@Test
	void testDATE() {
		Column column = new Column();
		column.setName("col");
		column.setDataType(DataType.DATE);
		assertEquals("addMonths(_start.col,1)", func.apply(column));
	}

	@Test
	void testDATETIME() {
		Column column = new Column();
		column.setName("col");
		column.setDataType(DataType.DATETIME);
		assertEquals("addMonths(_start.col,1)", func.apply(column));
	}

	@Test
	void testTIMESTAMP() {
		Column column = new Column();
		column.setName("col");
		column.setDataType(DataType.TIMESTAMP);
		assertEquals("addMonths(_start.col,1)", func.apply(column));
	}

	@Test
	void testTIME() {
		Column column = new Column();
		column.setName("col");
		column.setDataType(DataType.TIME);
		assertNull(func.apply(column));
	}

	@Test
	void testUUID() {
		Column column = new Column();
		column.setName("col");
		column.setDataType(DataType.UUID);
		assertNull(func.apply(column));
	}
}
