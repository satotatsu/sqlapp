/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-oracle.
 */
package com.sqlapp.data.db.dialect.oracle.sql;

import com.sqlapp.data.db.sql.DropViewFactory;
import com.sqlapp.data.schemas.View;
import com.sqlapp.util.AbstractSqlBuilder;

/**
 * Oracle Database 23ai DROP VIEW extensions.
 */
public class Oracle23aiDropViewFactory extends DropViewFactory {

	@Override
	protected void addDropObject(final View view,
			final AbstractSqlBuilder<?> builder) {
		if (!OracleJsonDualityViewUtils.isJsonRelationalDualityView(view)) {
			super.addDropObject(view, builder);
			return;
		}
		builder.drop().view().space()
				.ifExists(getOptions().isDropIfExists()).space()
				.name(view, getOptions().isDecorateSchemaName());
	}
}
