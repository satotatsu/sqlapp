/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core.
 *
 * sqlapp-core is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.schemas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

import javax.xml.stream.XMLStreamException;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.schemas.Table.TableOrder;
import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.FileUtils;

public class SchemaUtilsSortTest {

	protected void testDb() throws XMLStreamException, IOException {
		final InputStream stream = FileUtils.getInputStream(this.getClass(), "catalog.xml");
		if (stream == null) {
			return;
		}
		final Catalog obj1 = SchemaUtils.readXml(this.getClass(), "catalog.xml");
		final StringWriter stringWriter = new StringWriter();
		final Catalog obj2 = new Catalog();
		obj1.writeXml(stringWriter);
		final StringReader stringReader = new StringReader(stringWriter.toString());
		obj2.loadXml(stringReader);
		assertEquals(obj1, obj2);
	}

	@Test
	public void testINSERTSort() throws XMLStreamException, InterruptedException, IOException {
		List<Table> tables = readTables();
		System.out.println("===================INSERT===================");
		List<Table> sortedTables = SchemaUtils.getNewSortedTableList(tables, TableOrder.CREATE);
		logTablesForInsert(sortedTables);
		int i = getOrder(sortedTables, t -> t.getName().equalsIgnoreCase("SHIPMENT_DETAILS"));
		int j = getOrder(sortedTables, t -> t.getName().equalsIgnoreCase("INVOICE_DETAILS"));
		assertTrue(i >= 0);
		assertTrue(j >= 0);
		assertTrue(i < j);
		i = getOrder(sortedTables, t -> t.getName().equalsIgnoreCase("PRODUCTS"));
		j = getOrder(sortedTables, t -> t.getName().equalsIgnoreCase("ORDER_DETAILS"));
		assertTrue(i >= 0);
		assertTrue(j >= 0);
		assertTrue(i < j);
		i = getOrder(sortedTables, t -> t.getName().equalsIgnoreCase("RECEIPTS"));
		j = getOrder(sortedTables, t -> t.getName().equalsIgnoreCase("RECEIPT_ALLOCATIONS"));
		assertTrue(i >= 0);
		assertTrue(j >= 0);
		assertTrue(i < j);
		List<Table> reverseTables = SchemaUtils.getNewSortedTableList(tables, TableOrder.DROP);
		Collections.reverse(sortedTables);
		for (int k = 0; k < sortedTables.size(); k++) {
			assertEquals(sortedTables.get(k), reverseTables.get(k));
		}
	}

	private List<Table> readTables() throws IOException {
		final Catalog catalog = SchemaUtils.readXml(this.getClass(), "catalog.xml");
		List<Table> tables = CommonUtils.list();
		catalog.getSchemas().stream().map(s -> s.getTables()).forEach(ts -> {
			tables.addAll(ts);
		});
		return tables;
	}

	private void logTablesForInsert(List<Table> tables) {
		for (int i = 0; i < tables.size(); i++) {
			Table table = tables.get(i);
			StringBuilder builder = new StringBuilder("table[" + i + "]=" + table.getName());
			List<ForeignKeyConstraint> fks = table.getConstraints().getForeignKeyConstraints();
			if (!fks.isEmpty()) {
				builder.append(", fk_size=" + fks.size());
				builder.append(" (");
				int j = 0;
				for (ForeignKeyConstraint fk : fks) {
					if (j > 0) {
						builder.append(", ");
					}
					builder.append("->" + fk.getRelatedTableName());
					j++;
					for (int k = i + 1; k < tables.size(); k++) {
						Table relTable = tables.get(k);
						if (fk.getRelatedTableName().equalsIgnoreCase(relTable.getName())) {
							assertTrue(false, table.getName() + "[" + i + "], " + relTable.getName() + "[" + k + "]");
						}
					}
				}
				builder.append(")");
			}
			System.out.println(builder);
		}
	}

	private int getOrder(List<Table> tables, Predicate<Table> predicate) {
		for (int i = 0; i < tables.size(); i++) {
			if (predicate.test(tables.get(i))) {
				return i;
			}
		}
		return -1;
	}
}
