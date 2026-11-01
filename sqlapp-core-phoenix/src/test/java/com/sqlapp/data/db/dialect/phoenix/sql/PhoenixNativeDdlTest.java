/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.phoenix.sql;

import static org.junit.jupiter.api.Assertions.*;
import java.math.BigInteger;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.phoenix.DialectHolder;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.schemas.*;

class PhoenixNativeDdlTest {
	@Test
	void rejectsUnrepresentableIndexStateAndLocalSalting() {
		final var table = new Table("t");
		final var index = new Index("idx");
		index.getColumns().add("value");
		table.getIndexes().add(index);
		final var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
		index.getSpecifics().put("phoenix.indexState", "x");
		assertThrows(UnsupportedOperationException.class, () -> registry.createSql(index, SqlType.CREATE));
		index.getSpecifics().clear();
		index.getSpecifics().put("phoenix.indexType", "UNKNOWN");
		assertThrows(IllegalArgumentException.class, () -> registry.createSql(index, SqlType.CREATE));
		index.getSpecifics().put("phoenix.indexType", "LOCAL");
		index.getSpecifics().put(PhoenixDdlOptions.SALT_BUCKETS, "8");
		assertThrows(IllegalArgumentException.class, () -> registry.createSql(index, SqlType.CREATE));
	}

	@Test
	void createsTablesWithQuotedFamiliesAndArrayColumnsThroughXml() throws Exception {
		final var table = new Table("events");
		table.getColumns().add(new Column("id").setDataType(DataType.INT).setNotNull(true));
		final var payload = new Column("payload").setDataType(DataType.VARCHAR).setArrayDimension(1)
				.setArrayDimensionUpperBound(10);
		payload.getSpecifics().put(PhoenixCreateTableFactory.COLUMN_FAMILY, "case sensitive");
		table.getColumns().add(payload);
		final var pk = new UniqueConstraint("pk").setPrimaryKey(true);
		pk.getColumns().add("id");
		table.getConstraints().add(pk);
		final Table restored = SchemaUtils.readXml(new java.io.StringReader(table.asXml()));
		final var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
		registry.getOptions().setCreateIfNotExists(true);
		registry.getOptions().setQuateColumnName(true);
		final String sql = registry.createSql(restored, SqlType.CREATE).get(0).getSqlText();
		assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS"), sql);
		assertTrue(sql.contains("\"case sensitive\".\"payload\" VARCHAR ARRAY[10]"), sql);
		assertTrue(sql.contains("PRIMARY KEY"), sql);
		pk.getColumns().add("payload");
		pk.getColumns().add("extra");
		assertThrows(IllegalArgumentException.class, () -> registry.createSql(table, SqlType.CREATE));
	}

	@Test
	void createsSequencesThroughXmlAndRejectsInvalidBounds() throws Exception {
		final var sequence = new Sequence("s").setSchemaName("app").setDataType(DataType.BIGINT)
				.setStartValue(BigInteger.TEN).setIncrementBy(BigInteger.TWO).setMinValue(BigInteger.ONE)
				.setMaxValue(BigInteger.valueOf(100)).setCacheSize(10).setCycle(true);
		final Sequence restored = SchemaUtils.readXml(new java.io.StringReader(sequence.asXml()));
		final var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
		registry.getOptions().setDecorateSchemaName(true);
		registry.getOptions().setCreateIfNotExists(true);
		final String sql = registry.createSql(restored, SqlType.CREATE).get(0).getSqlText();
		assertTrue(sql.contains("CREATE SEQUENCE IF NOT EXISTS"), sql);
		assertTrue(sql.contains("START WITH 10 INCREMENT BY 2 MINVALUE 1 MAXVALUE 100 CYCLE CACHE 10"), sql);
		assertFalse(sql.contains(" AS "), sql);
		registry.getOptions().setDropIfExists(true);
		assertTrue(registry.createSql(restored, SqlType.DROP).get(0).getSqlText().contains("DROP SEQUENCE IF EXISTS"));
		assertThrows(IllegalArgumentException.class,
				() -> registry.createSql(sequence.clone().setIncrementBy(BigInteger.ZERO), SqlType.CREATE));
		assertThrows(IllegalArgumentException.class,
				() -> registry.createSql(sequence.clone().setCacheSize(-1), SqlType.CREATE));
		assertThrows(IllegalArgumentException.class,
				() -> registry.createSql(sequence.clone().setMinValue(BigInteger.valueOf(101)), SqlType.CREATE));
		assertThrows(ArithmeticException.class,
				() -> registry.createSql(sequence.clone().setStartValue(BigInteger.ONE.shiftLeft(64)), SqlType.CREATE));
	}

	@Test
	void createsCoveringIndexesAndDropsThemOnTheirParentTable() throws Exception {
		final var table = new Table("events").setSchemaName("app");
		final var index = new Index("idx_events");
		index.getColumns().add("created", Order.Desc);
		index.getColumns().add("id", Order.Asc);
		index.getIncludes().add("payload");
		table.getIndexes().add(index);
		final Table restored = SchemaUtils.readXml(new java.io.StringReader(table.asXml()));
		final var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
		registry.getOptions().setDecorateSchemaName(true);
		registry.getOptions().setCreateIfNotExists(true);
		registry.getOptions().setDropIfExists(true);
		final var restoredIndex = restored.getIndexes().get(0);
		final String create = registry.createSql(restoredIndex, SqlType.CREATE).get(0).getSqlText();
		assertTrue(create.contains("INDEX IF NOT EXISTS"), create);
		assertTrue(create.contains("DESC"), create);
		assertTrue(create.contains("INCLUDE ("), create);
		assertTrue(create.contains("payload"), create);
		final String drop = registry.createSql(restoredIndex, SqlType.DROP).get(0).getSqlText();
		assertTrue(drop.contains("DROP INDEX IF EXISTS"), drop);
		assertTrue(drop.contains(" ON "), drop);
		assertTrue(drop.contains("events"), drop);
		assertThrows(IllegalArgumentException.class, () -> registry.createSql(new Index("orphan"), SqlType.DROP));
		assertThrows(IllegalArgumentException.class, () -> registry.createSql(new Index("orphan"), SqlType.CREATE));
		final var empty = new Index("empty");
		restored.getIndexes().add(empty);
		assertThrows(IllegalArgumentException.class, () -> registry.createSql(empty, SqlType.CREATE));
		restoredIndex.setUnique(true);
		assertThrows(IllegalArgumentException.class, () -> registry.createSql(restoredIndex, SqlType.CREATE));
	}
}
