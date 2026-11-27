/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.firebird.sql;

import java.util.List;
import com.sqlapp.data.db.dialect.firebird.util.FirebirdSqlBuilder;
import com.sqlapp.data.db.sql.SimpleSqlFactory;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Constraint;

/** Named Firebird constraints are dropped through their owning table. */
public class FirebirdDropConstraintFactory extends SimpleSqlFactory<Constraint, FirebirdSqlBuilder> {
	@Override
	public List<SqlOperation> createSql(Constraint constraint) {
		var builder = createSqlBuilder();
		FirebirdConstraintSql.drop(constraint, builder);
		return List.of(new SqlOperation(builder.toString(), SqlType.DROP, constraint));
	}
}
