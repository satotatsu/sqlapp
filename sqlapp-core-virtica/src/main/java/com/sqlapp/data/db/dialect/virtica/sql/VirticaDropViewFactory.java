/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-virtica.
 */
package com.sqlapp.data.db.dialect.virtica.sql;

import com.sqlapp.data.db.dialect.virtica.util.VirticaSqlBuilder;
import com.sqlapp.data.db.sql.AbstractDropViewFactory;
import com.sqlapp.data.schemas.View;

/**
 * Drops a Vertica view idempotently.
 */
public class VirticaDropViewFactory
		extends AbstractDropViewFactory<VirticaSqlBuilder> {

	@Override
	protected void addDropObject(final View obj,
			final VirticaSqlBuilder builder) {
		builder.drop().view().ifExists(
				this.getOptions().isDropIfExists());
		builder.name(obj, this.getOptions().isDecorateSchemaName());
	}
}
