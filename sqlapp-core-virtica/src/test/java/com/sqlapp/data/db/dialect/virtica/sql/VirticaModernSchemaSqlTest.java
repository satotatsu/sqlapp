/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-virtica.
 */
package com.sqlapp.data.db.dialect.virtica.sql;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Table;

class VirticaModernSchemaSqlTest extends VirticaSqlFactoryTest {

	@Test
	void testTableExistenceClausesAndNativeUuid() {
		final Table table = new Table("DOCUMENTS");
		table.setDialect(dialect);
		table.getColumns().add(
				new Column("ID").setDataType(DataType.UUID));
		final String createSql = sqlFactoryRegistry
				.createSql(table, SqlType.CREATE).get(0).getSqlText()
				.replaceAll("\\s+", " ");
		assertTrue(createSql.contains(
				"CREATE TABLE IF NOT EXISTS DOCUMENTS"), createSql);
		assertTrue(createSql.contains("ID UUID"), createSql);
		final String dropSql = sqlFactoryRegistry
				.createSql(table, SqlType.DROP).get(0).getSqlText()
				.replaceAll("\\s+", " ");
		assertTrue(dropSql.contains("DROP TABLE IF EXISTS DOCUMENTS"),
				dropSql);
	}
}
