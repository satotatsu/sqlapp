/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core.
 */
package com.sqlapp.data.schemas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.datatype.DataType;

class ColumnDomainNameTest {

	@Test
	void namingTheCurrentDomainKeepsItsDefinition() {
		final Domain domain = new Domain("D_AMOUNT").setDataType(DataType.NUMERIC).setLength(18).setScale(2);
		final Column column = new Column("AMOUNT").setDomain(domain).setDomainName("D_AMOUNT");
		assertSame(domain, column.getDomain());
		assertEquals(DataType.NUMERIC, column.getDomain().getDataType());
		column.setDomainSchemaName(null);
		assertSame(domain, column.getDomain());
	}

	@Test
	void anotherNameReplacesTheDomain() {
		final Domain domain = new Domain("D_AMOUNT").setDataType(DataType.NUMERIC);
		final Column column = new Column("AMOUNT").setDomain(domain).setDomainName("D_PRICE");
		assertEquals("D_PRICE", column.getDomainName());
		assertNull(column.getDomain().getDataType());
		column.setDomainName(null);
		assertNull(column.getDomain());
		column.setDomain(domain).setDomainSchemaName("S");
		assertEquals("S", column.getDomainSchemaName());
		assertEquals("D_AMOUNT", column.getDomainName());
	}
}
