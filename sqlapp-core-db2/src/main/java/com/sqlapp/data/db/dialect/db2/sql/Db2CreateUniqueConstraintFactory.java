/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2.sql;

import com.sqlapp.data.db.dialect.db2.Db2_1050;
import com.sqlapp.data.db.sql.CreateUniqueConstraintFactory;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.UniqueConstraint;
import com.sqlapp.util.AbstractSqlBuilder;

public class Db2CreateUniqueConstraintFactory extends CreateUniqueConstraintFactory {
	@Override
	public void addObjectDetail(UniqueConstraint constraint, Table table, AbstractSqlBuilder<?> builder) {
		if (constraint.getColumns().isEmpty())
			throw new IllegalArgumentException("Db2 unique constraint requires key columns");
		if (!constraint.isEnable() && !(getDialect() instanceof Db2_1050)) {
			throw new IllegalArgumentException(
					"Db2 NOT ENFORCED primary/unique constraints require Db2 LUW 10.5 or later");
		}
		builder.constraint().space().name(constraint.getName());
		if (constraint.isPrimaryKey())
			builder.primaryKey();
		else
			builder.unique();
		builder.space()._add("(");
		int count = 0;
		for (var column : constraint.getColumns()) {
			if (column.isIncludedColumn())
				throw new IllegalArgumentException("Db2 unique constraint cannot contain INCLUDE columns");
			// Ordering belongs to the backing index, not the logical constraint column
			// list.
			builder.comma(count++ > 0).name(column);
		}
		builder.space()._add(")");
		if (!constraint.isEnable())
			builder.space()._add("NOT ENFORCED");
	}
}
