/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.informix.sql;

import com.sqlapp.data.db.dialect.informix.util.InformixSqlBuilder;
import com.sqlapp.data.db.sql.AbstractSequenceNextValuesFactory;
import com.sqlapp.data.schemas.Sequence;

/**
 * NEXTVAL once per returned row, without relying on catalog row counts or a
 * helper routine.
 */
public class InformixSequenceNextValuesFactory extends AbstractSequenceNextValuesFactory<InformixSqlBuilder> {
	@Override
	protected void addSequenceNextValues(final Sequence sequence, final InformixSqlBuilder builder) {
		if (sequence.getName() == null || sequence.getName().isBlank())
			throw new IllegalArgumentException("Informix sequence name is required");
		builder.select().space()._add("FIRST ")._add(getColumnParameterExpression(getCountParameterName(sequence), "1"))
				.space();
		if (sequence.getSchemaName() != null && !sequence.getSchemaName().isEmpty()) {
			identifier(builder, sequence.getSchemaName());
			builder._add('.');
		}
		identifier(builder, sequence.getName());
		builder._add(".NEXTVAL");
		builder.lineBreak().from().space();
		// The JDBC batch size matches this 100-row source.
		builder._add("TABLE(SET{0,1,2,3,4,5,6,7,8,9}) AS _d0(n), ");
		builder._add("TABLE(SET{0,1,2,3,4,5,6,7,8,9}) AS _d1(n)");
	}

	private void identifier(final InformixSqlBuilder builder, final String name) {
		if (getOptions().isQuateObjectName())
			builder._add("\"" + name.replace("\"", "\"\"") + "\"");
		else
			builder.name(name);
	}
}
