/**
 * Copyright (C) 2026-2026 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
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

import java.io.File;
import java.io.IOException;
import java.util.List;

import org.junit.jupiter.api.Test;

class TableViewOrderSorterTest {

	@Test
	void test() throws IOException {
		Catalog catalog = SchemaUtils.readXml(new File("src/test/resources/catalog.xml"));
		Schema schema = catalog.getSchemas().get(0);
		List<Table> tables = TableViewOrderSorter.sort(schema.getTables(), t -> t);
		tables.forEach(t -> {
			System.out.println(t.getName());
		});
		int i = 0;
		assertEquals("CUSTOMERS", tables.get(i++).getName());
		assertEquals("PRODUCTS", tables.get(i++).getName());
		assertEquals("SHIPMENTS", tables.get(i++).getName());
		assertEquals("PAYMENT_TERMS", tables.get(tables.size() - 3).getName());
		assertEquals("TAX_RATES", tables.get(tables.size() - 2).getName());
		assertEquals("changelog", tables.get(tables.size() - 1).getName());
	}

}
