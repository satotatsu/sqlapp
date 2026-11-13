/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2.common;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.db2.common.AbstractDb2HostDialect.Platform;
import com.sqlapp.data.db.dialect.db2.util.Db2CreationContext;
import com.sqlapp.data.db.sql.AlterTableFactory;
import com.sqlapp.data.db.sql.SqlFactory;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Constraint;
import com.sqlapp.data.schemas.DbObjectDifference;
import com.sqlapp.data.schemas.Difference;
import com.sqlapp.data.schemas.Index;
import com.sqlapp.data.schemas.SchemaObjectProperties;
import com.sqlapp.data.schemas.SchemaProperties;
import com.sqlapp.data.schemas.State;
import com.sqlapp.data.schemas.Table;

/**
 * Host table ALTER limited to changes both products apply in place: add column,
 * DROP COLUMN ... RESTRICT, widening SET DATA TYPE, SET/DROP DEFAULT,
 * nullability (IBM i only), RENAME COLUMN (z/OS only), keys, indexes (drop and
 * recreate) and comments. Every other change, including partitioning, temporal,
 * physical and access-control attributes, needs an explicit migration plan and
 * fails instead of producing a partial or invalid script. No REORG or data
 * backfill is generated.
 */
public class Db2HostAlterTableFactory extends AlterTableFactory {
	private static final Set<String> TABLE_PROPERTIES = Set.of(SchemaObjectProperties.COLUMNS.getLabel(),
			SchemaObjectProperties.INDEXES.getLabel(), SchemaObjectProperties.CONSTRAINTS.getLabel(),
			SchemaProperties.REMARKS.getLabel(), SchemaProperties.NAME.getLabel());
	private static final Set<String> COLUMN_PROPERTIES = Set.of("name", "dataType", "length", "scale", "defaultValue",
			"notNull", "remarks");

	private Platform platform() {
		return ((AbstractDb2HostDialect) getDialect()).getPlatform();
	}

	@Override
	public List<SqlOperation> createDiffSql(DbObjectDifference difference) {
		if (difference.getState() == State.Unchanged)
			return List.of();
		Table before = difference.getOriginal(Table.class), after = difference.getTarget(Table.class);
		if (difference.getState() == State.Added || difference.getState() == State.Deleted) {
			Table value = before == null ? after : before;
			SqlFactory<Table> factory = getSqlFactoryRegistry().getSqlFactory(value,
					before == null ? SqlType.CREATE : SqlType.DROP);
			return factory.createSql(value);
		}
		Map<String, Difference<?>> changed = difference.toDifference().getChangedProperties(getDialect());
		if (!TABLE_PROPERTIES.containsAll(changed.keySet()))
			throw new IllegalArgumentException("Db2 host table ALTER needs an explicit migration plan for attributes: "
					+ changed.keySet() + " (" + after.getName() + ")");
		if (changed.containsKey(SchemaProperties.NAME.getLabel())) {
			// Db2 renames tables with RENAME TABLE, not ALTER TABLE ... RENAME TO.
			List<SqlOperation> result = new java.util.ArrayList<>();
			result.add(new SqlOperation(
					"RENAME TABLE " + qualified(before) + " TO " + Db2CreationContext.identifier(after.getName()),
					SqlType.ALTER, before, after));
			var renamed = before.clone();
			renamed.setName(after.getName());
			result.addAll(createDiffSql(renamed.diff(after)));
			return result;
		}
		return super.createDiffSql(difference);
	}

	@Override
	protected void addAddColumn(Table original, Table table, DbObjectDifference difference, List<SqlOperation> result) {
		Column column = difference.getTarget(Column.class);
		if (column.isIdentity() || column.getFormula() != null || !column.getDefinition().isEmpty()
				|| !column.getStatement().isEmpty() || column.getSpecifics().get(Db2HostTables.GENERATION) != null
				|| column.getSpecifics().get(Db2HostTables.ROW_CHANGE) != null)
			throw new IllegalArgumentException(
					"Db2 host ADD COLUMN with generated attributes requires an explicit native plan: "
							+ column.getName());
		var builder = new Db2HostSqlBuilder(getDialect());
		builder.definition(column, false);
		result.add(
				new SqlOperation(
						"ALTER TABLE " + qualified(table) + " ADD COLUMN "
								+ Db2CreationContext.identifier(column.getName()) + " " + builder.toString().strip(),
						SqlType.ALTER, null, column));
		if (column.getRemarks() != null)
			result.add(comment("COLUMN " + qualified(table) + "." + Db2CreationContext.identifier(column.getName()),
					column.getRemarks(), column));
	}

	@Override
	protected void addDeleteColumn(Table original, Table table, DbObjectDifference difference,
			List<SqlOperation> result) {
		Column column = difference.getOriginal(Column.class);
		// RESTRICT keeps IBM i (default CASCADE) from silently dropping dependents.
		result.add(new SqlOperation("ALTER TABLE " + qualified(table) + " DROP COLUMN "
				+ Db2CreationContext.identifier(column.getName()) + " RESTRICT", SqlType.ALTER, column));
		if (platform() == Platform.ZOS)
			result.add(reorgNote(table, column));
	}

	@Override
	protected void addRenameOrAlterColumn(Table original, Table table, DbObjectDifference difference,
			List<SqlOperation> result) {
		Column before = difference.getOriginal(Column.class), after = difference.getTarget(Column.class);
		var changed = difference.getChangedProperties().keySet();
		if (!COLUMN_PROPERTIES.containsAll(changed))
			throw unsupported(before, "attributes " + changed);
		boolean substantive = changed.stream().anyMatch(key -> !key.equals("remarks"));
		if (substantive && (before.isIdentity() || after.isIdentity() || before.getFormula() != null
				|| after.getFormula() != null || before.getSpecifics().get(Db2HostTables.GENERATION) != null
				|| before.getSpecifics().get(Db2HostTables.ROW_CHANGE) != null))
			throw unsupported(before, "identity/generated columns");
		String prefix = "ALTER TABLE " + qualified(table) + " ALTER COLUMN "
				+ Db2CreationContext.identifier(before.getName());
		boolean pending = false;
		if (changed.stream().anyMatch(Set.of("dataType", "length", "scale")::contains)) {
			validateWidening(before, after);
			String type = new TypeRenderer(getDialect()).render(after);
			result.add(new SqlOperation(prefix + " SET DATA TYPE " + type, SqlType.ALTER, before, after));
			pending = true;
		}
		if (changed.contains("defaultValue")) {
			String expression = after.getDefaultValue();
			if (expression != null && (expression.isBlank() || expression.indexOf('\0') >= 0))
				throw unsupported(before, "invalid default expression");
			result.add(new SqlOperation(prefix + (expression == null ? " DROP DEFAULT" : " SET DEFAULT " + expression),
					SqlType.ALTER, before, after));
		}
		if (changed.contains("notNull")) {
			// Db2 for z/OS cannot change nullability in place.
			if (platform() != Platform.IBM_I)
				throw unsupported(before, "nullability change (Db2 for z/OS requires recreating the table)");
			result.add(new SqlOperation(prefix + (after.isNotNull() ? " SET NOT NULL" : " DROP NOT NULL"),
					SqlType.ALTER, before, after));
		}
		if (changed.contains("remarks"))
			result.add(comment("COLUMN " + qualified(table) + "." + Db2CreationContext.identifier(before.getName()),
					after.getRemarks(), after));
		if (changed.contains("name")) {
			// Db2 for i has no RENAME COLUMN.
			if (platform() != Platform.ZOS)
				throw unsupported(before, "column rename (Db2 for i has no RENAME COLUMN)");
			result.add(new SqlOperation("ALTER TABLE " + qualified(table) + " RENAME COLUMN "
					+ Db2CreationContext.identifier(before.getName()) + " TO "
					+ Db2CreationContext.identifier(after.getName()), SqlType.ALTER, before, after));
		}
		if (pending && platform() == Platform.ZOS)
			result.add(reorgNote(table, before));
	}

	@Override
	protected void addOtherDefinitions(Map<String, Difference<?>> differences, Table original, Table table,
			List<SqlOperation> result) {
		super.addOtherDefinitions(differences, original, table, result);
		if (differences.containsKey(SchemaProperties.REMARKS.getLabel()))
			result.add(comment("TABLE " + qualified(table), table.getRemarks(), table));
	}

	@Override
	protected void addCreateIndexDefinition(Table originalTable, Table table, Index originalIndex, Index index,
			DbObjectDifference difference, List<SqlOperation> result) {
		SqlFactory<Index> factory = getSqlFactoryRegistry().getSqlFactory(index, SqlType.CREATE);
		result.addAll(factory.createSql(index));
	}

	@Override
	protected void addDropIndexDefinition(Table originalTable, Table table, Index originalIndex, Index index,
			DbObjectDifference difference, List<SqlOperation> result) {
		SqlFactory<Index> factory = getSqlFactoryRegistry().getSqlFactory(originalIndex, SqlType.DROP);
		result.addAll(factory.createSql(originalIndex));
	}

	@Override
	protected void addCreateConstraintDefinition(Table originalTable, Table table, Constraint originalConstraint,
			Constraint constraint, DbObjectDifference difference, List<SqlOperation> result) {
		if (constraint.getSpecifics().get(Db2HostIndexes.BUSINESS_TIME_WITHOUT_OVERLAPS) != null
				|| !constraint.isEnable())
			throw new IllegalArgumentException(
					"Adding WITHOUT OVERLAPS or non-enforced keys requires an explicit native plan: "
							+ constraint.getName());
		super.addCreateConstraintDefinition(originalTable, table, originalConstraint, constraint, difference, result);
	}

	@Override
	protected void dropConstraintDefinition(Constraint constraint, com.sqlapp.util.AbstractSqlBuilder<?> builder) {
		builder._add("ALTER TABLE " + qualified(constraint.getParent().getTable()) + " DROP CONSTRAINT "
				+ Db2CreationContext.identifier(constraint.getName()));
	}

	private SqlOperation comment(String target, String remarks, com.sqlapp.data.schemas.DbCommonObject<?> object) {
		var builder = new Db2HostSqlBuilder(getDialect());
		builder._add("COMMENT ON " + target + " IS ").sqlChar(remarks == null ? "" : remarks);
		return new SqlOperation(builder.toString(), SqlType.SET_COMMENT, object);
	}

	private static SqlOperation reorgNote(Table table, com.sqlapp.data.schemas.DbCommonObject<?> object) {
		return new SqlOperation(
				"-- Db2 for z/OS applies this change as a pending definition change on " + qualified(table)
						+ "; REORG is required to materialize it. No REORG or data backfill is performed by this plan.",
				SqlType.COMMENT, object);
	}

	static String qualified(Table table) {
		return table.getSchemaName() == null ? Db2CreationContext.identifier(table.getName())
				: Db2CreationContext.qualified(table.getSchemaName(), table.getName());
	}

	private void validateWidening(Column before, Column after) {
		var integers = List.of(DataType.SMALLINT, DataType.INT, DataType.BIGINT);
		if (before.getDataType() != after.getDataType()) {
			if (integers.contains(before.getDataType())
					&& integers.indexOf(after.getDataType()) > integers.indexOf(before.getDataType()))
				return;
			throw unsupported(before, "type conversion/narrowing");
		}
		if (Set.of(DataType.CHAR, DataType.VARCHAR, DataType.NVARCHAR, DataType.VARBINARY, DataType.DECIMAL,
				DataType.NUMERIC).contains(after.getDataType()) && before.getLength() != null
				&& after.getLength() != null && after.getLength() > before.getLength()
				&& Objects.equals(before.getScale(), after.getScale()))
			return;
		throw unsupported(before, "length/scale change outside supported widening");
	}

	private static IllegalArgumentException unsupported(Column column, String reason) {
		return new IllegalArgumentException("Db2 host column ALTER requires an explicit native migration plan for "
				+ reason + ": " + column.getName());
	}

	private static final class TypeRenderer extends Db2HostSqlBuilder {
		private static final long serialVersionUID = 1L;

		TypeRenderer(com.sqlapp.data.db.dialect.Dialect dialect) {
			super(dialect);
		}

		String render(Column column) {
			return typeDefinition(column).toString();
		}
	}
}
