/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.firebird.sql;

import com.sqlapp.data.db.dialect.firebird.util.FirebirdSqlBuilder;
import com.sqlapp.data.db.sql.AbstractCreateProcedureFactory;
import com.sqlapp.data.schemas.Procedure;
import com.sqlapp.jdbc.sql.ParameterDirection;
import com.sqlapp.util.CommonUtils;

/** Recreates PSQL procedures from their body and input/output arguments. */
public class FirebirdCreateProcedureFactory extends AbstractCreateProcedureFactory<FirebirdSqlBuilder> {
	@Override
	protected void addOtherDefinitions(Procedure obj, java.util.List<com.sqlapp.data.db.sql.SqlOperation> result) {
		for (var argument : obj.getArguments()) {
			if (argument.getRemarks() != null)
				addSql(result, FirebirdRoutineSql.argumentComment(obj, argument, createSqlBuilder()),
						com.sqlapp.data.db.sql.SqlType.SET_COMMENT, argument);
		}
	}

	@Override
	protected void addCreateObject(Procedure obj, FirebirdSqlBuilder builder) {
		if (!CommonUtils.isEmpty(obj.getDefinition())) {
			builder._add(obj.getDefinition());
			return;
		}
		builder.create().procedure().name(obj, false);
		for (var argument : obj.getArguments()) {
			if (argument.getDirection() == ParameterDirection.Inout) {
				throw new IllegalArgumentException("Firebird procedures require separate input and output parameters");
			}
		}
		addArguments(obj, false, builder);
		addArguments(obj, true, builder);
		FirebirdRoutineSql.addSecurity(obj.getSqlSecurity(), builder);
		if (!FirebirdRoutineSql.addExternal(obj.getSpecifics(), builder))
			FirebirdRoutineSql.addBody(obj.getStatement(), builder);
	}

	private void addArguments(Procedure obj, boolean output, FirebirdSqlBuilder builder) {
		var arguments = obj.getArguments().stream()
				.filter(a -> (a.getDirection() == ParameterDirection.Output) == output).toList();
		if (arguments.isEmpty()) {
			return;
		}
		if (output) {
			builder.lineBreak().returns();
		}
		builder.space()._add("(");
		for (int i = 0; i < arguments.size(); i++) {
			var argument = arguments.get(i);
			builder.comma(i > 0).argument(argument);
		}
		builder._add(")");
	}
}
