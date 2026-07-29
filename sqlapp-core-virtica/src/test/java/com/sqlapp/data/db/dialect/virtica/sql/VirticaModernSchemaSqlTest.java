/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-virtica.
 */
package com.sqlapp.data.db.dialect.virtica.sql;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.Sequence;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.View;

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

	@Test
	void testCreateSequence() {
		final Sequence sequence = new Sequence("ORDER_SEQ");
		sequence.setDialect(dialect);
		sequence.setStartValue(BigInteger.valueOf(100));
		sequence.setIncrementBy(BigInteger.TEN);
		sequence.setMinValue(BigInteger.valueOf(100));
		sequence.setMaxValue(BigInteger.valueOf(999999));
		sequence.setCacheSize(100);
		sequence.setCycle(true);
		final String sql = sqlFactoryRegistry
				.createSql(sequence, SqlType.CREATE).get(0).getSqlText()
				.replaceAll("\\s+", " ");
		assertTrue(sql.contains("CREATE SEQUENCE IF NOT EXISTS ORDER_SEQ"),
				sql);
		assertTrue(sql.contains("START WITH 100"), sql);
		assertTrue(sql.contains("INCREMENT BY 10"), sql);
		assertTrue(sql.contains("MAXVALUE 999999"), sql);
		assertTrue(sql.contains("MINVALUE 100"), sql);
		assertTrue(sql.contains("CYCLE"), sql);
		assertTrue(sql.contains("CACHE 100"), sql);
	}

	@Test
	void testSequenceNextValues() {
		final Sequence sequence = new Sequence("ORDER_SEQ");
		new Schema("PUBLIC").getSequences().add(sequence);
		sequence.setDialect(dialect);
		final String sql = sqlFactoryRegistry
				.createSql(sequence, SqlType.SEQUENCE_NEXT_VALUES).get(0)
				.getSqlText().replaceAll("\\s+", " ");
		assertTrue(sql.contains(
				"WITH RECURSIVE SQLAPP_SEQUENCE_ROWS(N) AS (SELECT 1 UNION ALL SELECT N + 1 FROM SQLAPP_SEQUENCE_ROWS WHERE N < /*context*/1)"),
				sql);
		assertTrue(sql.contains(
				"SELECT NEXTVAL('PUBLIC.ORDER_SEQ') FROM SQLAPP_SEQUENCE_ROWS"),
				sql);
	}

	@Test
	void testCreateAndDropView() {
		final View view = new View("ACTIVE_DOCUMENTS");
		view.setDialect(dialect);
		view.setStatement(
				"SELECT ID FROM DOCUMENTS WHERE ACTIVE = TRUE");
		final String createSql = sqlFactoryRegistry
				.createSql(view, SqlType.CREATE).get(0).getSqlText()
				.replaceAll("\\s+", " ");
		assertTrue(createSql.contains(
				"CREATE OR REPLACE VIEW ACTIVE_DOCUMENTS AS SELECT ID FROM DOCUMENTS WHERE ACTIVE = TRUE"),
				createSql);
		final String dropSql = sqlFactoryRegistry
				.createSql(view, SqlType.DROP).get(0).getSqlText()
				.replaceAll("\\s+", " ");
		assertTrue(dropSql.contains(
				"DROP VIEW IF EXISTS ACTIVE_DOCUMENTS"), dropSql);
	}
}
