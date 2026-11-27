/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.hsql.sql;

import java.util.List;
import com.sqlapp.data.db.dialect.hsql.util.HsqlSqlBuilder;
import com.sqlapp.data.db.sql.SimpleSqlFactory;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.schemas.DbObjectDifference;
import com.sqlapp.data.schemas.Type;

/** HSQLDB 2.7.4 has no executable ALTER TYPE, including rename. */
public class HsqlAlterTypeFactory extends SimpleSqlFactory<Type, HsqlSqlBuilder> {
	@Override
	public List<SqlOperation> createSql(Type type) {
		throw new IllegalArgumentException("HSQLDB type ALTER requires original.diff(target)");
	}

	@Override
	public List<SqlOperation> createDiffSql(DbObjectDifference difference) {
		if (!(difference.getOriginal() instanceof Type original) || !(difference.getTarget() instanceof Type target)) {
			throw new IllegalArgumentException("HSQLDB type ALTER requires two types");
		}
		if (original.equals(target))
			return List.of();
		throw new IllegalArgumentException(
				"HSQLDB distinct type changes require HsqlTableMigration.plan for the dependent table; ALTER TYPE is unsupported by the bundled engine");
	}
}
