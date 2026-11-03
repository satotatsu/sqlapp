/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-virtica.
 */
package com.sqlapp.data.db.dialect.vertica.sql;

import com.sqlapp.data.db.dialect.vertica.util.VerticaSqlBuilder;
import com.sqlapp.data.db.sql.AbstractCreateSequenceFactory;
import com.sqlapp.data.schemas.Sequence;

/**
 * Creates a Vertica named sequence.
 */
public class VerticaCreateSequenceFactory extends AbstractCreateSequenceFactory<VerticaSqlBuilder> {

	@Override
	protected void addIfNotExists(final Sequence obj, final VerticaSqlBuilder builder) {
		builder.ifNotExists(this.getOptions().isCreateIfNotExists());
	}

	@Override
	protected void addDataType(final Sequence obj, final VerticaSqlBuilder builder) {
		// Vertica named sequences do not have an AS data-type clause.
	}

	@Override
	protected void addCache(final Sequence obj, final VerticaSqlBuilder builder) {
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
	protected void addOrder(final Sequence obj, final VerticaSqlBuilder builder) {
		// ORDER/NO ORDER is not part of Vertica CREATE SEQUENCE.
	}

	@Override
	protected void addOptions(final Sequence obj, VerticaSqlBuilder builder) {
		if (!obj.isCache() && obj.getCacheSize() == null) {
			builder.space()._add("NO CACHE");
		}
	}

}
