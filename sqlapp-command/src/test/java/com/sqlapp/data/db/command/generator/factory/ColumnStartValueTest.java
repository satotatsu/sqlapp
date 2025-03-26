package com.sqlapp.data.db.command.generator.factory;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.command.generator.factory.ColumnStartValue;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.schemas.Column;

class ColumnStartValueTest {

	private ColumnStartValue func = new ColumnStartValue();

	@Test
	void testBIGINT() {
		Column column = new Column();
		column.setDataType(DataType.BIGINT);
		assertEquals("1", func.apply(column));
	}

	@Test
	void testBOOLEAN() {
		Column column = new Column();
		column.setDataType(DataType.BOOLEAN);
		assertEquals("true", func.apply(column));
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
		column.setDataType(DataType.DATE);
		LocalDate date = LocalDate.now();
		int year = date.getYear();
		int month = date.getMonthValue();
		assertEquals("LocalDate.of(" + year + "," + month + ",1)", func.apply(column));
	}

	@Test
	void testDATETIME() {
		Column column = new Column();
		column.setDataType(DataType.DATETIME);
		LocalDate date = LocalDate.now();
		int year = date.getYear();
		int month = date.getMonthValue();
		assertEquals("LocalDateTime.of(" + year + "," + month + ",1,0,0,0)", func.apply(column));
	}

	@Test
	void testTIMESTAMP() {
		Column column = new Column();
		column.setDataType(DataType.TIMESTAMP);
		LocalDate date = LocalDate.now();
		int year = date.getYear();
		int month = date.getMonthValue();
		assertEquals("LocalDateTime.of(" + year + "," + month + ",1,0,0,0)", func.apply(column));
	}

	@Test
	void testTIME() {
		Column column = new Column();
		column.setDataType(DataType.TIME);
		assertEquals("LocalTime.of(0,0,0)", func.apply(column));
	}

	@Test
	void testUUID() {
		Column column = new Column();
		column.setDataType(DataType.UUID);
		assertEquals("java.util.UUID.randomUUID()", func.apply(column));
	}
}
