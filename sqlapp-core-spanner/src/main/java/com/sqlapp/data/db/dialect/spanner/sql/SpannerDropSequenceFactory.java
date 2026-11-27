/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.spanner.sql;

import com.sqlapp.data.db.dialect.spanner.util.SpannerSqlBuilder;
import com.sqlapp.data.db.sql.AbstractDropSequenceFactory;
import com.sqlapp.data.schemas.Sequence;

/** GoogleSQL DROP SEQUENCE. */
public class SpannerDropSequenceFactory extends AbstractDropSequenceFactory<SpannerSqlBuilder> {
	@Override
	protected void addDropObject(final Sequence sequence, final SpannerSqlBuilder builder) {
		if (sequence.getName() == null || sequence.getName().isBlank())
			throw new IllegalArgumentException("Spanner DROP SEQUENCE requires a name");
		builder.drop().sequence().ifExists(getOptions().isDropIfExists())
				.name(sequence, getOptions().isDecorateSchemaName());
	}
}
