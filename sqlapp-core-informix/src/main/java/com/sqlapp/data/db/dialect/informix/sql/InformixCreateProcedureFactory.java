/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.informix.sql;

import com.sqlapp.data.db.sql.AbstractCreateProcedureFactory;
import com.sqlapp.data.db.dialect.informix.util.InformixSqlBuilder;
import com.sqlapp.data.schemas.Procedure;

/**
 * Reuses complete catalog CREATE text rather than synthesizing SPL from partial
 * arguments.
 */
public class InformixCreateProcedureFactory extends AbstractCreateProcedureFactory<InformixSqlBuilder> {
	@Override
	protected void addCreateObject(final Procedure procedure, final InformixSqlBuilder builder) {
		if (procedure.getDefinition().isEmpty() || procedure.getDefinition().stream().allMatch(String::isBlank))
			throw new IllegalArgumentException(
					"Complete Informix CREATE PROCEDURE definition is required: " + procedure.getName());
		super.addCreateObject(procedure, builder);
	}

	@Override
	protected void addOtherDefinitions(final Procedure procedure,
			final java.util.List<com.sqlapp.data.db.sql.SqlOperation> result) {
		for (String sql : InformixRoutineAlterSql
				.split(procedure.getSpecifics().get(com.sqlapp.data.db.dialect.informix.Informix.ROUTINE_ALTER)))
			addSql(result, createSqlBuilder()._add(sql), com.sqlapp.data.db.sql.SqlType.ALTER, procedure);
	}
}
