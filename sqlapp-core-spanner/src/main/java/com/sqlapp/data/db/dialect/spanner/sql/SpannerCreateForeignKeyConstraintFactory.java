/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.spanner.sql;

import com.sqlapp.data.db.dialect.spanner.util.SpannerSqlBuilder;
import com.sqlapp.data.db.sql.AbstractCreateForeignKeyConstraintFactory;
import com.sqlapp.data.schemas.CascadeRule;
import com.sqlapp.data.schemas.Deferrability;
import com.sqlapp.data.schemas.ForeignKeyConstraint;
import com.sqlapp.data.schemas.Table;

/** GoogleSQL foreign-key actions and enforcement shared by CREATE and ALTER. */
public class SpannerCreateForeignKeyConstraintFactory
		extends AbstractCreateForeignKeyConstraintFactory<SpannerSqlBuilder> {
	@Override
	public void addObjectDetail(final ForeignKeyConstraint constraint, final Table table,
			final SpannerSqlBuilder builder) {
		if (table == null || constraint.getTable() == null || constraint.getName() == null
				|| constraint.getName().isBlank() || constraint.getColumns().isEmpty()
				|| constraint.getColumns().size() != constraint.getRelatedColumns().size()) {
			throw new IllegalArgumentException("Cloud Spanner foreign key requires an owning table, name "
					+ "and matching nonempty referencing/referenced column lists");
		}
		final Table related = constraint.getRelatedTable();
		if (related == null || related.getName() == null || related.getName().isBlank()
				|| (constraint.getRelatedTableName() != null
						&& !constraint.getRelatedTableName().equals(related.getName()))
				|| (constraint.getRelatedTableSchemaName() != null
						&& !constraint.getRelatedTableSchemaName().equals(related.getSchemaName()))) {
			throw new IllegalArgumentException(
					"Cloud Spanner foreign key referenced table cannot be resolved: " + constraint.getName()
							+ "; resolve it in the Schema model or bind referenced columns to their table");
		}
		if ((constraint.getDeferrability() != null && constraint.getDeferrability() != Deferrability.NotDeferrable)
				|| (constraint.getMatchOption() != null
						&& constraint.getMatchOption() != ForeignKeyConstraint.MatchOption.Simple)
				|| (constraint.getUpdateRule() != null && constraint.getUpdateRule() != CascadeRule.None)) {
			throw new UnsupportedOperationException(
					"Cloud Spanner foreign key supports only nondeferred SIMPLE matching and NO ACTION updates: "
							+ constraint.getName());
		}
		final CascadeRule delete = constraint.getDeleteRule();
		if (delete != null && delete != CascadeRule.None && delete != CascadeRule.Cascade
				&& delete != CascadeRule.SetNull) {
			throw new UnsupportedOperationException("Unsupported Cloud Spanner foreign-key delete action: " + delete);
		}
		if (!constraint.isEnable() && delete != null && delete != CascadeRule.None) {
			throw new IllegalArgumentException(
					"Cloud Spanner NOT ENFORCED foreign keys cannot have delete actions: " + constraint.getName());
		}
		super.addObjectDetail(constraint, table, builder);
	}

	@Override
	protected void addCascadeRule(final ForeignKeyConstraint constraint, final SpannerSqlBuilder builder) {
		if (constraint.isEnable() && constraint.getDeleteRule() != null) {
			builder.space()._add("ON DELETE ")
					._add(constraint.getDeleteRule() == CascadeRule.None ? "NO ACTION" : constraint.getDeleteRule());
		}
		// NO ACTION updates are inherent in GoogleSQL; there is no ON UPDATE clause.
	}

	@Override
	protected void addAfter(final ForeignKeyConstraint constraint, final SpannerSqlBuilder builder) {
		if (!constraint.isEnable()) {
			builder.space()._add("NOT ENFORCED");
		}
	}
}
