/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-h2.
 */
package com.sqlapp.data.db.dialect.h2.sql;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Domain;

class H2DomainSqlTest extends AbstractH2SqlFactoryTest {

	@Test
	void testCreateAndDropDomain() {
		final Domain domain = new Domain("POSITIVE_AMOUNT");
		domain.setDialect(dialect);
		domain.setDataType(DataType.DECIMAL);
		domain.setLength(18);
		domain.setScale(2);
		domain.setDefaultValue("0");
		domain.setNullable(false);
		domain.setCheck("VALUE >= 0");
		final String createSql = sqlFactoryRegistry
				.createSql(domain, SqlType.CREATE).get(0).getSqlText()
				.replaceAll("\\s+", " ");
		assertTrue(createSql.contains(
				"CREATE DOMAIN IF NOT EXISTS POSITIVE_AMOUNT AS DECIMAL(18,2) DEFAULT 0 NOT NULL CHECK (VALUE >= 0)"),
				createSql);
		final String dropSql = sqlFactoryRegistry
				.createSql(domain, SqlType.DROP).get(0).getSqlText()
				.replaceAll("\\s+", " ");
		assertTrue(dropSql.contains(
				"DROP DOMAIN IF EXISTS POSITIVE_AMOUNT"), dropSql);
	}
}
