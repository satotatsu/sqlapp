package com.sqlapp.data.db.dialect.postgres;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.schemas.Column;

class Postgres160Test {

	Dialect dialect = DialectResolver.getInstance().getDialect("Postgres", 16, 0, 0);

	@Test
	void testChar() {
		Column column = createColumn("colName");
		column.setDataTypeName("character");
		column.setLength(100);
		assertEquals(DataType.CHAR, column.getDataType());
		//
		column = createColumn("colName");
		column.setDataTypeName("char");
		assertEquals(DataType.CHAR, column.getDataType());
		//
		column = createColumn("colName");
		column.setDataTypeName("char(10)");
		assertEquals(DataType.CHAR, column.getDataType());
		//
		column = createColumn("colName");
		column.setDataTypeName("bpchar(15)");
		assertEquals(DataType.CHAR, column.getDataType());
		assertEquals(15, column.getLength().intValue());
		//
		column = createColumn("colName");
		column.setDataTypeName("\"char\"(20)");
		assertEquals(DataType.CHAR, column.getDataType());
		assertEquals(20, column.getLength().intValue());
	}

	@Test
	void testVarChar() {
		Column column = createColumn("colName");
		column.setDataTypeName("varchar(100)");
		assertEquals(DataType.VARCHAR, column.getDataType());
		assertEquals(100, column.getLength().intValue());
		//
		column = createColumn("colName");
		column.setDataTypeName("VARCHAR");
		column.setLength(10);
		assertEquals(DataType.VARCHAR, column.getDataType());
		//
		column = createColumn("colName");
		column.setDataTypeName("character(10) varying");
		column.setLength(10);
		assertEquals(DataType.VARCHAR, column.getDataType());
	}

	private Column createColumn(String name) {
		Column column = new Column("colName");
		column.setDialect(dialect);
		return column;
	}

}
