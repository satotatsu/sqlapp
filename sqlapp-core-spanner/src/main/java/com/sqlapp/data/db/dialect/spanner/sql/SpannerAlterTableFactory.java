/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.spanner.sql;

import java.util.List;

import com.sqlapp.data.db.dialect.spanner.util.SpannerSqlBuilder;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.datatype.LengthProperties;
import com.sqlapp.data.db.sql.AbstractAlterTableFactory;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.CheckConstraint;
import com.sqlapp.data.schemas.Constraint;
import com.sqlapp.data.schemas.ForeignKeyConstraint;
import com.sqlapp.data.schemas.Index;
import com.sqlapp.data.schemas.DbObjectDifference;
import com.sqlapp.data.schemas.DbObjectDifferenceCollection;
import com.sqlapp.data.schemas.SchemaObjectProperties;
import com.sqlapp.data.schemas.SchemaProperties;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.State;
import com.sqlapp.util.CommonUtils;

/**
 * GoogleSQL column migrations that can be represented without rebuilding a
 * table.
 */
public class SpannerAlterTableFactory extends AbstractAlterTableFactory<SpannerSqlBuilder> {
	@Override
	public List<SqlOperation> createDiffSql(final DbObjectDifference difference) {
		final var changed = difference.toDifference().getChangedProperties(getDialect());
		for (String property : changed.keySet()) {
			if (!SchemaObjectProperties.COLUMNS.getLabel().equals(property)
					&& !SchemaObjectProperties.CONSTRAINTS.getLabel().equals(property)
					&& !SchemaObjectProperties.INDEXES.getLabel().equals(property)) {
				throw new UnsupportedOperationException(
						"Cloud Spanner table change requires a separate migration: " + property);
			}
		}
		final List<SqlOperation> result = new java.util.ArrayList<>();
		final var constraints = (DbObjectDifferenceCollection) changed
				.get(SchemaObjectProperties.CONSTRAINTS.getLabel());
		if (constraints != null) {
			for (final var modified : constraints.getList(State.Modified)) {
				final var oldConstraint = modified.getOriginal(Constraint.class);
				final var constraint = modified.getTarget(Constraint.class);
				validateConstraintIdentity(oldConstraint);
				validateConstraintIdentity(constraint);
				// Remove the old constraint before dropping any columns it references.
				super.addDropConstraintDefinition(difference.getOriginal(Table.class),
						difference.getTarget(Table.class), oldConstraint, constraint, modified, result);
			}
		}
		final var indexes = (DbObjectDifferenceCollection) changed.get(SchemaObjectProperties.INDEXES.getLabel());
		if (indexes != null) {
			for (final var modified : indexes.getList(State.Modified)) {
				result.addAll(getSqlFactoryRegistry().createSql(modified.getOriginal(Index.class), SqlType.DROP));
			}
		}
		result.addAll(super.createDiffSql(difference));
		return result;
	}

	@Override
	protected void addCreateIndexDefinition(final Table original, final Table table, final Index oldIndex,
			final Index index, final DbObjectDifference difference, final List<SqlOperation> result) {
		final var operations = getSqlFactoryRegistry().createSql(index, SqlType.CREATE);
		if (operations.isEmpty()) {
			throw new UnsupportedOperationException(
					"Cloud Spanner constraint-owned index requires a constraint migration: " + index.getName());
		}
		result.addAll(operations);
	}

	@Override
	protected void addDropIndexDefinition(final Table original, final Table table, final Index oldIndex,
			final Index index, final DbObjectDifference difference, final List<SqlOperation> result) {
		if (difference.getState() != State.Modified) {
			result.addAll(getSqlFactoryRegistry().createSql(oldIndex, SqlType.DROP));
		}
	}

	@Override
	protected void addDropConstraintDefinition(final Table original, final Table table, final Constraint oldConstraint,
			final Constraint constraint, final DbObjectDifference difference, final List<SqlOperation> result) {
		if (difference.getState() != State.Modified) {
			super.addDropConstraintDefinition(original, table, oldConstraint, constraint, difference, result);
		}
	}

	@Override
	protected void addConstraintDefinition(final Table original, final Table table, final Constraint oldConstraint,
			final Constraint constraint, final DbObjectDifference difference, final List<SqlOperation> result) {
		for (Constraint candidate : new Constraint[] { oldConstraint, constraint }) {
			validateConstraintIdentity(candidate);
		}
		super.addConstraintDefinition(original, table, oldConstraint, constraint, difference, result);
	}

	private void validateConstraintIdentity(final Constraint constraint) {
		if (constraint == null)
			return;
		if (!(constraint instanceof CheckConstraint) && !(constraint instanceof ForeignKeyConstraint)) {
			throw new UnsupportedOperationException(
					"Cloud Spanner constraint change requires a separate migration: " + constraint.getName());
		}
		if (constraint.getName() == null || constraint.getName().isBlank()) {
			throw new IllegalArgumentException("Cloud Spanner DROP/ADD constraint requires a name");
		}
	}

	private void requireNonKey(final Table table, final Column column) {
		final var key = table.getConstraints().getPrimaryKeyConstraint();
		if (key != null && key.getColumns().get(column.getName()) != null) {
			throw new UnsupportedOperationException(
					"Cloud Spanner cannot change primary-key columns: " + column.getName());
		}
	}

	@Override
	protected void addAddColumn(final Table original, final Table table, final DbObjectDifference difference,
			final List<SqlOperation> result) {
		final Column column = difference.getTarget(Column.class);
		requireNonKey(table, column);
		if (column.isIdentity()) {
			throw new UnsupportedOperationException(
					"Cloud Spanner identity-column addition requires a separate migration: " + column.getName());
		}
		if (column.isNotNull() && (column.getArrayDimension() > 0 || (CommonUtils.isEmpty(column.getDefaultValue())
				&& (CommonUtils.isEmpty(column.getFormula()) || !column.isFormulaPersisted())))) {
			throw new IllegalArgumentException("Cloud Spanner ADD COLUMN NOT NULL requires a scalar column "
					+ "with a default or stored expression: " + column.getName());
		}
		final var builder = createSqlBuilder();
		builder.alter().table().name(table, getOptions().isDecorateSchemaName()).add().column()
				.ifNotExists(getOptions().isCreateIfNotExists()).space().name(column).space().definition(column, false);
		add(result, createOperation(builder.toString(), SqlType.ALTER, null, column));
	}

	@Override
	protected void addDeleteColumn(final Table original, final Table table, final DbObjectDifference difference,
			final List<SqlOperation> result) {
		requireNonKey(original, difference.getOriginal(Column.class));
		// GoogleSQL has no DROP COLUMN IF EXISTS clause.
		super.addDeleteColumn(original, table, difference, result);
	}

	@Override
	protected void addRenameOrAlterColumn(final Table original, final Table table, final DbObjectDifference difference,
			final List<SqlOperation> result) {
		final var changed = difference.toDifference().getChangedProperties(getDialect());
		final Column column = difference.getTarget(Column.class);
		final Column oldColumn = difference.getOriginal(Column.class);
		if (changed.size() == 1 && changed.containsKey(SpannerSqlBuilder.ALLOW_COMMIT_TIMESTAMP)) {
			addCommitTimestampChange(original, table, oldColumn, column, result);
			return;
		}
		final boolean nullability = changed.containsKey(SchemaProperties.NOT_NULL.getLabel());
		final boolean length = changed.containsKey(SchemaProperties.LENGTH.getLabel());
		final boolean nativeLengthName = length && column.getDataType() == oldColumn.getDataType()
				&& isNativeLengthName(column) && isNativeLengthName(oldColumn);
		if (changed.isEmpty() || changed.keySet().stream()
				.anyMatch(property -> !SchemaProperties.DEFAULT_VALUE.getLabel().equals(property)
						&& !SchemaProperties.NOT_NULL.getLabel().equals(property)
						&& !SchemaProperties.LENGTH.getLabel().equals(property)
						&& !(nativeLengthName && SchemaProperties.DATA_TYPE_NAME.getLabel().equals(property)))) {
			throw new UnsupportedOperationException("Cloud Spanner column change requires a separate migration: "
					+ column.getName() + " " + changed.keySet());
		}
		requireNonKey(original, oldColumn);
		if (length) {
			if ((column.getDataType() != DataType.VARCHAR && column.getDataType() != DataType.VARBINARY)
					|| column.getDataType() != oldColumn.getDataType() || column.getArrayDimension() != 0) {
				throw new UnsupportedOperationException(
						"Cloud Spanner length ALTER supports only scalar STRING/BYTES columns: " + column.getName());
			}
			final Long targetLength = column.getLength();
			if (targetLength == null || targetLength <= 0) {
				throw new IllegalArgumentException("Cloud Spanner length ALTER requires an explicit positive length: "
						+ column.getName() + "; use the dialect's modeled MAX length for MAX");
			}
			final var type = getDialect().getDbDataTypes().getDbType(column.getDataType(), targetLength);
			if (!(type instanceof LengthProperties<?> sized) || sized.getMaxLength() == null
					|| targetLength > sized.getMaxLength()) {
				throw new IllegalArgumentException("Cloud Spanner column length exceeds the native type limit: "
						+ column.getName() + " " + targetLength);
			}
		}
		if (column.isIdentity() || !CommonUtils.isEmpty(column.getFormula())
				|| !CommonUtils.isEmpty(column.getOnUpdate())) {
			throw new UnsupportedOperationException("Cloud Spanner column changes on identity, generated or "
					+ "ON UPDATE columns require a separate migration: " + column.getName());
		}
		final var builder = createSqlBuilder();
		builder.alter().table().name(table, getOptions().isDecorateSchemaName()).alterColumn().name(column);
		if (nullability || length) {
			if (column.getArrayDimension() > 0 && column.isNotNull()) {
				throw new UnsupportedOperationException(
						"Cloud Spanner cannot add NOT NULL to an existing ARRAY column: " + column.getName());
			}
			builder.space().definitionForAlterColumn(column);
		} else if (CommonUtils.isEmpty(column.getDefaultValue())) {
			builder.space()._add("DROP DEFAULT");
		} else {
			builder.space()._add("SET DEFAULT (")._add(column.getDefaultValue())._add(")");
		}
		add(result, createOperation(builder.toString(), SqlType.ALTER, oldColumn, column));
	}

	private boolean isNativeLengthName(final Column column) {
		final String nativeName = column.getDataType() == DataType.VARCHAR ? "STRING"
				: column.getDataType() == DataType.VARBINARY ? "BYTES" : null;
		if (nativeName == null)
			return false;
		final String name = column.getDataTypeName();
		return CommonUtils.isEmpty(name) || nativeName.equalsIgnoreCase(name)
				|| (nativeName + "(MAX)").equalsIgnoreCase(name);
	}

	private void addCommitTimestampChange(final Table original, final Table table, final Column oldColumn,
			final Column column, final List<SqlOperation> result) {
		requireNonKey(original, oldColumn);
		if (column.getDataType() != DataType.TIMESTAMP || column.getArrayDimension() != 0) {
			throw new IllegalArgumentException(
					"Cloud Spanner commit timestamp ALTER requires a scalar TIMESTAMP column: " + column.getName());
		}
		if (column.isIdentity() || !CommonUtils.isEmpty(column.getFormula())
				|| !CommonUtils.isEmpty(column.getOnUpdate())) {
			throw new UnsupportedOperationException("Cloud Spanner commit timestamp ALTER on identity, generated or "
					+ "ON UPDATE columns requires a separate migration: " + column.getName());
		}
		final boolean before = Boolean.TRUE.equals(SpannerSqlBuilder.allowCommitTimestamp(oldColumn));
		final boolean after = Boolean.TRUE.equals(SpannerSqlBuilder.allowCommitTimestamp(column));
		if (before == after)
			return;
		final var builder = createSqlBuilder();
		builder.alter().table().name(table, getOptions().isDecorateSchemaName()).alterColumn().name(column).space()
				._add("SET OPTIONS (allow_commit_timestamp=")._add(after ? "true" : "null")._add(")");
		add(result, createOperation(builder.toString(), SqlType.ALTER, oldColumn, column));
	}
}
