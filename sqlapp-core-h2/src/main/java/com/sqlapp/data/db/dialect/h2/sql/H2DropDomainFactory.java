/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-h2.
 */
package com.sqlapp.data.db.dialect.h2.sql;

import com.sqlapp.data.db.dialect.h2.util.H2SqlBuilder;
import com.sqlapp.data.db.sql.AbstractDropNamedObjectFactory;
import com.sqlapp.data.schemas.Domain;

/**
 * Drops an H2 domain idempotently.
 */
public class H2DropDomainFactory
		extends AbstractDropNamedObjectFactory<Domain, H2SqlBuilder> {

	@Override
	protected void addDropObject(final Domain obj,
			final H2SqlBuilder builder) {
		builder.drop().domain()
				.ifExists(this.getOptions().isDropIfExists())
				.name(obj, this.getOptions().isDecorateSchemaName());
	}
}
