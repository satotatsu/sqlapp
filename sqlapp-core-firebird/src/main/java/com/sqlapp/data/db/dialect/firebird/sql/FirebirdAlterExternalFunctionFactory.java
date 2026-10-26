/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.firebird.sql;

import java.util.List;
import com.sqlapp.data.db.dialect.firebird.util.FirebirdSqlBuilder;
import com.sqlapp.data.db.sql.*;
import com.sqlapp.data.schemas.*;

public class FirebirdAlterExternalFunctionFactory extends SimpleSqlFactory<Function, FirebirdSqlBuilder> {
	@Override
	public List<SqlOperation> createSql(Function function) {
		throw new IllegalArgumentException("External function ALTER requires original.diff(target)");
	}

	@Override
	public List<SqlOperation> createDiffSql(DbObjectDifference difference) {
		return FirebirdExternalFunctionSql.alter(difference, getSqlFactoryRegistry(), createSqlBuilder());
	}
}
