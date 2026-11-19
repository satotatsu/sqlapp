/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.spanner.sql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Index;
import com.sqlapp.data.schemas.Table;

class SpannerPrimaryKeyMigrationTest {
	@Test
	void rejectsInvalidKeysAndUnsupportedDependencies() {
		final var source = new Table("Orders").setSchemaName("Sales");
		source.getColumns().add(new Column("id").setDataType(DataType.BIGINT).setNotNull(true));
		source.getColumns().add(new Column("code").setDataType(DataType.BIGINT).setNotNull(true));
		source.getColumns().add(new Column("array_value").setDataType(DataType.BIGINT).setArrayDimension(1));
		source.getConstraints().addPrimaryKeyConstraint("pk", "id");
		final var plan = SpannerPrimaryKeyMigration.plan(source, List.of("code", "id"), "Stage", "Backup");
		assertFalse(plan.createSql().contains("IF NOT EXISTS"));
		assertEquals("Orders", source.getName());
		assertEquals("id", source.getPrimaryKeyConstraint().getColumns().getFirst().getName());
		for (final var key : List.of(List.<String>of(), List.of("id"), List.of("missing"), List.of("code", "code"),
				List.of("array_value"))) {
			assertThrows(IllegalArgumentException.class, () ->
					SpannerPrimaryKeyMigration.plan(source, key, "Stage", "Backup"));
		}
		final var index = new Index("ByCode");
		index.getColumns().add("code");
		source.getIndexes().add(index);
		assertThrows(UnsupportedOperationException.class, () ->
				SpannerPrimaryKeyMigration.plan(source, List.of("code"), "Stage", "Backup"));
	}
}
