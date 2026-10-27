/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.firebird.sql;

import com.sqlapp.data.db.dialect.firebird.util.FirebirdSqlBuilder;
import com.sqlapp.data.schemas.Function;
import com.sqlapp.util.CommonUtils;

/**
 * Firebird 3.0+ PSQL/UDR functions, with legacy UDF declarations delegated to
 * the base factory.
 */
public class Firebird30CreateFunctionFactory extends FirebirdCreateFunctionFactory {
	@Override
	protected void addOtherDefinitions(Function obj, java.util.List<com.sqlapp.data.db.sql.SqlOperation> result) {
		if (obj.getRemarks() != null)
			result.addAll(getSqlFactoryRegistry().createSql(obj, com.sqlapp.data.db.sql.SqlType.SET_COMMENT));
		if (FirebirdExternalFunctionSql.legacy(obj, getDialect()))
			return;
		for (var argument : obj.getArguments()) {
			if (argument.getRemarks() != null)
				addSql(result, FirebirdRoutineSql.argumentComment(obj, argument, createSqlBuilder()),
						com.sqlapp.data.db.sql.SqlType.SET_COMMENT, argument);
		}
	}

	@Override
	protected void addCreateObject(Function obj, FirebirdSqlBuilder builder) {
		if (FirebirdExternalFunctionSql.legacy(obj, getDialect())) {
			super.addCreateObject(obj, builder);
			return;
		}
		if (!CommonUtils.isEmpty(obj.getDefinition())) {
			builder._add(obj.getDefinition());
			return;
		}
		builder.create().function().name(obj, false);
		if (!obj.getArguments().isEmpty()) {
			builder.space()._add("(");
			for (int i = 0; i < obj.getArguments().size(); i++) {
				var argument = obj.getArguments().get(i);
				if (argument.getDirection() != com.sqlapp.jdbc.sql.ParameterDirection.Input)
					throw new IllegalArgumentException("Firebird function arguments must be input parameters");
				builder.comma(i > 0).argument(argument);
			}
			builder._add(")");
		}
		builder.lineBreak().returns().space();
		var reference = com.sqlapp.data.db.dialect.firebird.FirebirdRoutineTypeReference
				.get(obj.getReturning().getSpecifics());
		if (reference == null)
			builder._add(obj.getReturning());
		else
			reference.appendTo(builder);
		if (reference == null && obj.getReturning().getCharacterSet() != null)
			builder._add(" CHARACTER SET ").name(obj.getReturning().getCharacterSet());
		String notNull = obj.getReturning().getSpecifics().get("firebird.notNull");
		if (notNull != null && !java.util.Set.of("true", "false").contains(notNull))
			throw new IllegalArgumentException("Function return firebird.notNull must be true or false");
		if ("true".equals(notNull))
			builder._add(" NOT NULL");
		if (obj.getReturning().getCollation() != null)
			builder._add(" COLLATE ").name(obj.getReturning().getCollation());
		if (Boolean.TRUE.equals(obj.getDeterministic()))
			builder.lineBreak()._add("DETERMINISTIC");
		FirebirdRoutineSql.addSecurity(obj.getSqlSecurity(), builder);
		if (!FirebirdRoutineSql.addExternal(obj.getSpecifics(), builder))
			FirebirdRoutineSql.addBody(obj.getStatement(), builder);
	}
}
