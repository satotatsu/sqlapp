/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-phoenix.
 */
package com.sqlapp.data.db.dialect.phoenix;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Sequence;
import com.sqlapp.data.schemas.Table;

class PhoenixGeneratedKeyTest {

	@Test
	void usesExplicitSequenceReservationAndUpsert() {
		final Dialect dialect = DialectHolder.defaultDialect;
		assertTrue(dialect.supportsSequencePreallocation());
		assertFalse(dialect.supportsIdentity());
		assertFalse(dialect.supportsValues());
		assertNull(dialect.getSelectDummyTableName());
		final Sequence sequence = new Sequence("ORDER_SEQ").setIncrementBy(BigInteger.TEN);
		sequence.setDialect(dialect);
		final String sequenceSql = dialect.createSqlFactoryRegistry()
				.createSql(sequence, SqlType.SEQUENCE_NEXT_VALUES).get(0).getSqlText().replaceAll("\\s+", " ");
		assertTrue(sequenceSql.contains("SELECT NEXT /*context*/1 VALUES FOR ORDER_SEQ"), sequenceSql);
		assertEquals(List.of(100L, 110L, 120L),
				dialect.expandSequenceValues(sequence, List.of(100L), 3));

		final Table table = new Table("ORDERS");
		table.setDialect(dialect);
		table.getColumns().add(new Column("ID").setDataType(DataType.BIGINT));
		final String insertSql = dialect.createSqlFactoryRegistry()
				.createSql(table, SqlType.INSERT_ROWS).get(0).getSqlText().replaceAll("\\s+", " ");
		assertTrue(insertSql.startsWith("UPSERT INTO ORDERS"), insertSql);
	}

	@Test
	void enablesMultiRowValuesFromVersion5_3_1() {
		assertTrue(DialectHolder.defaultDialect5_3_1.supportsValues());
	}
}
