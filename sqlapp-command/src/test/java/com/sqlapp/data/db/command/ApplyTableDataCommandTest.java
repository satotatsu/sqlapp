/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-command.
 */
package com.sqlapp.data.db.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.sql.Connection;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.schemas.DbCommonObject;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.Table;

class ApplyTableDataCommandTest {

	@Test
	void acceptsSchemaModelObjectsDirectly() {
		final Schema schema = new Schema("移行元");
		final Table table = new Table("顧客");
		schema.getTables().add(table);
		final TestCommand command = new TestCommand();
		command.setSchemaObjects(List.of(schema));

		assertSame(schema, command.getSchemaObjects().get(0));
		final List<Table> targets = command.targets(
				command.getSchemaObjects());
		assertEquals(List.of(table), targets);
	}

	private static class TestCommand extends ApplyTableDataCommand {
		List<Table> targets(final List<DbCommonObject<?>> objects) {
			return getTarget(objects, (Connection) null, (Dialect) null);
		}
	}
}
