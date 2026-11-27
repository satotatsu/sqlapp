/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-spanner.
 */
package com.sqlapp.data.db.dialect.spanner.sql;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.View;

class SpannerCreateViewFactoryTest extends SpannerSqlFactoryTest {

	@Test
	void testCreateInvokerViewByDefault() {
		final View view = new View("ACTIVE_USERS");
		view.setDialect(dialect);
		view.setStatement("SELECT ID AS ID FROM USERS WHERE ACTIVE");

		final String sql = sqlFactoryRegistry.createSql(view, SqlType.CREATE).get(0).getSqlText().replaceAll("\\s+",
				" ");
		assertTrue(sql.contains(
				"CREATE VIEW ACTIVE_USERS SQL SECURITY INVOKER AS " + "SELECT ID AS ID FROM USERS WHERE ACTIVE"), sql);
	}

	@Test
	void testCreateDefinerView() {
		final View view = new View("ACTIVE_USERS");
		view.setDialect(dialect);
		view.setStatement("SELECT ID AS ID FROM USERS");
		view.getSpecifics().put(SpannerCreateViewFactory.SECURITY_TYPE, "DEFINER");

		final String sql = sqlFactoryRegistry.createSql(view, SqlType.CREATE).get(0).getSqlText().replaceAll("\\s+",
				" ");
		assertTrue(sql.contains("SQL SECURITY DEFINER"), sql);
	}

	@Test
	void testRejectUnsupportedSecurityType() {
		final View view = new View("ACTIVE_USERS");
		view.setDialect(dialect);
		view.setStatement("SELECT ID AS ID FROM USERS");
		view.getSpecifics().put(SpannerCreateViewFactory.SECURITY_TYPE, "UNSUPPORTED");

		assertThrows(IllegalArgumentException.class, () -> sqlFactoryRegistry.createSql(view, SqlType.CREATE));
	}

	@Test
	void testDropViewIfExists() {
		final View view = new View("ACTIVE_USERS");
		view.setDialect(dialect);

		final String sql = sqlFactoryRegistry.createSql(view, SqlType.DROP).get(0).getSqlText().replaceAll("\\s+", " ");
		assertTrue(sql.contains("DROP VIEW IF EXISTS ACTIVE_USERS"), sql);
	}
}
