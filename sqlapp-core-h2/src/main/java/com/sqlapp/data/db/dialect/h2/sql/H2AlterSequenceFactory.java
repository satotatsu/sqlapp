/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.h2.sql;

import com.sqlapp.data.db.dialect.h2.H2_200;
import com.sqlapp.data.db.dialect.h2.util.H2SqlBuilder;
import com.sqlapp.data.db.sql.StandardAlterSequenceFactory;
import com.sqlapp.data.schemas.Sequence;

/** H2 ALTER SEQUENCE; bounds and CYCLE exist from 2.0, matching CREATE. */
public class H2AlterSequenceFactory extends StandardAlterSequenceFactory<H2SqlBuilder> {
	@Override
	protected void addMinValue(final Sequence sequence, final H2SqlBuilder builder) {
		requireModern(sequence, "MINVALUE");
		super.addMinValue(sequence, builder);
	}

	@Override
	protected void addMaxValue(final Sequence sequence, final H2SqlBuilder builder) {
		requireModern(sequence, "MAXVALUE");
		super.addMaxValue(sequence, builder);
	}

	@Override
	protected void addCycle(final Sequence sequence, final H2SqlBuilder builder) {
		requireModern(sequence, "CYCLE");
		super.addCycle(sequence, builder);
	}

	private void requireModern(final Sequence sequence, final String clause) {
		if (!(getDialect() instanceof H2_200)) {
			throw new IllegalArgumentException(
					"Sequence " + clause + " requires H2 2.0 or later: " + sequence.getName());
		}
	}
}
