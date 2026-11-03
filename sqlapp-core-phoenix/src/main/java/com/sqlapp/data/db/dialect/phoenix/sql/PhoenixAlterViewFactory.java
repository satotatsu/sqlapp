/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.phoenix.sql;

import java.util.List;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.DbObjectDifference;
import com.sqlapp.data.schemas.SchemaObjectProperties;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.View;
import com.sqlapp.util.AbstractSqlBuilder;

/** Native ALTER VIEW column operations using the table-diff executor. */
public class PhoenixAlterViewFactory extends PhoenixAlterTableFactory {
	@Override
	public List<SqlOperation> createDiffSql(final DbObjectDifference difference) {
		if (!(difference.getOriginal() instanceof View) || !(difference.getTarget() instanceof View)) {
			throw new IllegalArgumentException("Phoenix ALTER VIEW requires original and target View models");
		}
		for (String property : difference.toDifference().getChangedProperties(getDialect()).keySet()) {
			if (!SchemaObjectProperties.COLUMNS.getLabel().equals(property)
					&& !SchemaObjectProperties.INDEXES.getLabel().equals(property)) {
				throw new UnsupportedOperationException(
						"Phoenix ALTER VIEW supports only column/index changes; separate migration required: "
								+ property);
			}
		}
		return super.createDiffSql(difference);
	}

	@Override
	protected void addAlterObject(final Table table, final AbstractSqlBuilder<?> builder) {
		builder.alter().view().name(table, getOptions().isDecorateSchemaName());
	}

	@Override
	protected void addColumnDefinition(final Column column, final AbstractSqlBuilder<?> builder) {
		PhoenixViewColumns.appendDefinition(column, builder);
	}
}
