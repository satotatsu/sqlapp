/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-virtica.
 */
package com.sqlapp.data.db.dialect.virtica.sql;

import com.sqlapp.data.db.dialect.virtica.util.VirticaSqlBuilder;
import com.sqlapp.data.db.sql.AbstractDropSequenceFactory;
import com.sqlapp.data.schemas.Sequence;

/**
 * Drops a Vertica named sequence idempotently.
 */
public class VirticaDropSequenceFactory
		extends AbstractDropSequenceFactory<VirticaSqlBuilder> {

	@Override
	protected void addDropObject(final Sequence obj,
			final VirticaSqlBuilder builder) {
		builder.drop().sequence().ifExists(
				this.getOptions().isDropIfExists());
		builder.name(obj, this.getOptions().isDecorateSchemaName());
	}
}
