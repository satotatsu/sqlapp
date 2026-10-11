/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.schemas;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class NullsOrderTest {
	@Test
	void preservesDistinctSqlValuesAndParsing() {
		assertEquals("NULLS FIRST", NullsOrder.NullsFirst.getSqlValue());
		assertEquals("NULLS LAST", NullsOrder.NullsLast.getSqlValue());
		for (var order : NullsOrder.values()) {
			assertSame(order, NullsOrder.parse(order.getSqlValue()));
			assertEquals(order.getSqlValue(), order.getDisplayName());
		}
	}
	@Test
	void retainsBothOrdersThroughExistingSchemaXml() throws Exception {
		for (var order : NullsOrder.values()) {
			var column = new ReferenceColumn("label").setNullsOrder(order);
			var output = new java.io.StringWriter();
			var writer = new com.sqlapp.util.StaxWriter(output);
			writer.writeStartDocument();
			column.writeXml(writer);
			var reader = new com.sqlapp.util.StaxReader(new java.io.StringReader(output.toString()));
			var handler = new com.sqlapp.util.xml.ResultHandler();
			handler.registerChild(new ReferenceColumnXmlReaderHandler());
			handler.handle(reader, null);
			assertSame(order, ((ReferenceColumn) handler.getResult().get(0)).getNullsOrder());
		}
	}

}
