/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-command.
 */
package com.sqlapp.data.db.command.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.migration.MigrationDataTest;

class MigrationDataTestPlannerTest {

	@Test
	void infersNotNullAndPrimaryKeyUniqueness() {
		final Schema schema = new Schema("PUBLIC");
		final Table table = new Table("CUSTOMER");
		table.getColumns().add(new Column("ID").setDataType(DataType.BIGINT).setNotNull(true));
		table.getColumns().add(new Column("NAME").setDataType(DataType.VARCHAR));
		table.setPrimaryKey("PK_CUSTOMER", table.getColumns().get("ID"));
		schema.getTables().add(table);

		final var tests = MigrationDataTestPlanner.infer(schema);

		assertEquals(2, tests.size());
		assertEquals(MigrationDataTest.Type.NOT_NULL, tests.get(0).type());
		assertEquals(MigrationDataTest.Type.UNIQUE, tests.get(1).type());
	}
}
