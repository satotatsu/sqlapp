/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.sqlserver.sql;

import java.util.List;
import com.sqlapp.data.db.dialect.sqlserver.util.SqlServerSqlBuilder;
import com.sqlapp.data.db.sql.AbstractCreateCheckConstraintFactory;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.schemas.CheckConstraint;

public class SqlServer2005CreateCheckConstraintFactory
		extends AbstractCreateCheckConstraintFactory<SqlServerSqlBuilder> {
	@Override
	public void addCreateObject(CheckConstraint constraint, SqlServerSqlBuilder builder) {
		builder.alter().table().name(constraint.getTable(), getOptions().isDecorateSchemaName());
		SqlServerConstraintSupport.addPrefix(constraint, builder);
		builder.add();
		addObjectDetail(constraint, constraint.getTable(), builder);
	}

	@Override
	protected void addCheckConstraintOption(CheckConstraint constraint, SqlServerSqlBuilder builder) {
		SqlServerConstraintSupport.replication(constraint, builder);
	}

	/**
	 * Writes a column CHECK using the same name and replication semantics as a
	 * table CHECK.
	 */
	public static void addColumnCheck(com.sqlapp.data.schemas.Column column, SqlServerSqlBuilder builder) {
		SqlServerConstraintSupport.columnCheck(column, builder);
	}

	@Override
	public List<SqlOperation> createSql(CheckConstraint constraint) {
		List<SqlOperation> result = super.createSql(constraint);
		SqlServerConstraintSupport.restore(constraint, createSqlBuilder(), getOptions().isDecorateSchemaName(), false,
				result);
		return result;
	}
}
