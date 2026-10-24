/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.schemas;

import static org.junit.jupiter.api.Assertions.*;
import java.io.StringReader;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.datatype.DataType;

class MaskTest {
	private Mask mask() {
		return new Mask("M").setSchemaName("CONTROL").setTableSchemaName("DATA")
				.setTableName("T").setColumnName("C").setEnable(false).setStatement("0");
	}

	@Test void cloneAndXmlRetainTargetAndDisabledState() throws Exception {
		Mask original = mask();
		for (Mask copy : java.util.List.of(original.clone(), SchemaUtils.<Mask>readXml(new StringReader(original.asXml())))) {
			assertEquals("DATA", copy.getTableSchemaName());
			assertEquals("T", copy.getTableName());
			assertEquals("C", copy.getColumnName());
			assertFalse(copy.isEnable());
			assertEquals(original, copy);
		}
	}

	@Test void changesInEnableAndEachTargetIdentifierAreDetected() {
		Mask original = mask();
		assertEquals(State.Modified, original.diff(original.clone().setEnable(true)).getState());
		assertEquals(State.Modified, original.diff(original.clone().setColumnName("D")).getState());
		assertEquals(State.Modified, original.diff(original.clone().setTableName("OTHER")).getState());
		assertEquals(State.Modified, original.diff(original.clone().setTableSchemaName("OTHER")).getState());
	}

	@Test void changingOneReferenceComponentPreservesTheOthers() {
		Mask mask = mask();
		mask.setTableSchemaName("DATA");
		assertEquals("T", mask.getTableName());
		mask.setColumnName("D");
		assertEquals("DATA", mask.getTableSchemaName()); assertEquals("T", mask.getTableName());
		mask.setTableName("T2");
		assertEquals("DATA", mask.getTableSchemaName()); assertEquals("D", mask.getColumnName());
		mask.setTableSchemaName("OTHER");
		assertEquals("T2", mask.getTableName()); assertEquals("D", mask.getColumnName());
	}

	@Test void changingResolvedMaskDoesNotRenameSourceColumnOrTable() {
		Schema schema = new Schema("DATA");
		Table table = new Table("T");
		table.getColumns().add("C", c -> c.setDataType(DataType.INT));
		schema.getTables().add(table);
		Mask mask = new Mask("M").setTableSchemaName("DATA").setTableName("T").setColumnName("C");
		schema.getMasks().add(mask);
		assertSame(table.getColumns().get("C"), mask.getColumn());
		mask.setColumnName("D");
		assertEquals("C", table.getColumns().getFirst().getName());
		assertEquals("T", table.getName());
		assertEquals("DATA", table.getSchemaName());
		assertEquals("D", mask.getColumnName());
	}
}
