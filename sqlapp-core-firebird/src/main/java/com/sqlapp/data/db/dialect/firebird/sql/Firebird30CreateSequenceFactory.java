/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.firebird.sql;

import com.sqlapp.data.db.dialect.firebird.util.FirebirdSqlBuilder;
import com.sqlapp.data.schemas.Sequence;

/** Firebird 3.0+ supports sequence start and increment clauses. */
public class Firebird30CreateSequenceFactory extends Firebird20CreateSequenceFactory {
	@Override
	protected void addStartWith(final Sequence obj, FirebirdSqlBuilder builder) {
		builder.start().with().space()._add(obj.getStartValue());
	}
	@Override
	protected void addIncrementBy(final Sequence obj, FirebirdSqlBuilder builder) {
		builder.increment().by().space()._add(obj.getIncrementBy());
	}
}
