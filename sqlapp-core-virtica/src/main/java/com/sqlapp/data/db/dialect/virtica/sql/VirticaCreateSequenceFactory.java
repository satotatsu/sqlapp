/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-virtica.
 */
package com.sqlapp.data.db.dialect.virtica.sql;

import com.sqlapp.data.db.dialect.virtica.util.VirticaSqlBuilder;
import com.sqlapp.data.db.sql.AbstractCreateSequenceFactory;
import com.sqlapp.data.schemas.Sequence;

/**
 * Creates a Vertica named sequence.
 */
public class VirticaCreateSequenceFactory extends AbstractCreateSequenceFactory<VirticaSqlBuilder> {

	@Override
	protected void addIfNotExists(final Sequence obj, final VirticaSqlBuilder builder) {
		builder.ifNotExists(this.getOptions().isCreateIfNotExists());
	}

	@Override
	protected void addDataType(final Sequence obj, final VirticaSqlBuilder builder) {
		// Vertica named sequences do not have an AS data-type clause.
	}

	@Override
	protected void addCache(final Sequence obj, final VirticaSqlBuilder builder) {
		if (obj.getCacheSize() < 0) {
			throw new IllegalArgumentException("Sequence cache size must not be negative");
		}
		if (!obj.isCache() || obj.getCacheSize() <= 1) {
			builder.space()._add("NO CACHE");
		} else {
			super.addCache(obj, builder);
		}
	}

	@Override
	protected void addOrder(final Sequence obj, final VirticaSqlBuilder builder) {
		// ORDER/NO ORDER is not part of Vertica CREATE SEQUENCE.
	}

	@Override
	protected void addOptions(final Sequence obj, VirticaSqlBuilder builder) {
		if (!obj.isCache() && obj.getCacheSize() == null) {
			builder.space()._add("NO CACHE");
		}
	}

}
