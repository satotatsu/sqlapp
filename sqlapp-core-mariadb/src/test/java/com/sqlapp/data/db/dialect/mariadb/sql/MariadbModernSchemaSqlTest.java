/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-mariadb.
 */
package com.sqlapp.data.db.dialect.mariadb.sql;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.math.BigInteger;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.sql.SqlFactory;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Index;
import com.sqlapp.data.schemas.Sequence;
import com.sqlapp.data.schemas.Table;

class MariadbModernSchemaSqlTest extends AbstractMariadbSqlFactoryTest {

	@Override
	protected int getMajorVersion() {
		return 11;
	}

	@Override
	protected int getMinorVersion() {
		return 8;
	}

	@Test
	void testInvisibleColumnAndIgnoredIndex() {
		Table table = new Table("ORDERS");
		table.setDialect(dialect);
		table.getColumns().add("ID", column -> column.setDataType(DataType.INT));
		table.getColumns().add("LEGACY_CODE",
				column -> column.setDataType(DataType.VARCHAR).setLength(20).setHidden(true));
		Index index = new Index("IDX_ORDERS_CODE");
		index.getColumns().add(table.getColumns().get("LEGACY_CODE"));
		index.setEnable(false);
		table.getIndexes().add(index);

		SqlFactory<Table> factory = sqlFactoryRegistry.getSqlFactory(table, SqlType.CREATE);
		String sql = factory.createSql(table).get(0).getSqlText();
		assertTrue(sql.contains("LEGACY_CODE` VARCHAR(20) INVISIBLE"), sql);
		assertTrue(sql.contains("INDEX `IDX_ORDERS_CODE`"), sql);
		assertTrue(sql.contains("`LEGACY_CODE` ) IGNORED"), sql);
	}

	@Test
	void testCreateSequence() {
		Sequence sequence = new Sequence("ORDER_SEQ");
		sequence.setDialect(dialect);
		sequence.setStartValue(BigInteger.valueOf(100));
		sequence.setIncrementBy(BigInteger.TEN);
		sequence.setMinValue(BigInteger.ONE);
		sequence.setMaxValue(BigInteger.valueOf(999999));
		sequence.setCacheSize(50);
		sequence.setCycle(true);

		SqlFactory<Sequence> factory = sqlFactoryRegistry.getSqlFactory(sequence, SqlType.CREATE);
		String sql = factory.createSql(sequence).get(0).getSqlText();
		assertTrue(sql.contains("CREATE SEQUENCE IF NOT EXISTS `ORDER_SEQ`"), sql);
		assertTrue(sql.contains("START WITH 100"), sql);
		assertTrue(sql.contains("INCREMENT BY 10"), sql);
		assertTrue(sql.contains("MINVALUE 1"), sql);
		assertTrue(sql.contains("MAXVALUE 999999"), sql);
		assertTrue(sql.contains("CYCLE"), sql);
		assertTrue(sql.contains("CACHE 50"), sql);
		assertTrue(dialect.supportsSequence());
		assertTrue("NEXT VALUE FOR ORDER_SEQ".equals(dialect.getSequenceNextValString("ORDER_SEQ")));
	}

	@Test
	void testModernDataTypes() {
		assertNotNull(dialect.getDbDataTypes().getDbType(DataType.JSON, null));
		assertNotNull(dialect.getDbDataTypes().getDbType(DataType.UUID, null));
	}
}
