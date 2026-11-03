/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-virtica.
 */
package com.sqlapp.data.db.dialect.vertica.sql;

import com.sqlapp.data.db.dialect.vertica.util.VerticaSqlBuilder;
import com.sqlapp.data.db.sql.AbstractDropViewFactory;
import com.sqlapp.data.schemas.View;

/**
 * Drops a Vertica view idempotently.
 */
public class VerticaDropViewFactory extends AbstractDropViewFactory<VerticaSqlBuilder> {

	@Override
	protected void addDropObject(final View obj, final VerticaSqlBuilder builder) {
		builder.drop().view().ifExists(this.getOptions().isDropIfExists());
		builder.name(obj, this.getOptions().isDecorateSchemaName());
	}
}
