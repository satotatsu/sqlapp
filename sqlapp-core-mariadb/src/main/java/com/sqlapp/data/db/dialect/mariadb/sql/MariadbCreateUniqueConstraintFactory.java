/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.mariadb.sql;

import com.sqlapp.data.db.dialect.mysql.sql.MySqlCreateUniqueConstraintFactory;
import com.sqlapp.data.db.dialect.mysql.util.MySqlSqlBuilder;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.UniqueConstraint;

/** Recreates period keys while retaining shared index validation. */
public class MariadbCreateUniqueConstraintFactory extends MySqlCreateUniqueConstraintFactory {
	@Override
	public void addObjectDetail(UniqueConstraint constraint, Table table, MySqlSqlBuilder builder) {
		String periodName = constraint.getSpecifics().get("mariadb.withoutOverlapsPeriod");
		if (periodName == null) {
			super.addObjectDetail(constraint, table, builder);
			return;
		}
		Table owner = table == null ? constraint.getTable() : table;
		var period = owner == null ? null : owner.getTemporalPeriods().get(periodName);
		if (!(getDialect() instanceof com.sqlapp.data.db.dialect.mariadb.Mariadb11_40) || period == null
				|| period.getPeriodType() != com.sqlapp.data.schemas.TemporalPeriodType.APPLICATION_TIME
				|| period.getStartColumnName() == null || period.getEndColumnName() == null
				|| period.getStartColumnName().equalsIgnoreCase(period.getEndColumnName())
				|| owner.getColumns().get(period.getStartColumnName()) == null
				|| owner.getColumns().get(period.getEndColumnName()) == null) {
			throw new IllegalArgumentException(
					"MariaDB WITHOUT OVERLAPS requires 11.4+ and a resolved application period: " + periodName);
		}
		var columns = constraint.getColumns();
		int size = columns.size();
		boolean matchingBounds = size >= 3
				&& ((columns.get(size - 2).getName().equalsIgnoreCase(period.getStartColumnName())
						&& columns.get(size - 1).getName().equalsIgnoreCase(period.getEndColumnName()))
						|| (columns.get(size - 2).getName().equalsIgnoreCase(period.getEndColumnName())
								&& columns.get(size - 1).getName().equalsIgnoreCase(period.getStartColumnName())));
		if (!matchingBounds || columns.get(size - 2).getLength() != null || columns.get(size - 1).getLength() != null
				|| columns.get(size - 2).getOrder() == com.sqlapp.data.schemas.Order.Desc
				|| columns.get(size - 1).getOrder() == com.sqlapp.data.schemas.Order.Desc
				|| (constraint.getIndexType() != null
						&& constraint.getIndexType() != com.sqlapp.data.schemas.IndexType.BTree)) {
			throw new IllegalArgumentException(
					"MariaDB WITHOUT OVERLAPS requires scalar keys followed by full period bounds: "
							+ constraint.getName() + " columns=" + columns);
		}
		// Reuse the ordinary constraint path for all shared key validation, without
		// mutating the model.
		super.addObjectDetail(constraint, owner, createSqlBuilder());
		builder.constraint().space().name(constraint, false);
		if (constraint.isPrimaryKey())
			builder.primaryKey();
		else
			builder.unique();
		addOption(constraint, builder);
		builder.space()._add("(");
		var scalarKey = (UniqueConstraint) constraint.clone();
		scalarKey.getColumns().remove(size - 1);
		scalarKey.getColumns().remove(size - 2);
		builder.names(scalarKey.getColumns());
		builder.comma().space().name(periodName).space()._add("WITHOUT OVERLAPS").space()._add(")");
		addDeferrability(constraint, builder);
		addAfter(constraint, builder);
	}
}
