/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2.common;

import com.sqlapp.data.db.dialect.db2.util.Db2SqlBuilder;
import com.sqlapp.data.db.sql.AbstractSequenceNextValuesFactory;
import com.sqlapp.data.schemas.Sequence;

/**
 * Fetches n NEXT VALUEs with a recursive common table expression over
 * SYSIBM.SYSDUMMY1. Host ordinary identifiers cannot start with an underscore,
 * so the generator CTE uses a regular name.
 */
public class Db2HostSequenceNextValuesFactory extends AbstractSequenceNextValuesFactory<Db2SqlBuilder> {
	private static final String GENERATOR = "SQLAPP_SEQ_N";

	@Override
	protected void addSequenceNextValues(final Sequence sequence, final Db2SqlBuilder builder) {
		builder._add("WITH " + GENERATOR + " (N) AS (");
		builder.lineBreak()._add("SELECT 1 AS N FROM SYSIBM.SYSDUMMY1");
		builder.lineBreak()._add("UNION ALL");
		builder.lineBreak()._add("SELECT N + 1 FROM " + GENERATOR + " WHERE N < ");
		builder._add(getColumnParameterExpression(getCountParameterName(sequence), "1"));
		builder.lineBreak()._add(")");
		builder.lineBreak()._add("SELECT NEXT VALUE FOR ").name(sequence, true);
		builder.lineBreak()._add("FROM " + GENERATOR);
	}
}
