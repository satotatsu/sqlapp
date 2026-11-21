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
	void tenantCatalogDoesNotBecomeAnSqlIdentifier() {
		for (var dialect : List.of(DialectHolder.defaultDialect, DialectHolder.defaultDialect5_3_1)) {
			assertEquals("\"Sales\".\"Batch Seq\"", dialect.getObjectFullName("customer-a", "Sales", "Batch Seq"));
			assertEquals("\"Batch Seq\"", dialect.getObjectFullName("customer-b", null, "Batch Seq"));
		}
	}

	@Test
	void respectsReservationSchemaAndRejectsOutOfBoundsExpansion() {
		final var dialect = DialectHolder.defaultDialect5_3_1;
		final var sequence = new Sequence("Batch Seq").setSchemaName("Sales").setIncrementBy(BigInteger.valueOf(-2))
				.setMinValue(BigInteger.ONE);
		final var registry = dialect.createSqlFactoryRegistry();
		registry.getOptions().setDecorateSchemaName(true);
		final var reservation = registry.createSqlNodes(sequence, SqlType.SEQUENCE_NEXT_VALUES).get(0).eval(3);
		assertTrue(reservation.getSql().contains("VALUES FOR \"Sales\".\"Batch Seq\""), reservation.getSql());
		assertEquals(3, reservation.getBindParameters().get(0).getBindParameter().getValue());
		assertEquals(List.of(5L, 3L, 1L), dialect.expandSequenceValues(sequence, List.of(5L), 3));
		org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
				() -> dialect.expandSequenceValues(sequence, List.of(5L), 4));
	}

	@Test
	void usesExplicitSequenceReservationAndUpsert() {
		final Dialect dialect = DialectHolder.defaultDialect;
		assertTrue(dialect.supportsSequencePreallocation());
		assertFalse(dialect.supportsIdentity());
		assertFalse(dialect.supportsValues());
		assertNull(dialect.getSelectDummyTableName());
		final Sequence sequence = new Sequence("ORDER_SEQ").setIncrementBy(BigInteger.TEN);
		sequence.setDialect(dialect);
		final String sequenceSql = dialect.createSqlFactoryRegistry().createSql(sequence, SqlType.SEQUENCE_NEXT_VALUES)
				.get(0).getSqlText().replaceAll("\\s+", " ");
		assertTrue(sequenceSql.contains("SELECT NEXT /*context*/1 VALUES FOR ORDER_SEQ"), sequenceSql);
		assertEquals(List.of(100L, 110L, 120L), dialect.expandSequenceValues(sequence, List.of(100L), 3));

		final Table table = new Table("ORDERS");
		table.setDialect(dialect);
		table.getColumns().add(new Column("ID").setDataType(DataType.BIGINT));
		final String insertSql = dialect.createSqlFactoryRegistry().createSql(table, SqlType.INSERT_ROWS).get(0)
				.getSqlText().replaceAll("\\s+", " ");
		assertTrue(insertSql.startsWith("UPSERT INTO ORDERS"), insertSql);
	}

	@Test
	void enablesMultiRowValuesFromVersion5_3_1() {
		assertTrue(DialectHolder.defaultDialect5_3_1.supportsValues());
	}
}
