/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.virtica.sql;

import com.sqlapp.data.db.sql.AbstractCreateProcedureFactory;
import com.sqlapp.data.db.dialect.virtica.util.VirticaSqlBuilder;
import com.sqlapp.data.schemas.Procedure;
import com.sqlapp.util.CommonUtils;

/** USER_PROCEDURES does not expose a procedure body. */
public class VirticaCreateProcedureFactory extends AbstractCreateProcedureFactory<VirticaSqlBuilder> {
	@Override
	protected void addCreateObject(Procedure procedure, VirticaSqlBuilder builder) {
		if (CommonUtils.isEmpty(procedure.getDefinition())
				|| procedure.getDefinition().stream().allMatch(s -> s == null || s.isBlank()))
			throw new IllegalArgumentException(
					"Vertica procedure requires complete executable CREATE definition; USER_PROCEDURES has no body: "
							+ procedure.getName());
		builder._add(procedure.getDefinition());
	}
}
