/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.jdbc.bulk.BulkMigrationMode;
import com.sqlapp.jdbc.bulk.BulkOption;
import com.sqlapp.jdbc.bulk.BulkUpsertOption;

class BulkMigrationVerificationColumnsTest {
	@Test
	void followsTheColumnsActuallyWrittenByInsertAndUpsert() {
		final Table table = new Table("ITEMS");
		table.getColumns().add(new Column("ID").setNotNull(true));
		table.getColumns().add(new Column("VALUE"));
		table.getColumns().add(new Column("HIDDEN_VALUE").setHidden(true));
		table.getColumns().add(new Column("CALCULATED").setFormula("VALUE * 2"));
		table.setPrimaryKey("PK_ITEMS", table.getColumns().get("ID"));

		assertEquals(List.of("ID", "VALUE"), BulkMigrationVerificationColumns.resolve(
				table, BulkMigrationMode.INSERT, BulkOption.defaults(),
				BulkUpsertOption.defaults()));
		assertEquals(List.of("ID", "VALUE"), BulkMigrationVerificationColumns.resolve(
				table, BulkMigrationMode.UPSERT, BulkOption.defaults(),
				BulkUpsertOption.defaults()));
	}

	@Test
	void insertIncludesIdentityOnlyWhenItIsKept() {
		final Table table = new Table("ITEMS");
		table.getColumns().add(new Column("ID").setIdentity(true));
		table.getColumns().add(new Column("VALUE"));

		assertEquals(List.of("VALUE"), BulkMigrationVerificationColumns.resolve(table,
				BulkMigrationMode.INSERT, BulkOption.defaults(), BulkUpsertOption.defaults()));
		assertEquals(List.of("ID", "VALUE"), BulkMigrationVerificationColumns.resolve(table,
				BulkMigrationMode.INSERT, BulkOption.builder().keepIdentity(true).build(),
				BulkUpsertOption.defaults()));
	}
}
