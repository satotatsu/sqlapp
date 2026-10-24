/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.saphana.sql;

import com.sqlapp.data.db.dialect.saphana.util.SapHanaSqlBuilder;
import com.sqlapp.data.db.sql.AbstractCreateProcedureFactory;
import com.sqlapp.data.schemas.Procedure;
import com.sqlapp.util.CommonUtils;

/** Creates modeled SQLScript procedures or replays complete catalog DDL. */
public class SapHanaCreateProcedureFactory extends AbstractCreateProcedureFactory<SapHanaSqlBuilder> {
	@Override
	protected void addCreateObject(final Procedure procedure, final SapHanaSqlBuilder builder) {
		if (!CommonUtils.isEmpty(procedure.getDefinition())) {
			super.addCreateObject(procedure, builder);
			return;
		}
		builder.create().procedure().name(procedure, getOptions().isDecorateSchemaName())
				.arguments(procedure.getArguments());
		SapHanaRoutineBody.append(toString(procedure.getStatement()), builder);
	}
}
