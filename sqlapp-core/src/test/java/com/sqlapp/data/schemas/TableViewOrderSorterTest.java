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
