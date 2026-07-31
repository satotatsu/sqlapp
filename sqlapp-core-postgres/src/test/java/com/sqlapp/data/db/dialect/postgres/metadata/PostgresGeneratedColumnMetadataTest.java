package com.sqlapp.data.db.dialect.postgres.metadata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.schemas.Column;

class PostgresGeneratedColumnMetadataTest {

	@Test
	void testRestoreVirtualGeneratedColumn() {
		Column column = new Column("TOTAL");

		PostgresUtils.setGeneratedExpression(column, "price * quantity", "v");

		assertEquals("price * quantity", column.getFormula());
		assertFalse(column.isFormulaPersisted());
		assertNull(column.getDefaultValue());
	}

	@Test
	void testRestoreStoredGeneratedColumn() {
		Column column = new Column("TOTAL");

		PostgresUtils.setGeneratedExpression(column, "price * quantity", "s");

		assertEquals("price * quantity", column.getFormula());
		assertTrue(column.isFormulaPersisted());
		assertNull(column.getDefaultValue());
	}

	@Test
	void testRestoreOrdinaryDefaultExpression() {
		Column column = new Column("STATUS");

		PostgresUtils.setGeneratedExpression(column, "'NEW'::text", "");

		assertNull(column.getFormula());
		assertEquals("'NEW'::text", column.getDefaultValue());
	}
}
