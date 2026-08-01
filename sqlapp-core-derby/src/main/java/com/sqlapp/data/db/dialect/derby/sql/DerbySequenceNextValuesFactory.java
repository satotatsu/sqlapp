/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-derby.
 */
package com.sqlapp.data.db.dialect.derby.sql;

import com.sqlapp.data.db.dialect.derby.util.DerbySqlBuilder;
import com.sqlapp.data.db.sql.AbstractSequenceNextValuesFactory;
import com.sqlapp.data.schemas.Sequence;

/** Generates multiple Derby sequence values in one recursive query. */
public class DerbySequenceNextValuesFactory extends AbstractSequenceNextValuesFactory<DerbySqlBuilder> {

	@Override
	protected void addSequenceNextValues(final Sequence obj, final DerbySqlBuilder builder) {
		builder.select().next().value().for_().name(obj);
		builder.lineBreak().from()._add(" SYS.SYSTABLES T1, SYS.SYSCOLUMNS T2, SYS.SYSTABLES T3");
		builder.lineBreak().fetch().first().space()
				._add(getColumnParameterExpression(getCountParameterName(obj), "1")).rows().only();
	}
}
