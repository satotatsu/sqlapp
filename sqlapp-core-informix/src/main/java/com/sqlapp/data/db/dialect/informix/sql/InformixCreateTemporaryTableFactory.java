/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.informix.sql;

import java.util.List;

import com.sqlapp.data.db.dialect.informix.util.InformixSqlBuilder;
import com.sqlapp.data.db.sql.AbstractCreateTemporaryTableFactory;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Index;
import com.sqlapp.data.schemas.Table;

/**
 * Session temporary tables using the shared column and constraint generators.
 */
public class InformixCreateTemporaryTableFactory extends AbstractCreateTemporaryTableFactory<InformixSqlBuilder> {
	@Override
	public List<SqlOperation> createSql(final Table table) {
		if (table.getPartitioning() != null || table.getTableSpaceName() != null || !table.getInherits().isEmpty()
				|| !table.getSpecifics().isEmpty())
			throw new UnsupportedOperationException(
					"Informix temporary tables require a plain table model: " + table.getName());
		if (!table.getConstraints().getForeignKeyConstraints().isEmpty()
				|| !table.getConstraints().getExcludeConstraints().isEmpty())
			throw new UnsupportedOperationException(
					"Informix temporary tables do not support foreign key or exclusion constraints");
		final Table temporary = table.clone();
		final String prefix = getTableOptions().getTemporaryTableNamePrefix().apply(table);
		final String suffix = getTableOptions().getTemporaryTableNameSuffix().apply(table);
		temporary.setName((prefix == null ? "" : prefix) + table.getName() + (suffix == null ? "" : suffix));
		temporary.setSchemaName(null).setCatalogName(null);
		return super.createSql(temporary);
	}

	@Override
	protected void addCreateObject(final Table table, final InformixSqlBuilder builder) {
		builder.create().space()._add("TEMP TABLE").space().name(table, false);
	}

	@Override
	protected void addConstraintDefinitions(final Table table, final InformixSqlBuilder builder) {
		super.addConstraintDefinitions(table, builder);
		for (final var constraint : table.getConstraints().getCheckConstraints())
			addConstraintDefinition(constraint, builder);
	}

	@Override
	protected void addCreateIndexDefinition(final Table table, final Index index, final List<SqlOperation> result) {
		result.addAll(getSqlFactoryRegistry().createSql(index, SqlType.CREATE));
	}
}
