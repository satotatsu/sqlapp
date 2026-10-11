/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.informix.sql;

import com.sqlapp.data.db.sql.AbstractCreateSequenceFactory;
import com.sqlapp.data.schemas.Sequence;
import com.sqlapp.util.AbstractSqlBuilder;

/** Informix sequence syntax, including explicit cache disabling. */
public class InformixCreateSequenceFactory extends AbstractCreateSequenceFactory<AbstractSqlBuilder<?>> {
	@Override
	protected void addDataType(final Sequence obj, AbstractSqlBuilder<?> builder) {
		// Informix sequences are signed 64-bit integers and have no AS clause.
	}
	@Override
	protected void addCache(final Sequence obj, AbstractSqlBuilder<?> builder) {
		if (obj.getCacheSize() < 0) {
			throw new IllegalArgumentException("Sequence cache size must not be negative");
		}
		if (!obj.isCache() || obj.getCacheSize() == 0) {
			builder.space()._add("NOCACHE");
		} else if (obj.getCacheSize() == 1) {
			throw new IllegalArgumentException("Informix sequence cache size must be at least 2, or 0 for NOCACHE");
		} else {
			super.addCache(obj, builder);
		}
	}
	@Override
	protected void addOptions(final Sequence obj, AbstractSqlBuilder<?> builder) {
		if (!obj.isCache() && obj.getCacheSize() == null) {
			builder.space()._add("NOCACHE");
		}
	}

}
