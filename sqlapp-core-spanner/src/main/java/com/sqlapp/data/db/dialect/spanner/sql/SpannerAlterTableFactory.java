/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.spanner.sql;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

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
import com.sqlapp.data.schemas.IndexType;
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
	public static final String IDENTITY_RESTART_COUNTER_WITH = "IDENTITY_RESTART_COUNTER_WITH";
	private static final Pattern COMMIT_TIMESTAMP_EXPRESSION = Pattern.compile(
			"\\s*PENDING_COMMIT_TIMESTAMP\\s*\\(\\s*\\)\\s*", Pattern.CASE_INSENSITIVE);
	private static final List<String> MUTABLE_TABLE_OPTIONS = List.of(SpannerCreateTableFactory.LOCALITY_GROUP,
			SpannerCreateTableFactory.COLUMNAR_POLICY, SpannerCreateTableFactory.FULLTEXT_DICTIONARY_STALENESS);

	@Override
	public List<SqlOperation> createDiffSql(final DbObjectDifference difference) {
		final var changed = difference.toDifference().getChangedProperties(getDialect());
		if (changed.containsKey(SchemaProperties.NAME.getLabel())) {
			final var original = difference.getOriginal(Table.class);
			final var target = difference.getTarget(Table.class);
			if (original.getName() == null || original.getName().isBlank()
					|| target.getName() == null || target.getName().isBlank())
				throw new IllegalArgumentException("Spanner table rename requires original and target names");
			// Owned indexes expose the enclosing table's name. Normalize it on a
			// clone so those derived changes do not look like structural changes.
			final var normalized = target.clone().setName(original.getName());
			// Self references follow the renamed table; normalize only matching
			// old/new self references, retaining all other foreign-key changes.
			for (final var constraint : original.getConstraints()) {
				if (constraint instanceof ForeignKeyConstraint before && isSelfReference(before, original)
						&& normalized.getConstraints().get(before.getName()) instanceof ForeignKeyConstraint after
						&& isSelfReference(after, target))
					after.setRelatedTableName(original.getName());
			}
			final var structuralChanges = original.diff(normalized).toDifference().getChangedProperties(getDialect());
			if (!structuralChanges.isEmpty())
				throw new UnsupportedOperationException("Spanner table rename requires a separate migration: "
						+ structuralChanges.keySet());
			final var builder = createSqlBuilder();
			builder.alter().table().name(original, getOptions().isDecorateSchemaName()).space()
					._add("RENAME TO ").name(target, getOptions().isDecorateSchemaName());
			return List.of(createOperation(builder.toString(), SqlType.ALTER, original, target));
		}
		if (changed.containsKey(SpannerCreateTableFactory.INTERLEAVE_TYPE)) {
			if (changed.keySet().stream().anyMatch(property ->
					!SpannerCreateTableFactory.INTERLEAVE_TYPE.equalsIgnoreCase(property)
							&& !SpannerCreateTableFactory.ON_DELETE_ACTION.equalsIgnoreCase(property))) {
				throw new UnsupportedOperationException(
						"Spanner SET INTERLEAVE requires the same parent and a separate migration");
			}
			return createInterleaveTypeChange(difference.getOriginal(Table.class), difference.getTarget(Table.class));
		}
		if (changed.containsKey(SpannerCreateTableFactory.ON_DELETE_ACTION)) {
			if (changed.size() != 1)
				throw new UnsupportedOperationException(
						"Spanner INTERLEAVE ON DELETE and other table changes require separate migrations");
			return createInterleaveDeleteActionChange(difference.getOriginal(Table.class), difference.getTarget(Table.class));
		}
		final var original = difference.getOriginal(Table.class);
		final var table = difference.getTarget(Table.class);
		final boolean ttlChanged = changed.containsKey(SpannerCreateTableFactory.ROW_DELETION_POLICY_EXPRESSION);
		final boolean columnsChanged = changed.containsKey(SchemaObjectProperties.COLUMNS.getLabel());
		final String ttlBefore = ttlChanged ? SpannerRowDeletionPolicy.expression(original) : null;
		final String ttlAfter = ttlChanged || columnsChanged ? SpannerRowDeletionPolicy.expression(table) : null;
		if (ttlChanged && changed.size() > 1
				&& (!columnsChanged || changed.size() != 2)) {
			throw new UnsupportedOperationException(
					"Spanner TTL can only be combined with column changes");
		}
		if (columnsChanged && ttlBefore != null && ttlAfter != null)
			SpannerRowDeletionPolicy.expression(table, ttlBefore);
		for (String property : changed.keySet()) {
			if (!SchemaObjectProperties.COLUMNS.getLabel().equals(property)
					&& !SchemaObjectProperties.CONSTRAINTS.getLabel().equals(property)
					&& !SchemaObjectProperties.INDEXES.getLabel().equals(property)
					&& !SpannerCreateTableFactory.ROW_DELETION_POLICY_EXPRESSION.equalsIgnoreCase(property)
					&& MUTABLE_TABLE_OPTIONS.stream().noneMatch(property::equalsIgnoreCase)) {
				throw new UnsupportedOperationException(
						"Cloud Spanner table change requires a separate migration: " + property);
			}
		}
		final List<SqlOperation> result = new java.util.ArrayList<>();
		if (ttlChanged && ttlAfter == null)
			addRowDeletionPolicyChange(original, table, ttlBefore, null, result);
		final var options = MUTABLE_TABLE_OPTIONS.stream().filter(changed::containsKey).toList();
		if (!options.isEmpty()) {
			addTableOptionChanges(original, table, options, result);
		}
		final var constraints = (DbObjectDifferenceCollection) changed
				.get(SchemaObjectProperties.CONSTRAINTS.getLabel());
		if (constraints != null) {
			for (final var modified : constraints.getList(State.Modified)) {
				final var oldConstraint = modified.getOriginal(Constraint.class);
				final var constraint = modified.getTarget(Constraint.class);
				validateConstraintIdentity(oldConstraint);
				validateConstraintIdentity(constraint);
				// Remove the old constraint before dropping any columns it references.
				super.addDropConstraintDefinition(original, table, oldConstraint, constraint, modified, result);
			}
		}
		final var indexes = (DbObjectDifferenceCollection) changed.get(SchemaObjectProperties.INDEXES.getLabel());
		if (indexes != null) {
			for (final var modified : indexes.getList(State.Modified)) {
				final Index oldIndex = modified.getOriginal(Index.class);
				if (changesOnlyStoredColumns(modified)) {
					addStoredColumnChanges(oldIndex, modified.getTarget(Index.class), false, result);
				} else if (!changesOnlyVectorSearchOption(modified)) {
					result.addAll(getSqlFactoryRegistry().createSql(oldIndex, SqlType.DROP));
				}
			}
		}
		result.addAll(super.createDiffSql(difference));
		if (ttlChanged && ttlAfter != null)
			addRowDeletionPolicyChange(original, table, ttlBefore, ttlAfter, result);
		return result;
	}

	private boolean isSelfReference(final ForeignKeyConstraint constraint, final Table table) {
		return table.getName().equalsIgnoreCase(constraint.getRelatedTableName())
				&& Objects.toString(table.getSchemaName(), "").equalsIgnoreCase(
						Objects.toString(constraint.getRelatedTableSchemaName(), ""));
	}

	private void addRowDeletionPolicyChange(final Table original, final Table table, final String before,
			final String after, final List<SqlOperation> result) {
		final var builder = createSqlBuilder();
		builder.alter().table().name(table, getOptions().isDecorateSchemaName()).space();
		if (after == null)
			builder._add("DROP ROW DELETION POLICY");
		else
			builder._add(before == null ? "ADD" : "REPLACE")._add(" ROW DELETION POLICY (")._add(after)._add(")");
		add(result, createOperation(builder.toString(), SqlType.ALTER, original, table));
	}

	private List<SqlOperation> createInterleaveTypeChange(final Table original, final Table table) {
		final String before = SpannerCreateTableFactory.interleaveType(original);
		final String after = SpannerCreateTableFactory.interleaveType(table);
		if (before == null || after == null)
			throw new UnsupportedOperationException("Spanner SET INTERLEAVE requires an existing interleaved table");
		if (before.equals(after))
			return "IN PARENT".equals(after) ? createInterleaveDeleteActionChange(original, table) : List.of();
		if (table.getName() == null || table.getName().isBlank())
			throw new IllegalArgumentException("Spanner SET INTERLEAVE requires a table name");

		final var builder = createSqlBuilder();
		builder.alter().table().name(table, getOptions().isDecorateSchemaName()).space()
				._add("SET INTERLEAVE ")._add(after).space()
				.name(SpannerCreateTableFactory.interleaveParent(table), getOptions().isDecorateSchemaName());
		if ("IN".equals(after))
			return List.of(createOperation(builder.toString(), SqlType.ALTER, original, table));

		// Spanner validates parent rows before it allows CASCADE to be enabled.
		builder._add(" ON DELETE NO ACTION");
		if ("NO ACTION".equals(SpannerCreateTableFactory.interleaveDeleteAction(table)))
			return List.of(createOperation(builder.toString(), SqlType.ALTER, original, table));

		final var intermediate = table.clone();
		intermediate.getSpecifics().put(SpannerCreateTableFactory.ON_DELETE_ACTION, "NO ACTION");
		final var result = new java.util.ArrayList<SqlOperation>();
		result.add(createOperation(builder.toString(), SqlType.ALTER, original, intermediate));
		result.addAll(createInterleaveDeleteActionChange(intermediate, table));
		return result;
	}

	private List<SqlOperation> createInterleaveDeleteActionChange(final Table original, final Table table) {
		if (!"IN PARENT".equals(SpannerCreateTableFactory.interleaveType(original))
				|| !"IN PARENT".equals(SpannerCreateTableFactory.interleaveType(table))) {
			throw new UnsupportedOperationException("Spanner SET ON DELETE requires an existing INTERLEAVE IN PARENT: "
					+ table.getName());
		}
		final String action = SpannerCreateTableFactory.interleaveDeleteAction(table);
		if (action.equals(SpannerCreateTableFactory.interleaveDeleteAction(original)))
			return List.of();
		if (table.getName() == null || table.getName().isBlank())
			throw new IllegalArgumentException("Spanner SET ON DELETE requires a table name");
		final var builder = createSqlBuilder();
		builder.alter().table().name(table, getOptions().isDecorateSchemaName()).space()
				._add("SET ON DELETE ")._add(action);
		return List.of(createOperation(builder.toString(), SqlType.ALTER, original, table));
	}

	private void addTableOptionChanges(final Table original, final Table table, final List<String> options,
			final List<SqlOperation> result) {
		final var builder = createSqlBuilder();
		builder.alter().table().name(table, getOptions().isDecorateSchemaName()).space()._add("SET OPTIONS (");
		for (int i = 0; i < options.size(); i++) {
			final String key = options.get(i);
			final String value = table.getSpecifics().get(key);
			final boolean columnarPolicy = SpannerCreateTableFactory.COLUMNAR_POLICY.equals(key);
			if (value == null && !columnarPolicy)
				throw new UnsupportedOperationException("Spanner requires an explicit replacement for " + key);
			if (value != null && (value.isBlank() || value.chars().anyMatch(Character::isISOControl)))
				throw new IllegalArgumentException("Invalid Spanner table option " + key + ": " + value);
			if (columnarPolicy && value != null && !"enabled".equals(value) && !"disabled".equals(value))
				throw new IllegalArgumentException("Spanner COLUMNAR_POLICY must be enabled or disabled: " + value);
			builder.comma(i > 0)._add(key.toLowerCase(Locale.ROOT))._add(" = ");
			if (value == null)
				builder._add("NULL");
			else
				builder._add("'")._add(value.replace("\\", "\\\\").replace("'", "\\'"))._add("'");
		}
		builder._add(")");
		add(result, createOperation(builder.toString(), SqlType.ALTER, original, table));
	}

	@Override
	protected void addCreateIndexDefinition(final Table original, final Table table, final Index oldIndex,
			final Index index, final DbObjectDifference difference, final List<SqlOperation> result) {
		if (changesOnlyStoredColumns(difference)) {
			addStoredColumnChanges(oldIndex, index, true, result);
			return;
		}
		if (changesOnlyVectorSearchOption(difference)) {
			addVectorSearchOptionChange(oldIndex, index, result);
			return;
		}
		final var operations = getSqlFactoryRegistry().createSql(index, SqlType.CREATE);
		if (operations.isEmpty()) {
			throw new UnsupportedOperationException(
					"Cloud Spanner constraint-owned index requires a constraint migration: " + index.getName());
		}
		result.addAll(operations);
	}

	private boolean changesOnlyStoredColumns(final DbObjectDifference difference) {
		// The supported emulator accepts CREATE SEARCH INDEX but not ALTER SEARCH INDEX.
		if (difference.getState() != State.Modified
				|| difference.getOriginal(Index.class).getIndexType() == IndexType.FullText)
			return false;
		final var changes = difference.toDifference().getChangedProperties(getDialect());
		return changes.size() == 1 && changes.containsKey(SchemaObjectProperties.INCLUDED_COLUMNS.getLabel());
	}

	private boolean changesOnlyVectorSearchOption(final DbObjectDifference difference) {
		if (difference.getState() != State.Modified
				|| difference.getOriginal(Index.class).getIndexType() != IndexType.Vector)
			return false;
		final var changes = difference.toDifference().getChangedProperties(getDialect());
		return changes.size() == 1 && changes.containsKey(SpannerCreateIndexFactory.DISABLE_SEARCH);
	}

	private void addVectorSearchOptionChange(final Index original, final Index index,
			final List<SqlOperation> result) {
		SpannerIndexValidation.requireStandaloneIndex(original);
		SpannerIndexValidation.requireStandaloneIndex(index);
		final String value = index.getSpecifics().get(SpannerCreateIndexFactory.DISABLE_SEARCH);
		if (value != null && !"true".equalsIgnoreCase(value) && !"false".equalsIgnoreCase(value))
			throw new IllegalArgumentException("Spanner DISABLE_SEARCH requires true or false: " + value);
		final var builder = createSqlBuilder();
		builder.alter().space()._add("VECTOR").index().name(index, getOptions().isDecorateSchemaName())
				.space()._add("SET OPTIONS (disable_search = ")
				._add(value == null ? "NULL" : value.toLowerCase(Locale.ROOT))._add(")");
		add(result, createOperation(builder.toString(), SqlType.ALTER, original, index));
	}

	private void addStoredColumnChanges(final Index original, final Index target, final boolean adding,
			final List<SqlOperation> result) {
		final Index index = adding ? target : original;
		final Index other = adding ? original : target;
		SpannerIndexValidation.requireStandaloneIndex(index);
		for (final var column : index.getIncludes()) {
			final String name = column.getName();
			if (name == null || name.isBlank() || index.getTable().getColumns().get(name) == null
					|| index.getColumns().get(name) != null)
				throw new IllegalArgumentException("Invalid Spanner stored column on " + index.getName() + ": " + name);
			if (other.getIncludes().get(name) != null)
				continue;
			final var builder = createSqlBuilder();
			builder.alter();
			if (index.getIndexType() == IndexType.Vector)
				builder.space()._add("VECTOR");
			builder.index().name(index, getOptions().isDecorateSchemaName()).space()
					._add(adding ? "ADD STORED COLUMN " : "DROP STORED COLUMN ").name(column);
			add(result, createOperation(builder.toString(), SqlType.ALTER, original, target));
		}
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
		if (changed.size() == 1 && changed.containsKey(IDENTITY_RESTART_COUNTER_WITH)) {
			if (!oldColumn.isIdentity() || !column.isIdentity()
					|| column.getDataType() != DataType.BIGINT || column.getArrayDimension() != 0)
				throw new IllegalArgumentException("Spanner counter restart requires an existing scalar INT64 identity: "
						+ column.getName());
			final long counter = SpannerCounterRestart.value(column.getSpecifics().get(IDENTITY_RESTART_COUNTER_WITH),
					table.getName() + "." + column.getName());
			final var builder = createSqlBuilder();
			builder.alter().table().name(table, getOptions().isDecorateSchemaName()).alterColumn().name(column)
					.space()._add("ALTER IDENTITY RESTART COUNTER WITH ")._add(counter);
			add(result, createOperation(builder.toString(), SqlType.ALTER, oldColumn, column));
			return;
		}
		if (!changed.isEmpty() && changed.keySet().stream().allMatch(property ->
				SpannerSqlBuilder.IDENTITY_SKIP_RANGE_MIN.equalsIgnoreCase(property)
						|| SpannerSqlBuilder.IDENTITY_SKIP_RANGE_MAX.equalsIgnoreCase(property))) {
			if (!oldColumn.isIdentity() || !column.isIdentity()
					|| column.getDataType() != DataType.BIGINT || column.getArrayDimension() != 0)
				throw new IllegalArgumentException("Spanner skip range requires an existing scalar INT64 identity: "
						+ column.getName());
			final var builder = createSqlBuilder();
			builder.alter().table().name(table, getOptions().isDecorateSchemaName()).alterColumn().name(column)
					.alterIdentitySkipRange(column);
			add(result, createOperation(builder.toString(), SqlType.ALTER, oldColumn, column));
			return;
		}
		if (changed.size() == 1 && changed.containsKey(SpannerSqlBuilder.ALLOW_COMMIT_TIMESTAMP)) {
			addCommitTimestampChange(original, table, oldColumn, column, result);
			return;
		}
		final boolean defaultChanged = changed.containsKey(SchemaProperties.DEFAULT_VALUE.getLabel());
		if (column.getDataType() != oldColumn.getDataType()) {
			addStringBytesTypeChange(original, table, oldColumn, column, changed.keySet(), result);
			return;
		}
		if (changed.containsKey(SchemaProperties.ON_UPDATE.getLabel())
				&& changed.size() == (defaultChanged ? 2 : 1)) {
			addOnUpdateChange(original, table, oldColumn, column, defaultChanged, result);
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

	private void addOnUpdateChange(final Table original, final Table table, final Column oldColumn,
			final Column column, final boolean defaultChanged, final List<SqlOperation> result) {
		requireNonKey(original, oldColumn);
		if (column.getDataType() != DataType.TIMESTAMP || column.getArrayDimension() != 0 || column.isIdentity()
				|| !CommonUtils.isEmpty(column.getFormula()))
			throw new IllegalArgumentException("Spanner ON UPDATE requires a scalar TIMESTAMP column: " + column.getName());
		final String expression = column.getOnUpdate();
		final boolean remove = CommonUtils.isEmpty(expression);
		final String defaultValue = column.getDefaultValue();
		if (!remove && (!Boolean.TRUE.equals(SpannerSqlBuilder.allowCommitTimestamp(column))
				|| !COMMIT_TIMESTAMP_EXPRESSION.matcher(expression).matches()
				|| defaultValue == null || !COMMIT_TIMESTAMP_EXPRESSION.matcher(defaultValue).matches())) {
			throw new IllegalArgumentException("Spanner SET ON UPDATE requires allow_commit_timestamp=true "
					+ "and matching PENDING_COMMIT_TIMESTAMP() default and update expressions: " + column.getName());
		}
		if (defaultChanged && remove && !CommonUtils.isEmpty(defaultValue))
			throw new UnsupportedOperationException("Spanner ON UPDATE removal can be combined with DROP DEFAULT only");

		final var builder = createSqlBuilder();
		builder.alter().table().name(table, getOptions().isDecorateSchemaName()).alterColumn().name(column).space();
		if (defaultChanged) {
			// A typed ALTER applies the pair together and retains the column's nullability.
			builder.definitionForAlterColumn(column.clone().setOnUpdate(remove ? null : defaultValue));
		} else if (remove) {
			builder._add("DROP ON UPDATE");
		} else {
			builder._add("SET ON UPDATE (")._add(defaultValue.strip())._add(")");
		}
		add(result, createOperation(builder.toString(), SqlType.ALTER, oldColumn, column));
	}

	private void addStringBytesTypeChange(final Table original, final Table table, final Column before,
			final Column after, final java.util.Set<String> changes, final List<SqlOperation> result) {
		final boolean supported = before.getDataType() == DataType.VARCHAR && after.getDataType() == DataType.VARBINARY
				|| before.getDataType() == DataType.VARBINARY && after.getDataType() == DataType.VARCHAR;
		if (!supported || before.getArrayDimension() != 0 || after.getArrayDimension() != 0
				|| !isNativeLengthName(before) || !isNativeLengthName(after)
				|| changes.stream().anyMatch(property -> !SchemaProperties.DATA_TYPE.getLabel().equals(property)
						&& !SchemaProperties.DATA_TYPE_NAME.getLabel().equals(property)
						&& !SchemaProperties.LENGTH.getLabel().equals(property)))
			throw new UnsupportedOperationException("Spanner native type migration supports scalar STRING/BYTES only: "
					+ after.getName());
		requireNonKey(original, before);
		if (before.isIdentity() || after.isIdentity() || !CommonUtils.isEmpty(before.getFormula())
				|| !CommonUtils.isEmpty(after.getFormula()) || !CommonUtils.isEmpty(before.getDefaultValue())
				|| !CommonUtils.isEmpty(after.getDefaultValue()) || !CommonUtils.isEmpty(after.getOnUpdate())
				|| original.getConstraints().stream().anyMatch(constraint -> constraint instanceof CheckConstraint
						|| constraint instanceof com.sqlapp.data.schemas.AbstractColumnConstraint<?> columns
								&& columns.getColumns().stream().anyMatch(column -> column.getName().equalsIgnoreCase(before.getName())))
				|| original.getIndexes().stream().anyMatch(index -> index.getColumns().contains(before.getName())
						|| index.getIncludes().contains(before.getName()))
				|| original.getColumns().stream().anyMatch(column -> !CommonUtils.isEmpty(column.getFormula())))
			throw new UnsupportedOperationException("Spanner type migration requires removal of modeled dependencies "
					+ "and expressions first: " + after.getName());
		final Long length = after.getLength();
		final var type = length == null ? null : getDialect().getDbDataTypes().getDbType(after.getDataType(), length);
		if (length == null || length <= 0 || !(type instanceof LengthProperties<?> sized)
				|| sized.getMaxLength() == null || length > sized.getMaxLength())
			throw new IllegalArgumentException("Spanner type migration requires an explicit valid target length: "
					+ after.getName());
		final var builder = createSqlBuilder();
		builder.alter().table().name(table, getOptions().isDecorateSchemaName()).alterColumn().name(after)
				.space().definitionForAlterColumn(after);
		add(result, createOperation(builder.toString(), SqlType.ALTER, before, after));
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
