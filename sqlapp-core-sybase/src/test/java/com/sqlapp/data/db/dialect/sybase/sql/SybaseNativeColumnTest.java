/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.sybase.sql;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.*;
import com.sqlapp.data.db.dialect.sybase.DialectHolder;
import com.sqlapp.data.db.dialect.sybase.util.SybaseSqlBuilder;

class SybaseNativeColumnTest extends AbstractSybaseSqlFactoryRegistryTest {
	@Test
	void recreatesNativeColumnsThroughXml() throws Exception {
		final Table table = new Table("sales");
		table.getColumns()
				.add(new Column("id").setDataType(DataType.NUMERIC).setLength(18).setScale(0).setIdentity(true));
		table.getColumns().add(new Column("amount").setDataType(DataType.INT));
		table.getColumns().add(new Column("total").setFormula("amount * 2").setFormulaPersisted(true));
		table.getColumns().add(new Column("virtual_total").setFormula("amount + 1"));
		final Table restored = SchemaUtils.readXml(new java.io.StringReader(table.asXml()));
		final String sql = sqlFactoryRegistry.createSql(restored, SqlType.CREATE).get(0).getSqlText()
				.replaceAll("\\s+", " ").toUpperCase();
		assertTrue(sql.contains("IDENTITY"), sql);
		assertFalse(sql.contains("GENERATED"), sql);
		assertFalse(sql.contains("IDENTITY NULL"), sql);
		assertTrue(sql.contains("[TOTAL] AS AMOUNT * 2 MATERIALIZED"), sql);
		assertTrue(sql.contains("[VIRTUAL_TOTAL] AS AMOUNT + 1 NOT MATERIALIZED"), sql);
		assertFalse(sql.contains("MATERIALIZED NULL"), sql);
	}

	@Test
	void addsComputedColumn() {
		final Table original = new Table("sales");
		original.getColumns().add(new Column("amount").setDataType(DataType.INT));
		final Table target = original.clone();
		target.getColumns().add(new Column("total").setFormula("amount * 2").setFormulaPersisted(true));
		final String sql = sqlFactoryRegistry.createSql(original.diff(target), SqlType.ALTER).get(0).getSqlText()
				.replaceAll("\\s+", " ").toUpperCase();
		assertTrue(sql.contains("ADD [TOTAL] AS AMOUNT * 2 MATERIALIZED"), sql);
		assertFalse(sql.contains("MATERIALIZED NULL"), sql);
	}

	@Test
	void rejectsUnsupportedOrContradictoryOptions() {
		final var builder = new SybaseSqlBuilder(DialectHolder.defaultDialect);
		assertThrows(IllegalArgumentException.class, () -> builder
				.definition(new Column("id").setDataType(DataType.INT).setIdentity(true).setIdentityStep(2L)));
		assertThrows(IllegalArgumentException.class, () -> builder
				.definition(new Column("id").setDataType(DataType.INT).setIdentity(true).setIdentityStartValue(5L)));
		assertThrows(IllegalArgumentException.class, () -> builder.definition(new Column("bad").setFormula(" ")));
		assertThrows(IllegalArgumentException.class,
				() -> builder.definition(new Column("bad").setFormula("1").setDefaultValue("1")));
	}
}
