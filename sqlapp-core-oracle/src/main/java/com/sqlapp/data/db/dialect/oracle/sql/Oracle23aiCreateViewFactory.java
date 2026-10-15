/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-oracle.
 */
package com.sqlapp.data.db.dialect.oracle.sql;

import static com.sqlapp.util.CommonUtils.isEmpty;

import com.sqlapp.data.db.sql.CreateViewFactory;
import com.sqlapp.data.schemas.View;
import com.sqlapp.util.AbstractSqlBuilder;

/**
 * Oracle Database 23ai CREATE VIEW extensions.
 */
public class Oracle23aiCreateViewFactory extends CreateViewFactory {

	@Override
	protected void addCreateObject(final View view, final AbstractSqlBuilder<?> builder) {
		final Boolean replication = logicalReplication(view);
		if (replication != null && (!supportsLogicalReplication()
				|| !OracleJsonDualityViewUtils.isJsonRelationalDualityView(view))) {
			throw new IllegalArgumentException("LOGICAL_REPLICATION requires an Oracle 26ai JSON relational duality view: "
					+ view.getName());
		}
		if (!OracleJsonDualityViewUtils.isJsonRelationalDualityView(view)) {
			super.addCreateObject(view, builder);
			return;
		}
		if (!isEmpty(view.getDefinition())) {
			if (replication != null) {
				throw new IllegalArgumentException("Use statement with LOGICAL_REPLICATION instead of a complete definition: "
						+ view.getName());
			}
			builder._add(view.getDefinition());
			return;
		}
		if (isEmpty(view.getStatement())) {
			throw new IllegalArgumentException(
					"JSON relational duality view statement must not be empty: " + view.getName());
		}
		builder.create().space()._add("JSON RELATIONAL DUALITY VIEW").space()
				.ifNotExists(getOptions().isCreateIfNotExists()).space().name(view, getOptions().isDecorateSchemaName())
				.lineBreak().as();
		if (replication != null) {
			builder.space()._add(replication ? "ENABLE LOGICAL REPLICATION" : "DISABLE LOGICAL REPLICATION");
		}
		builder.lineBreak()._add(view.getStatement());
	}

	protected boolean supportsLogicalReplication() {
		return false;
	}

	private Boolean logicalReplication(final View view) {
		final String value = view.getSpecifics().get(OracleJsonDualityViewUtils.LOGICAL_REPLICATION);
		if (value == null) {
			return null;
		}
		if ("true".equalsIgnoreCase(value)) {
			return true;
		}
		if ("false".equalsIgnoreCase(value)) {
			return false;
		}
		throw new IllegalArgumentException("LOGICAL_REPLICATION must be true or false: " + view.getName());
	}
}
