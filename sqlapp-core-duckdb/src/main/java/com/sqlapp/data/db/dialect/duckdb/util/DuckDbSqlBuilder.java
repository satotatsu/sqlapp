/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-duckdb.
 */
package com.sqlapp.data.db.dialect.duckdb.util;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.schemas.AbstractColumn;
import com.sqlapp.data.schemas.AbstractSchemaObject;
import com.sqlapp.data.schemas.Index;
import com.sqlapp.data.schemas.Routine;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.ForeignKeyConstraint;
import com.sqlapp.util.AbstractSqlBuilder;
import com.sqlapp.util.CommonUtils;

/**
 * SQL builder for DuckDB.
 */
public class DuckDbSqlBuilder extends AbstractSqlBuilder<DuckDbSqlBuilder> {

	private static final long serialVersionUID = 1L;

	public DuckDbSqlBuilder(final Dialect dialect) {
		super(dialect);
	}

	@Override
	public DuckDbSqlBuilder name(final AbstractSchemaObject<?> object, final boolean qualified) {
		if (qualified && !CommonUtils.isEmpty(object.getCatalogName())) {
			name(object.getCatalogName())._add('.');
		}
		return super.name(object, qualified);
	}

	@Override
	public DuckDbSqlBuilder name(final Index index, final boolean qualified) {
		if (qualified && !CommonUtils.isEmpty(index.getCatalogName())) {
			name(index.getCatalogName())._add('.');
		}
		return super.name(index, qualified);
	}

	@Override
	public DuckDbSqlBuilder specificName(final Routine<?> routine, final boolean qualified) {
		if (qualified && !CommonUtils.isEmpty(routine.getCatalogName())) {
			name(routine.getCatalogName())._add('.');
		}
		return super.specificName(routine, qualified);
	}

	@Override
	protected DuckDbSqlBuilder typeDefinition(Column column) {
		super.typeDefinition(column);
		if (column.getDataType() == com.sqlapp.data.db.datatype.DataType.INTERVAL && column.getArrayDimension() > 0)
			_add("[]".repeat(column.getArrayDimension()));
		return this;
	}

	/**
	 * Adds a column definition. DuckDB generated columns are always VIRTUAL and may
	 * not have a default value. Column comments are emitted as separate
	 * {@code COMMENT ON COLUMN} statements by the table factories.
	 */
	@Override
	public DuckDbSqlBuilder definition(final Column column, final boolean withRemarks) {
		if (column.isIdentity()) {
			throw new IllegalArgumentException(
					"DuckDB has no identity columns; use a sequence default (DEFAULT nextval('...')): "
							+ column.getName());
		}
		typeDefinition(column);
		collateDefinition(column);
		if (!CommonUtils.isEmpty(column.getFormula())) {
			if (!CommonUtils.isEmpty(column.getDefaultValue())) {
				throw new IllegalArgumentException(
						"DuckDB generated columns cannot specify a default value: " + column.getName());
			}
			space()._add("GENERATED ALWAYS AS (")._add(column.getFormula())._add(") VIRTUAL");
		} else if (!CommonUtils.isEmpty(column.getDefaultValue())) {
			defaultDefinition(column);
		}
		notNullDefinition(column);
		if (!CommonUtils.isEmpty(column.getOnUpdate())) {
			throw new IllegalArgumentException("DuckDB does not support ON UPDATE column values: " + column.getName());
		}
		if (!CommonUtils.isEmpty(column.getCheck())) {
			checkConstraintDefinition(column);
		}
		return instance();
	}

	/**
	 * Adds the column type with its collation, as used by
	 * {@code ALTER COLUMN ... TYPE}.
	 *
	 * @param column column
	 * @return this builder
	 */
	public DuckDbSqlBuilder typeWithCollation(final Column column) {
		typeDefinition(column);
		collateDefinition(column);
		return instance();
	}

	@Override
	protected DuckDbSqlBuilder collateDefinition(final Column column) {
		if (!CommonUtils.isEmpty(column.getCollation())) {
			space()._add("COLLATE ").name(column.getCollation());
		}
		return instance();
	}

	@Override
	protected DuckDbSqlBuilder autoIncrement(final AbstractColumn<?> column) {
		throw new IllegalArgumentException(
				"DuckDB has no identity columns; use a sequence default (DEFAULT nextval('...')): " + column.getName());
	}

	@Override
	protected DuckDbSqlBuilder comment(final AbstractColumn<?> column) {
		return instance();
	}

	/**
	 * DuckDB supports only the default referential action (NO ACTION), and
	 * {@code DuckDbCreateTableFactory} rejects other rules. The rule is therefore
	 * not written; the shared builder would write the {@code NONE} rule literally,
	 * which DuckDB cannot parse.
	 */
	@Override
	public DuckDbSqlBuilder cascadeRule(final ForeignKeyConstraint constraint) {
		return instance();
	}
}
