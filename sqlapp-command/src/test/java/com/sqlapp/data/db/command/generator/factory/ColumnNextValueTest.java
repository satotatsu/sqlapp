package com.sqlapp.data.db.command.generator.factory;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.command.generator.factory.ColumnNextValue;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.schemas.Column;

class ColumnNextValueTest {

	private ColumnNextValue func = new ColumnNextValue();

	@Test
	void testBIGINT() {
		Column column = new Column();
		column.setName("col");
		column.setDataType(DataType.BIGINT);
		assertEquals("_previous.col + 1", func.apply(column));
	}

	@Test
	void testBOOLEAN() {
		Column column = new Column();
		column.setName("col");
		column.setDataType(DataType.BOOLEAN);
		assertEquals("!_previous.col", func.apply(column));
	}

	@Test
	void testVARCHAR() {
		Column column = new Column();
		column.setDataType(DataType.VARCHAR);
		column.setLength(10);
		assertEquals("nextAlphaNumeric( 10 )", func.apply(column));
	}

	@Test
	void testDATE() {
		Column column = new Column();
		column.setName("col");
		column.setDataType(DataType.DATE);
		assertEquals("addDays(_previous.col,1)", func.apply(column));
	}

	@Test
	void testDATETIME() {
		Column column = new Column();
		column.setName("col");
		column.setDataType(DataType.DATETIME);
		assertEquals("addMilliSeconds(_previous.col,1)", func.apply(column));
	}

	@Test
	void testTIMESTAMP() {
		Column column = new Column();
		column.setName("col");
		column.setDataType(DataType.TIMESTAMP);
		assertEquals("addMilliSeconds(_previous.col,1)", func.apply(column));
	}

	@Test
	void testTIME() {
		Column column = new Column();
		column.setName("col");
		column.setDataType(DataType.TIME);
		assertEquals("addSeconds(_previous.col,1)", func.apply(column));
	}

	@Test
	void testUUID() {
		Column column = new Column();
		column.setName("col");
		column.setDataType(DataType.UUID);
		assertEquals("java.util.UUID.randomUUID()", func.apply(column));
	}
}
