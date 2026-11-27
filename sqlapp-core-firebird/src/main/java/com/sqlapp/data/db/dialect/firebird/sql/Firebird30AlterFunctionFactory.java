/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.firebird.sql;

public class Firebird30AlterFunctionFactory extends FirebirdAlterRoutineFactory<com.sqlapp.data.schemas.Function> {
	@Override
	public java.util.List<com.sqlapp.data.db.sql.SqlOperation> createDiffSql(
			com.sqlapp.data.schemas.DbObjectDifference difference) {
		if (difference.getOriginal() instanceof com.sqlapp.data.schemas.Function before
				&& difference.getTarget() instanceof com.sqlapp.data.schemas.Function after
				&& (FirebirdExternalFunctionSql.legacy(before, getDialect())
						|| FirebirdExternalFunctionSql.legacy(after, getDialect())))
			return FirebirdExternalFunctionSql.alter(difference, getSqlFactoryRegistry(), createSqlBuilder());
		return super.createDiffSql(difference);
	}

	@Override
	public java.util.List<com.sqlapp.data.db.sql.SqlOperation> createSql(com.sqlapp.data.schemas.Function function) {
		if (FirebirdExternalFunctionSql.legacy(function, getDialect()))
			throw new IllegalArgumentException("External function ALTER requires original.diff(target)");
		return super.createSql(function);
	}

	@Override
	protected String kind() {
		return "FUNCTION";
	}
}
