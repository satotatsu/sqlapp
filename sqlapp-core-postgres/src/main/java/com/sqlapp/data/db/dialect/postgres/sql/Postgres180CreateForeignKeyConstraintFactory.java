package com.sqlapp.data.db.dialect.postgres.sql;

import com.sqlapp.data.db.sql.AbstractCreateForeignKeyConstraintFactory;
import com.sqlapp.data.schemas.ForeignKeyConstraint;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.util.AbstractSqlBuilder;
import com.sqlapp.util.CommonUtils;

/**
 * PostgreSQL 18 temporal foreign key constraint.
 */
public class Postgres180CreateForeignKeyConstraintFactory
		extends AbstractCreateForeignKeyConstraintFactory<AbstractSqlBuilder<?>> {
	public static final String PERIOD = "period";

	@Override
	public void addObjectDetail(ForeignKeyConstraint obj, Table table,
			AbstractSqlBuilder<?> builder) {
		boolean temporal = Boolean.parseBoolean(obj.getSpecifics().get(PERIOD));
		if (temporal && obj.getColumns().isEmpty()) {
			throw new IllegalArgumentException(
					"Temporal foreign key requires a PERIOD column.");
		}
		builder.constraint().space();
		builder.name(obj, table != null ? false
				: this.getOptions().isDecorateSchemaName());
		builder.space().foreignKey();
		addOption(obj, builder);
		builder.space()._add("(");
		addLocalColumns(builder, obj, temporal);
		builder.space()._add(")");
		builder.references();
		if (obj.getTable().getSchemaName() != null
				&& obj.getRelatedTable().getSchemaName() != null
				&& !CommonUtils.eq(obj.getTable().getSchemaName(),
						obj.getRelatedTable().getSchemaName())) {
			builder.name(obj.getRelatedTable(), true);
		} else {
			builder.name(obj.getRelatedTable(), false);
		}
		builder.space()._add('(');
		addRelatedColumns(builder, obj, temporal);
		builder.space()._add(')');
		addMatchOption(obj, builder);
		addCascadeRule(obj, builder);
		addDeferrability(obj, builder);
		addAfter(obj, builder);
	}

	private void addLocalColumns(AbstractSqlBuilder<?> builder,
			ForeignKeyConstraint obj, boolean temporal) {
		for (int i = 0; i < obj.getColumns().size(); i++) {
			builder.comma(i > 0);
			if (temporal && i == obj.getColumns().size() - 1) {
				builder._add("PERIOD").space();
			}
			builder.name(obj.getColumns().get(i));
		}
	}

	private void addRelatedColumns(AbstractSqlBuilder<?> builder,
			ForeignKeyConstraint obj, boolean temporal) {
		for (int i = 0; i < obj.getRelatedColumns().size(); i++) {
			builder.comma(i > 0);
			if (temporal && i == obj.getRelatedColumns().size() - 1) {
				builder._add("PERIOD").space();
			}
			builder.name(obj.getRelatedColumns().get(i));
		}
	}

	@Override
	protected void addAfter(ForeignKeyConstraint constraint,
			AbstractSqlBuilder<?> builder) {
		if (Boolean.parseBoolean(constraint.getSpecifics().get(
				Postgres180CreateCheckConstraintFactory.NOT_ENFORCED))) {
			builder.space()._add("NOT ENFORCED");
		}
	}
}
