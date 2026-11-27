/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.firebird.sql;

import java.util.List;
import com.sqlapp.data.db.dialect.firebird.util.FirebirdPrivilege;
import com.sqlapp.data.db.dialect.firebird.util.FirebirdSqlBuilder;
import com.sqlapp.data.db.sql.*;
import com.sqlapp.data.schemas.Function;

public class FirebirdDropFunctionFactory extends SimpleSqlFactory<Function, FirebirdSqlBuilder> {
	@Override
	public List<SqlOperation> createSql(Function function) {
		FirebirdPrivilege.identifier(function.getName(), "function name");
		var builder = createSqlBuilder()
				._add(FirebirdExternalFunctionSql.legacy(function, getDialect()) ? "DROP EXTERNAL FUNCTION "
						: "DROP FUNCTION ")
				.name(function.getName());
		return List.of(new SqlOperation(builder.toString(), SqlType.DROP, function));
	}
}
