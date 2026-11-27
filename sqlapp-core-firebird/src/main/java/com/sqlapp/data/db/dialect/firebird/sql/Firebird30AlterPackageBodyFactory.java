/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.firebird.sql;

import java.util.*;
import com.sqlapp.data.db.dialect.firebird.util.FirebirdSqlBuilder;
import com.sqlapp.data.db.sql.*;
import com.sqlapp.data.schemas.DbObjectDifference;
import com.sqlapp.data.schemas.PackageBody;

/**
 * Firebird replaces package bodies with RECREATE, preserving package
 * privileges.
 */
public class Firebird30AlterPackageBodyFactory extends SimpleSqlFactory<PackageBody, FirebirdSqlBuilder> {
	@Override
	public List<SqlOperation> createSql(PackageBody object) {
		var create = getSqlFactoryRegistry().createSql(object, SqlType.CREATE);
		String ddl = create.get(0).getSqlText();
		if (!ddl.matches("(?is)^\\s*CREATE\\s+PACKAGE\\s+BODY\\b.*"))
			throw new IllegalArgumentException(
					"Package body ALTER requires a CREATE PACKAGE BODY definition or modeled statement");
		List<SqlOperation> result = new ArrayList<>();
		addSql(result,
				createSqlBuilder()
						._add(ddl.replaceFirst("(?is)^\\s*CREATE\\s+PACKAGE\\s+BODY\\b", "RECREATE PACKAGE BODY")),
				SqlType.ALTER, object);
		return result;
	}

	@Override
	public List<SqlOperation> createDiffSql(DbObjectDifference difference) {
		PackageBody target = FirebirdAlterSql.target(difference, PackageBody.class, Set.of("statement"));
		if (!difference.getChangedProperties().containsKey("statement"))
			return List.of();
		if (!target.getDefinition().isEmpty())
			throw new IllegalArgumentException("A complete package body definition shadows modeled ALTER changes");
		return createSql(target);
	}
}
