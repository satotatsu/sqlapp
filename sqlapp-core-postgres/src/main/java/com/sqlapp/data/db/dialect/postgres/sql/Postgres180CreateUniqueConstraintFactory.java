package com.sqlapp.data.db.dialect.postgres.sql;

import com.sqlapp.data.db.sql.AbstractCreateUniqueConstraintFactory;
import com.sqlapp.data.schemas.Order;
import com.sqlapp.data.schemas.ReferenceColumn;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.UniqueConstraint;
import com.sqlapp.util.AbstractSqlBuilder;

/**
 * PostgreSQL 18 temporal PRIMARY KEY / UNIQUE constraint.
 */
public class Postgres180CreateUniqueConstraintFactory
		extends AbstractCreateUniqueConstraintFactory<AbstractSqlBuilder<?>> {
	public static final String WITHOUT_OVERLAPS = "withoutOverlaps";

	@Override
	public void addObjectDetail(UniqueConstraint obj, Table table,
			AbstractSqlBuilder<?> builder) {
		builder.constraint().space();
		builder.name(obj, table != null ? false
				: this.getOptions().isDecorateSchemaName());
		if (obj.isPrimaryKey()) {
			builder.primaryKey();
		} else {
			builder.unique();
		}
		addOption(obj, builder);
		builder.space()._add('(');
		int i = 0;
		int size = obj.getColumns().size();
		for (ReferenceColumn col : obj.getColumns()) {
			builder.comma(i > 0).name(col);
			if (col.getOrder() != null && col.getOrder() != Order.Asc) {
				builder.space()._add(col.getOrder());
			}
			if (isTemporal(obj) && i == size - 1) {
				builder.space()._add("WITHOUT OVERLAPS");
			}
			i++;
		}
		builder.space()._add(')');
		addDeferrability(obj, builder);
		addAfter(obj, builder);
	}

	private boolean isTemporal(UniqueConstraint obj) {
		return Boolean.parseBoolean(obj.getSpecifics().get(WITHOUT_OVERLAPS));
	}
}
