/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-mdb.
 */
package com.sqlapp.data.db.dialect.mdb.sql;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.sqlapp.data.db.dialect.mdb.util.MdbSqlBuilder;
import com.sqlapp.data.db.sql.AbstractAlterTableFactory;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Constraint;
import com.sqlapp.data.schemas.DbObjectDifference;
import com.sqlapp.data.schemas.Index;
import com.sqlapp.data.schemas.Table;

/** Generates the subset of Access ALTER TABLE supported by UCanAccess. */
public class MdbAlterTableFactory extends AbstractAlterTableFactory<MdbSqlBuilder> {

	@Override
	public List<SqlOperation> createDiffSql(final DbObjectDifference difference) {
		final Table original = difference.getOriginal(Table.class);
		final Table target = difference.getTarget(Table.class);
		if (original == null || target == null || Objects.equals(original.getName(), target.getName())) {
			return super.createDiffSql(difference);
		}
		// A table rename also changes the owning table name of indexes/constraints.
		// Compare after renaming so these are not treated as DROP/recreate changes.
		final Table renamed = original.clone().setName(target.getName());
		final List<SqlOperation> result = new ArrayList<>();
		final MdbSqlBuilder builder = createSqlBuilder();
		builder.alter().table().name(original, getOptions().isDecorateSchemaName());
		builder.rename().to().name(target, getOptions().isDecorateSchemaName());
		result.add(createOperation(builder.toString(), SqlType.ALTER, original, target));
		result.addAll(super.createDiffSql(renamed.diff(target)));
		return result;
	}

	@Override
	protected void addDeleteColumn(final Table originalTable, final Table table, final DbObjectDifference diff,
			final List<SqlOperation> result) {
		throw unsupported("DROP COLUMN");
	}

	@Override
	protected void addAlterColumn(final Table originalTable, final Table table, final Column oldColumn,
			final Column column, final DbObjectDifference diff, final List<SqlOperation> result) {
		throw unsupported("column definition alteration");
	}

	@Override
	protected void addRenameColumn(final Table originalTable, final Table table, final Column oldColumn,
			final Column column, final DbObjectDifference diff, final List<SqlOperation> result) {
		throw unsupported("column rename");
	}

	@Override
	protected void addDropConstraintDefinition(final Table originalTable, final Table table,
			final Constraint originalConstraint, final Constraint constraint, final DbObjectDifference diff,
			final List<SqlOperation> result) {
		throw unsupported("DROP CONSTRAINT");
	}

	@Override
	protected void addDropIndexDefinition(final Table originalTable, final Table table, final Index originalIndex,
			final Index index, final DbObjectDifference diff, final List<SqlOperation> result) {
		throw unsupported("DROP INDEX");
	}

	private UnsupportedOperationException unsupported(final String operation) {
		return new UnsupportedOperationException(
				"Microsoft Access " + operation + " is not persistently supported by UCanAccess 5.2.0");
	}
}
