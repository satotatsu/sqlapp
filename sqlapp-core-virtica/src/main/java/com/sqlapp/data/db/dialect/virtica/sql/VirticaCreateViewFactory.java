/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-virtica.
 */
package com.sqlapp.data.db.dialect.virtica.sql;

import com.sqlapp.data.db.dialect.virtica.util.VirticaSqlBuilder;
import com.sqlapp.data.db.sql.AbstractCreateViewFactory;
import com.sqlapp.data.schemas.View;

/**
 * Creates a Vertica view without dropping its existing grants.
 */
public class VirticaCreateViewFactory
		extends AbstractCreateViewFactory<VirticaSqlBuilder> {

	@Override
	protected void createObject(final View obj,
			final VirticaSqlBuilder builder) {
		builder.create().or().replace().view();
	}
}
