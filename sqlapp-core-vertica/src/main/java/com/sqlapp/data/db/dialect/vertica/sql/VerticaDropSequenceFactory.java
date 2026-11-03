/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-virtica.
 */
package com.sqlapp.data.db.dialect.vertica.sql;

import com.sqlapp.data.db.dialect.vertica.util.VerticaSqlBuilder;
import com.sqlapp.data.db.sql.AbstractDropSequenceFactory;
import com.sqlapp.data.schemas.Sequence;

/**
 * Drops a Vertica named sequence idempotently.
 */
public class VerticaDropSequenceFactory extends AbstractDropSequenceFactory<VerticaSqlBuilder> {

	@Override
	protected void addDropObject(final Sequence obj, final VerticaSqlBuilder builder) {
		builder.drop().sequence().ifExists(this.getOptions().isDropIfExists());
		builder.name(obj, this.getOptions().isDecorateSchemaName());
	}
}
