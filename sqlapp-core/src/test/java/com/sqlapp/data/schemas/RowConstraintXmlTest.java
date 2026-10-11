/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core.
 */
package com.sqlapp.data.schemas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.datatype.DataType;

class RowConstraintXmlTest {
	@Test
	void preservesRowsAndColumnOrdinalsWithForeignKeyConstraints() throws Exception {
		Schema schema = new Schema("test");
		Table parent = new Table("parent");
		parent.getColumns().add("id", c -> c.setDataType(DataType.INT));
		schema.getTables().add(parent);
		Table table = new Table("child");
		schema.getTables().add(table);
		table.getColumns().add("id", c -> c.setDataType(DataType.INT));
		table.getColumns().add("parent_id", c -> c.setDataType(DataType.INT));
		table.getColumns().add("label", c -> c.setDataType(DataType.VARCHAR));
		table.getConstraints().addForeignKeyConstraint("fk", table.getColumns().get("parent_id"), parent.getColumns().get("id"));
		table.getRows().add(r -> { r.put("id", 10); r.put("parent_id", 20); r.put("label", "child"); });
		Schema restoredSchema = SchemaUtils.readXml(new java.io.StringReader(schema.asXml()));
		Table restored = restoredSchema.getTables().get("child");
		assertEquals(table.getRows().get(0).getValuesAsMap(), restored.getRows().get(0).getValuesAsMap());
		assertEquals(1, restored.getColumns().get("parent_id").getOrdinal());
		assertSame(restored.getColumns().get("parent_id"), ((ForeignKeyConstraint) restored.getConstraints().get("fk")).getColumns().get(0));
	}

	@Test
	void resolvingConstraintColumnsDoesNotMutateSourceOrClearItsOwnList() {
		Table table = new Table("t");
		table.getColumns().add("a");
		table.getColumns().add("b");
		ForeignKeyConstraint constraint = new ForeignKeyConstraint("fk");
		table.getConstraints().add(constraint);
		constraint.setColumns(table.getColumns());
		constraint.setColumns(constraint.getColumns());
		assertEquals(2, constraint.getColumns().size());
		assertEquals(1, table.getColumns().get("b").getOrdinal());
		assertSame(table.getColumns().get("b"), constraint.getColumns().get(1));
	}
}
