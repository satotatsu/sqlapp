package com.sqlapp.data.schemas;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.datatype.DataType;

class RangeColumnTest {
	@Test
	void testRangeAndMultirangeXmlCloneProperties() {
		Column range = new Column("VALID_AT").setDataType(DataType.RANGE).setDataTypeName("daterange");
		Column multirange = new Column("VALID_PERIODS").setDataType(DataType.MULTIRANGE)
				.setDataTypeName("datemultirange");

		assertEquals(DataType.RANGE, range.clone().getDataType());
		assertEquals("DATERANGE", range.clone().getDataTypeName());
		assertEquals(DataType.MULTIRANGE, multirange.clone().getDataType());
		assertEquals("DATEMULTIRANGE", multirange.clone().getDataTypeName());
	}
}
