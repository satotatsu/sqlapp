package com.sqlapp.data.schemas;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ColumnListTest {

	@Test
	void test() {
		ColumnList list = new ColumnList();
		Column colA = new Column("A");
		Column colB = new Column("B");
		Column colC = new Column("C");
		Column colD = new Column("D");
		list.add(colA);
		list.add(colB);
		list.add(colC);
		assertTrue(list.contains("A"));
		assertTrue(list.contains("B"));
		assertTrue(list.contains("C"));
		assertFalse(list.contains("D"));
		//
		assertTrue(list.contains("A"));
		assertTrue(list.contains("B"));
		assertTrue(list.contains("C"));
		//
		assertTrue(list.contains(colA));
		assertTrue(list.contains(colB));
		assertTrue(list.contains(colC));
		assertFalse(list.contains(colD));
		//
		list.add(colD);
		assertTrue(list.contains("D"));
		assertTrue(list.contains(colD));
		//
		list.remove("A");
		assertFalse(list.contains("A"));
		assertFalse(list.contains(colA));
		//
		list.remove(colB);
		assertFalse(list.contains("B"));
		assertFalse(list.contains(colB));
		//
		colC.setName("C1");
		assertTrue(list.contains("C"));
		assertTrue(list.contains(colC));
		//
		list.reset();
		assertFalse(list.contains("C"));
		assertTrue(list.contains("C1"));
	}

}
