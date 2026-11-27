/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.spanner.sql;

import com.sqlapp.data.db.dialect.spanner.util.SpannerSqlBuilder;
import com.sqlapp.data.db.sql.AbstractCreateCheckConstraintFactory;
import com.sqlapp.data.schemas.CheckConstraint;
import com.sqlapp.data.schemas.Deferrability;
import com.sqlapp.data.schemas.Table;

/** Shares CHECK rendering between standalone, CREATE TABLE and ALTER paths. */
public class SpannerCreateCheckConstraintFactory extends AbstractCreateCheckConstraintFactory<SpannerSqlBuilder> {
	@Override
	public void addObjectDetail(final CheckConstraint constraint, final Table table, final SpannerSqlBuilder builder) {
		if (constraint.getName() == null || constraint.getName().isBlank() || constraint.getExpression() == null
				|| constraint.getExpression().isBlank()) {
			throw new IllegalArgumentException("Cloud Spanner CHECK requires a name and expression");
		}
		if (!constraint.isEnable() || (constraint.getDeferrability() != null
				&& constraint.getDeferrability() != Deferrability.NotDeferrable)) {
			throw new UnsupportedOperationException(
					"Cloud Spanner CHECK does not support disabled or deferred constraints: " + constraint.getName());
		}
		super.addObjectDetail(constraint, table, builder);
	}
}
