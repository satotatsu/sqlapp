/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core.
 */
package com.sqlapp.data.schemas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.StringReader;
import java.io.StringWriter;

import org.junit.jupiter.api.Test;

class ConstraintXmlRoundTripTest {

	@Test
	void readsNamedForeignKeyAfterUnnamedCheckConstraint() throws Exception {
		final Table parent = new Table("parent");
		final Column parentId = new Column("id");
		parent.getColumns().add(parentId);
		final Table table = new Table("child");
		final Column parentIdReference = new Column("parent_id");
		table.getColumns().add(parentIdReference);
		table.getConstraints().addCheckConstraint(null, "parent_id > 0");
		final ForeignKeyConstraint foreignKey = new ForeignKeyConstraint(
				"fk_child_parent");
		foreignKey.addColumns(parentIdReference);
		foreignKey.addRelatedColumn(parentId);
		table.getConstraints().add(foreignKey);

		final StringWriter writer = new StringWriter();
		table.writeXml(writer);
		final Table restored = new Table();
		restored.loadXml(new StringReader(writer.toString()));

		assertNotNull(restored.getConstraints().stream()
				.filter(c -> c instanceof CheckConstraint).findFirst()
				.orElse(null));
		final ForeignKeyConstraint restoredForeignKey = (ForeignKeyConstraint) restored
				.getConstraints().get("fk_child_parent");
		assertNotNull(restoredForeignKey);
		assertEquals("parent", restoredForeignKey.getRelatedTableName());
		assertEquals("parent_id",
				restoredForeignKey.getColumns().get(0).getName());
	}
}
