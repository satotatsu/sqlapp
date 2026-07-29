/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-virtica.
 */
package com.sqlapp.data.db.dialect.virtica.sql;

import com.sqlapp.data.db.dialect.virtica.util.VirticaSqlBuilder;
import com.sqlapp.data.db.sql.AbstractAlterSequenceFactory;
import com.sqlapp.data.schemas.Sequence;

/**
 * Alters the behavior of a Vertica named sequence.
 */
public class VirticaAlterSequenceFactory
		extends AbstractAlterSequenceFactory<VirticaSqlBuilder> {

	@Override
	protected void addCreateObject(final Sequence obj,
			final VirticaSqlBuilder builder) {
		builder.alter().sequence().name(obj,
				this.getOptions().isDecorateSchemaName());
		if (obj.getIncrementBy() != null) {
			builder.increment().by().space()._add(obj.getIncrementBy());
		}
		if (obj.getMinValue() != null) {
			builder.minvalue().space()._add(obj.getMinValue());
		}
		if (obj.getMaxValue() != null) {
			builder.maxvalue().space()._add(obj.getMaxValue());
		}
		if (obj.getStartValue() != null) {
			builder.space()._add("RESTART").with().space()
					._add(obj.getStartValue());
		}
		if (obj.getCacheSize() != null) {
			builder.cache().space()._add(obj.getCacheSize());
		}
		if (obj.isCycle()) {
			builder.cycle();
		} else {
			builder.space()._add("NO CYCLE");
		}
	}
}
