/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-oracle.
 */
package com.sqlapp.data.db.dialect.oracle.sql;

import com.sqlapp.data.db.dialect.oracle.metadata.OracleSequenceReader;
import com.sqlapp.data.db.dialect.oracle.util.OracleSqlBuilder;
import com.sqlapp.data.schemas.Sequence;

/**
 * CREATE SEQUENCE factory for Oracle Database 18c scalable and session
 * sequences.
 */
public class Oracle18cCreateSequenceFactory extends OracleCreateSequenceFactory {

	@Override
	protected void addOptions(final Sequence sequence, final OracleSqlBuilder builder) {
		super.addOptions(sequence, builder);
		if (isTrue(sequence, OracleSequenceReader.SCALE)) {
			builder.space()._add("SCALE").space();
			if (isTrue(sequence, OracleSequenceReader.EXTEND)) {
				builder._add("EXTEND");
			} else {
				builder._add("NOEXTEND");
			}
		}
		if (isTrue(sequence, OracleSequenceReader.SESSION)) {
			builder.space()._add("SESSION");
		}
	}

	private boolean isTrue(final Sequence sequence, final String key) {
		return Boolean.parseBoolean(sequence.getSpecifics().get(key));
	}
}
