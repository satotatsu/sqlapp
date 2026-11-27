/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.firebird.sql;

import java.util.ArrayList;
import java.util.List;
import com.sqlapp.data.db.dialect.firebird.Firebird30;
import com.sqlapp.data.db.dialect.firebird.util.FirebirdSqlBuilder;
import com.sqlapp.data.db.sql.SimpleSqlFactory;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.PackageBody;

/**
 * Package bodies have a separate DROP statement, without removing the header.
 */
public class Firebird30DropPackageBodyFactory extends SimpleSqlFactory<PackageBody, FirebirdSqlBuilder> {
	@Override
	public List<SqlOperation> createSql(PackageBody body) {
		if (!(getDialect() instanceof Firebird30))
			throw new UnsupportedOperationException("DROP PACKAGE BODY requires Firebird 3.0 or later");
		com.sqlapp.data.db.dialect.firebird.util.FirebirdPrivilege.identifier(body.getName(), "package body name");
		var result = new ArrayList<SqlOperation>();
		addSql(result, createSqlBuilder()._add("DROP PACKAGE BODY ").name(body.getName()), SqlType.DROP, body);
		return result;
	}
}
