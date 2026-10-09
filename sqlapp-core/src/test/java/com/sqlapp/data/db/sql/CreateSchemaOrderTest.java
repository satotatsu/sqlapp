/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.sql;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.List;
import org.junit.jupiter.api.Test;

class CreateSchemaOrderTest {
	private static class Factory extends CreateSchemaFactory {
		List<String> order() { return List.copyOf(getCreateCollectionOrder()); }
		void custom(String name) { addCollectionOrder(name); }
	}
	@Test
	void keepsPrerequisitesBeforeDependentsAndIsolatesFactoryExtensions() {
		Factory factory = new Factory();
		List<String> order = factory.order();
		for (String[] dependency : new String[][] { {"types","domains"}, {"domains","functions"},
				{"functions","tables"}, {"tables","views"}, {"views","triggers"} }) {
			assertTrue(order.indexOf(dependency[0]) >= 0);
			assertTrue(order.indexOf(dependency[0]) < order.indexOf(dependency[1]), java.util.Arrays.toString(dependency));
		}
		factory.custom("vendorOnly");
		assertTrue(factory.order().contains("vendorOnly"));
		assertFalse(new Factory().order().contains("vendorOnly"));
	}
}
