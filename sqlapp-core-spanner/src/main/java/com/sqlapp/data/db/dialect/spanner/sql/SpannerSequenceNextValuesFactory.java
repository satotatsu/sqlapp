/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-spanner.
 */
package com.sqlapp.data.db.dialect.spanner.sql;

import com.sqlapp.data.db.dialect.spanner.util.SpannerSqlBuilder;
import com.sqlapp.data.db.sql.AbstractSequenceNextValuesFactory;
import com.sqlapp.data.schemas.Sequence;

/**
 * Fetches one or more values from a Cloud Spanner sequence.
 */
public class SpannerSequenceNextValuesFactory
		extends AbstractSequenceNextValuesFactory<SpannerSqlBuilder> {

	@Override
	protected void addSequenceNextValues(final Sequence sequence,
			final SpannerSqlBuilder builder) {
		builder.select().space()
				._add("GET_NEXT_SEQUENCE_VALUE")._add("(")
				._add("SEQUENCE").space().name(sequence)._add(")");
		builder.lineBreak().from().space()
				._add("UNNEST")._add("(")
				._add("GENERATE_ARRAY")._add("(")._add(1).comma()
				._add(getColumnParameterExpression(
						getCountParameterName(sequence), "1"))
				._add(")")._add(")");
	}
}
