/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.firebird.sql;

import java.util.List;
import com.sqlapp.data.db.dialect.firebird.util.FirebirdSqlBuilder;
import com.sqlapp.data.db.sql.SimpleSqlFactory;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.ForeignKeyConstraint;

/** Firebird drops foreign keys through their owning table. */
public class FirebirdDropForeignKeyConstraintFactory
		extends SimpleSqlFactory<ForeignKeyConstraint, FirebirdSqlBuilder> {
	@Override
	public List<SqlOperation> createSql(ForeignKeyConstraint key) {
		var builder = createSqlBuilder();
		FirebirdConstraintSql.drop(key, builder);
		return List.of(new SqlOperation(builder.toString(), SqlType.DROP, key));
	}
}
