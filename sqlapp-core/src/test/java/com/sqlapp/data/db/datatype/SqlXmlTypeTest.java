package com.sqlapp.data.db.datatype;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class SqlXmlTypeTest {
	@Test
	void switchingJdbcHandlingKeepsTheConfiguredDefaultValueLiteral() {
		assertEquals("'<root/>'", new SqlXmlType().setDefaultValueLiteral("'<root/>'").setAsNVarchar()
				.getDefaultValueLiteral());
		assertEquals("'<root/>'", new SqlXmlType().setDefaultValueLiteral("'<root/>'").setAsVarchar()
				.getDefaultValueLiteral());
		assertEquals(new SqlXmlType().getDefaultValueLiteral(), new SqlXmlType().setAsNVarchar().getDefaultValueLiteral());
	}
}
