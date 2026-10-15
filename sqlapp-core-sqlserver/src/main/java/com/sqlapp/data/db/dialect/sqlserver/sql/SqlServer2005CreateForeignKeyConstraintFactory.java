/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.sqlserver.sql;

import java.util.List;
import com.sqlapp.data.db.dialect.sqlserver.util.SqlServerSqlBuilder;
import com.sqlapp.data.db.sql.AbstractCreateForeignKeyConstraintFactory;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.schemas.ForeignKeyConstraint;

public class SqlServer2005CreateForeignKeyConstraintFactory
		extends AbstractCreateForeignKeyConstraintFactory<SqlServerSqlBuilder> {
	@Override
	public void addCreateObject(ForeignKeyConstraint constraint, SqlServerSqlBuilder builder) {
		builder.alter().table().name(constraint.getTable(), getOptions().isDecorateSchemaName());
		SqlServerConstraintSupport.addPrefix(constraint, builder);
		builder.add();
		addObjectDetail(constraint, constraint.getTable(), builder);
	}

	@Override
	protected void addAfter(ForeignKeyConstraint constraint, SqlServerSqlBuilder builder) {
		SqlServerConstraintSupport.replication(constraint, builder);
	}

	@Override
	public List<SqlOperation> createSql(ForeignKeyConstraint constraint) {
		List<SqlOperation> result = super.createSql(constraint);
		SqlServerConstraintSupport.restore(constraint, createSqlBuilder(), getOptions().isDecorateSchemaName(), false,
				result);
		return result;
	}
}
