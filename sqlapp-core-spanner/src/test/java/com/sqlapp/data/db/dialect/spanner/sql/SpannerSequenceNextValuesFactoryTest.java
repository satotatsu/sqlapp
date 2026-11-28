/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.spanner.sql;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.dialect.spanner.DialectHolder;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Sequence;

class SpannerSequenceNextValuesFactoryTest {
	@Test
	void honorsSchemaDecorationWhenGeneratingAllocationSql() {
		final var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
		final var sequence = new Sequence("OrderSeq").setSchemaName("Sales");
		final var qualified = registry.createSql(sequence, SqlType.SEQUENCE_NEXT_VALUES).getFirst().getSqlText();
		assertTrue(qualified.contains("SEQUENCE `Sales`.`OrderSeq`"), qualified);

		registry.getOptions().setDecorateSchemaName(false);
		final var unqualified = registry.createSql(sequence, SqlType.SEQUENCE_NEXT_VALUES).getFirst().getSqlText();
		assertTrue(unqualified.contains("SEQUENCE `OrderSeq`"), unqualified);
	}
}
