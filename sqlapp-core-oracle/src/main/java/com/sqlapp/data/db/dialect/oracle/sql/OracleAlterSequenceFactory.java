/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.oracle.sql;

import java.math.BigInteger;

import com.sqlapp.data.db.dialect.oracle.Oracle18c;
import com.sqlapp.data.db.dialect.oracle.util.OracleSqlBuilder;
import com.sqlapp.data.db.sql.StandardAlterSequenceFactory;
import com.sqlapp.data.schemas.Sequence;

/**
 * Oracle ALTER SEQUENCE. The next value can be changed in place from 18c with
 * {@code RESTART START WITH}; earlier versions require recreation.
 */
public class OracleAlterSequenceFactory extends StandardAlterSequenceFactory<OracleSqlBuilder> {
	@Override
	public boolean supportsRestart() {
		return getDialect() instanceof Oracle18c;
	}

	@Override
	protected void addMinValue(final Sequence sequence, final OracleSqlBuilder builder) {
		if (sequence.getMinValue() == null) {
			builder.space()._add("NOMINVALUE");
		} else {
			super.addMinValue(sequence, builder);
		}
	}

	@Override
	protected void addMaxValue(final Sequence sequence, final OracleSqlBuilder builder) {
		if (sequence.getMaxValue() == null) {
			builder.space()._add("NOMAXVALUE");
		} else {
			super.addMaxValue(sequence, builder);
		}
	}

	@Override
	protected void addCycle(final Sequence sequence, final OracleSqlBuilder builder) {
		builder.space()._add(sequence.isCycle() ? "CYCLE" : "NOCYCLE");
	}

	@Override
	protected void addCache(final Sequence sequence, final OracleSqlBuilder builder) {
		final Integer size = sequence.getCacheSize();
		if (!sequence.isCache() || size == null || size <= 1) {
			builder.space()._add("NOCACHE");
		} else {
			builder.space()._add("CACHE ")._add(size);
		}
	}

	@Override
	protected void addRestart(final Sequence sequence, final BigInteger next, final OracleSqlBuilder builder) {
		if (!supportsRestart()) {
			super.addRestart(sequence, next, builder);
		}
		builder.space()._add("RESTART START WITH ")._add(next);
	}
}
