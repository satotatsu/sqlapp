/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.informix.sql;

import com.sqlapp.data.db.sql.AbstractCreateFunctionFactory;
import com.sqlapp.data.db.dialect.informix.util.InformixSqlBuilder;
import com.sqlapp.data.schemas.Function;

/**
 * Reuses complete catalog CREATE text rather than synthesizing SPL from partial
 * arguments.
 */
public class InformixCreateFunctionFactory extends AbstractCreateFunctionFactory<InformixSqlBuilder> {
	@Override
	protected void addCreateObject(final Function function, final InformixSqlBuilder builder) {
		if (function.getDefinition().isEmpty() || function.getDefinition().stream().allMatch(String::isBlank))
			throw new IllegalArgumentException(
					"Complete Informix CREATE FUNCTION definition is required: " + function.getName());
		super.addCreateObject(function, builder);
	}

	@Override
	protected void addOtherDefinitions(final Function function,
			final java.util.List<com.sqlapp.data.db.sql.SqlOperation> result) {
		for (String sql : InformixRoutineAlterSql
				.split(function.getSpecifics().get(com.sqlapp.data.db.dialect.informix.Informix.ROUTINE_ALTER)))
			addSql(result, createSqlBuilder()._add(sql), com.sqlapp.data.db.sql.SqlType.ALTER, function);
	}
}
