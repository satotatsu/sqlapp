/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core.
 */
package com.sqlapp.data.schemas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.StringReader;
import java.io.StringWriter;

import org.junit.jupiter.api.Test;

class TemporalTableTest {

	@Test
	void testXmlRoundTrip() throws Exception {
		Catalog catalog = new Catalog("CAT");
		Schema schema = new Schema("PUBLIC");
		Table table = new Table("ORDERS");
		Table historyTable = new Table("ORDERS_HISTORY");
		catalog.getSchemas().add(schema);
		schema.getTables().add(table);
		schema.getTables().add(historyTable);
		table.getColumns().add("VALID_FROM");
		table.getColumns().add("VALID_TO");
		table.getTemporalPeriods().add(new TemporalPeriod("SYSTEM_TIME")
				.setPeriodType(TemporalPeriodType.SYSTEM_TIME)
				.setStartColumnName("VALID_FROM")
				.setEndColumnName("VALID_TO"));
		table.setSystemVersioning(new SystemVersioning()
				.setPeriodName("SYSTEM_TIME")
				.setHistoryTableName("ORDERS_HISTORY")
				.setTransactionIdColumnName("VALID_TO"));
		Table cloned = table.clone();
		assertEquals(table, cloned);
		cloned.getTemporalPeriods().get(0).setEndColumnName("CHANGED_VALID_TO");
		assertNotEquals(table, cloned);
		table.getColumns().get("VALID_FROM").setName("SYSTEM_VALID_FROM");
		table.getColumns().get("VALID_TO").setName("SYSTEM_VALID_TO");
		table.getTemporalPeriods().get(0).setName("BUSINESS_SYSTEM_TIME");
		historyTable.setName("ORDERS_HISTORY_ARCHIVE");
		assertEquals("SYSTEM_VALID_FROM", table.getTemporalPeriods().get(0).getStartColumnName());
		assertEquals("SYSTEM_VALID_TO", table.getTemporalPeriods().get(0).getEndColumnName());
		assertEquals("SYSTEM_VALID_TO", table.getSystemVersioning().getTransactionIdColumnName());
		assertEquals("BUSINESS_SYSTEM_TIME", table.getSystemVersioning().getPeriodName());
		assertEquals("ORDERS_HISTORY_ARCHIVE", table.getSystemVersioning().getHistoryTableName());

		StringWriter writer = new StringWriter();
		catalog.writeXml(writer);
		Catalog restored = new Catalog();
		restored.loadXml(new StringReader(writer.toString()));
		Table restoredTable = restored.getSchemas().get("PUBLIC").getTables().get("ORDERS");
		assertEquals(1, restoredTable.getTemporalPeriods().size());
		assertEquals("SYSTEM_VALID_FROM", restoredTable.getTemporalPeriods().get(0).getStartColumnName());
		assertEquals("SYSTEM_VALID_TO", restoredTable.getTemporalPeriods().get(0).getEndColumnName());
		assertNotNull(restoredTable.getSystemVersioning());
		assertEquals("ORDERS_HISTORY_ARCHIVE", restoredTable.getSystemVersioning().getHistoryTableName());
		assertEquals(table, restoredTable);
	}
}
