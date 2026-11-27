/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2.i;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.*;

/** IBM i ALTER differences from z/OS: nullability in place, no REORG notes. */
class Db2I75DiffTest {
	private final Db2I75 dialect = new Db2I75();

	@Test
	void nullabilityChangesInPlaceAndDropColumnIsRestricted() {
		var before = table();
		var after = table();
		after.getColumns().get("B").setNotNull(true);
		after.getColumns().remove("C");
		String sql = alter(before, after);
		assertTrue(sql.contains("ALTER TABLE \"APP\".\"T\" ALTER COLUMN \"B\" SET NOT NULL"), sql);
		assertTrue(sql.contains("ALTER TABLE \"APP\".\"T\" DROP COLUMN \"C\" RESTRICT"), sql);
		assertFalse(sql.contains("REORG"), sql);
	}

	@Test
	void renamesUseRenameTable() {
		var before = table();
		var after = table();
		after.setName("U");
		String sql = alter(before, after);
		assertTrue(sql.startsWith("RENAME TABLE \"APP\".\"T\" TO \"U\""), sql);
	}

	private static Table table() {
		var table = new Table("T").setSchemaName("APP");
		table.getColumns().add(new Column("A").setDataType(DataType.INT).setNotNull(true));
		table.getColumns().add(new Column("B").setDataType(DataType.VARCHAR).setLength(10));
		table.getColumns().add(new Column("C").setDataType(DataType.INT));
		return table;
	}

	private String alter(Table before, Table after) {
		return String.join("\n", dialect.createSqlFactoryRegistry().getSqlFactory(after, SqlType.ALTER)
				.createDiffSql(before.diff(after)).stream().map(op -> op.getSqlText()).toList());
	}
}
