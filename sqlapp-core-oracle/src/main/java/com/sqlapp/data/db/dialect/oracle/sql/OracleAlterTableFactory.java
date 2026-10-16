/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-oracle.
 *
 * sqlapp-core-oracle is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-oracle is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-oracle.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.oracle.sql;

import java.util.List;
import java.util.Map;

import com.sqlapp.data.db.dialect.oracle.util.OracleSqlBuilder;
import com.sqlapp.data.db.dialect.oracle.util.OracleUtils;
import com.sqlapp.data.db.sql.AbstractAlterTableFactory;
import com.sqlapp.data.db.sql.AddObjectDetail;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.DbObjectDifference;
import com.sqlapp.data.schemas.DbObjectDifferenceCollection;
import com.sqlapp.data.schemas.DbObjectPropertyDifference;
import com.sqlapp.data.schemas.Difference;
import com.sqlapp.data.schemas.Partition;
import com.sqlapp.data.schemas.Partitioning;
import com.sqlapp.data.schemas.SchemaObjectProperties;
import com.sqlapp.data.schemas.SchemaProperties;
import com.sqlapp.data.schemas.State;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.util.AbstractSqlBuilder;
import com.sqlapp.util.CommonUtils;

/**
 * ORacleのalter tableコマンド
 * 
 * @author tatsuo satoh
 * 
 */
public class OracleAlterTableFactory extends AbstractAlterTableFactory<OracleSqlBuilder> {

	@Override
	public List<SqlOperation> createDiffSql(DbObjectDifference difference) {
		Table before = difference.getOriginal(Table.class), after = difference.getTarget(Table.class);
		if (before != null && after != null
				&& !java.util.Objects.equals(before.getPartitioning(), after.getPartitioning())
				&& (OracleTablePartitionMaintenance.iot(before) || OracleLocalPartitionSplit.hasLocalIndexes(before)
						|| OracleLocalPartitionSplit.hasLocalIndexes(after))) {
			com.sqlapp.data.db.dialect.oracle.util.OracleTableDefinition.get(before);
			if (after.getIndexes().stream().anyMatch(index -> getTableOptions().getOnlineIndex().test(after, index)))
				throw new IllegalArgumentException("ONLINE index maintenance is not supported by the table partition planner");
			var plan = OracleTablePartitionMaintenance.create(before, after, getDialect(), getOptions().isDecorateSchemaName(),
					getTableOptions().getAllowAddPartition().test(after), getTableOptions().getAllowDropPartition().test(after), () -> createSqlBuilder(getDialect()));
			if (plan != null) {
				List<SqlOperation> operations = new java.util.ArrayList<>();
				for (String statement : plan.statements())
					operations.add(plan.removed() == null ? createOperation(statement, SqlType.ALTER, before, after)
							: createOperation(statement, SqlType.DROP, plan.removed(), null));
				return operations;
			}
			OracleSqlBuilder builder = createSqlBuilder();
			OracleLocalPartitionSplit.add(before, after, getDialect(), getOptions().isDecorateSchemaName(), builder);
			return List.of(createOperation(builder.toString(), SqlType.ALTER, before, after));
		}
		boolean rootMove = before != null && after != null && OracleTablePartitionMaintenance.iot(before)
				&& !java.util.Objects.equals(before.getTableSpaceName(), after.getTableSpaceName());
		if (before != null && after != null) {
			// An opaque table definition cannot silently become inconsistent after ALTER.
			com.sqlapp.data.db.dialect.oracle.util.OracleTableDefinition.get(before);
			if (!rootMove) {
				com.sqlapp.data.db.dialect.oracle.util.OracleTableDefinition.get(after);
				if (before.getSpecifics()
						.containsKey(com.sqlapp.data.db.dialect.oracle.util.OracleTableDefinition.DEFINITION)
						|| after.getSpecifics()
								.containsKey(com.sqlapp.data.db.dialect.oracle.util.OracleTableDefinition.DEFINITION)) {
					if (!com.sqlapp.data.db.dialect.oracle.util.OracleTableDefinition.fingerprint(before)
							.equals(com.sqlapp.data.db.dialect.oracle.util.OracleTableDefinition.fingerprint(after)))
						throw new IllegalArgumentException(
								"Structural ALTER of a complete Oracle IOT definition requires an explicit migration plan");
				}
			}
		}
		if (rootMove) {
			var changes = before.diff(after).toDifference().getChangedProperties(getDialect());
			if (changes.size() != 1 || !changes.containsKey(SchemaProperties.TABLE_SPACE_NAME.getLabel())
					|| before.getPartitioning() != null || before.getTableType() == Table.TableType.Temporary
					|| before.getSpecifics().containsKey("oracle.table.iotComplex")
					|| "true".equals(before.getSpecifics().get("oracle.table.iot.mapping"))
					|| after.getTableSpaceName() == null || after.getTableSpaceName().isBlank())
				throw new IllegalArgumentException(
						"IOT root MOVE requires one isolated nonpartitioned tablespace change");
			for (var index : after.getIndexes()) {
				OracleTablePartitionMaintenance.ordinary(index);
				if (index.getIndexType() != null && index.getIndexType().isBitMap()
						|| "true".equalsIgnoreCase(index.getSpecifics().get("oracle.index.bitmap")))
					throw new IllegalArgumentException("IOT root MOVE with bitmap indexes requires explicit index-state handling");
				if (getTableOptions().getOnlineIndex().test(after, index))
					throw new IllegalArgumentException("ONLINE IOT MOVE requires a dedicated table option");
			}
			OracleSqlBuilder builder = createSqlBuilder(getDialect());
			builder.alter().table().space().name(before, getOptions().isDecorateSchemaName()).space()
					._add("MOVE TABLESPACE ").name(after.getTableSpaceName());
			OracleIotOptions.addPreservedPrefix(before, builder);
			return List.of(createOperation(builder.toString(), SqlType.ALTER, before, after));
		}
		return super.createDiffSql(difference);
	}

	@Override
	protected void addIndexDefinition(Table originalTable, Table table, com.sqlapp.data.schemas.Index originalIndex,
			com.sqlapp.data.schemas.Index index, DbObjectDifference diff, List<SqlOperation> result) {
		if (diff.getState() == State.Modified && (OracleAlterIndexFactory.hasStateChange(originalIndex, index)
				|| OracleIndexRebuildOptions.physicalChange(originalIndex, index)
				|| OracleIndexPartitionLifecycle.structureChanged(originalIndex, index, getDialect()))) {
			result.addAll(getSqlFactoryRegistry().getSqlFactory(index, SqlType.ALTER).createDiffSql(diff));
			return;
		}
		super.addIndexDefinition(originalTable, table, originalIndex, index, diff, result);
	}

	@Override
	protected void addOtherDefinitions(final Map<String, Difference<?>> allDiff, final Table originalTable,
			final Table table, final List<SqlOperation> result) {
		final Map<String, Difference<?>> diff = this.getAll(allDiff, OracleUtils.getTableStatisticsKeys());
		if (diff.isEmpty()) {
			addCommentDefinitions(allDiff, originalTable, table, result);
			return;
		}
		final OracleSqlBuilder builder = createSqlBuilder();
		builder.alter().table();
		builder.name(table);
		builder.appendIndent(1);
		boolean hasChange = false;
		boolean hasStorage = false;
		for (final Map.Entry<String, Difference<?>> entry : diff.entrySet()) {
			if (!entry.getValue().getState().isChanged()) {
				continue;
			}
			final Difference<?> diffValue = entry.getValue();
			if (diffValue.getTarget() == null) {
				continue;
			}
			if (builder.isStoragePropertyName(entry.getKey())) {
				continue;
			} else {
				hasStorage = true;
			}
			builder.lineBreak().oracleProperty(entry.getKey(), diffValue.getTarget(String.class));
			hasChange = true;
		}
		if (hasStorage) {
			builder.lineBreak().storage();
			builder.lineBreak()._add("(");
			builder.appendIndent(1);
			for (final Map.Entry<String, Difference<?>> entry : diff.entrySet()) {
				if (!entry.getValue().getState().isChanged()) {
					continue;
				}
				if (!builder.isStoragePropertyName(entry.getKey())) {
					continue;
				}
				final Difference<?> diffValue = entry.getValue();
				if (diffValue.getTarget() == null) {
					continue;
				}
				builder.lineBreak().oracleProperty(entry.getKey(), diffValue.getTarget(String.class));
			}
			builder.appendIndent(-1);
			builder.lineBreak()._add(")");
			hasChange = true;
		}
		builder.appendIndent(-1);
		if (hasChange) {
			add(result, createOperation(builder.toString(), SqlType.ALTER, originalTable, table));
		}
		addCommentDefinitions(allDiff, originalTable, table, result);
	}

	protected void addCommentDefinitions(final Map<String, Difference<?>> allDiff, final Table originalTable,
			final Table table, final List<SqlOperation> result) {
		final Difference<?> tableProp = allDiff.get(SchemaProperties.REMARKS.getLabel());
		if (tableProp != null && tableProp.getState().isChanged()) {
			final OracleSqlBuilder builder = this.createSqlBuilder();
			builder.comment().on().table().space().name(table, this.getOptions().isDecorateSchemaName()).is()
					.$if(table.getRemarks() != null, () -> builder.sqlChar(table.getRemarks()), () -> builder.null_());
			addSql(result, builder, SqlType.SET_COMMENT, table);
		}
		final DbObjectDifferenceCollection colsDiff = (DbObjectDifferenceCollection) allDiff
				.get(SchemaObjectProperties.COLUMNS.getLabel());
		if (colsDiff != null) {
			for (final DbObjectDifference diff : colsDiff.getList(State.Modified)) {
				final Column obj = diff.getTarget(Column.class);
				if (CommonUtils.eq(diff.getOriginal(Column.class).getRemarks(), obj.getRemarks()))
					continue;
				final OracleSqlBuilder builder = this.createSqlBuilder();
				builder.comment().on().column().space().columnName(obj, true, this.getOptions().isDecorateSchemaName())
						.is()
						.$if(obj.getRemarks() != null, () -> builder.sqlChar(obj.getRemarks()), () -> builder.null_());
				addSql(result, builder, SqlType.SET_COMMENT, obj);
			}
		}
	}

	/**
	 * カラム定義を追加します
	 * 
	 * @param originalTable
	 * @param table
	 * @param colDiff
	 * @param sqlBuilder
	 */
	@Override
	protected void addColumnDefinitions(final Map<String, Difference<?>> allDiff, final Table originalTable,
			final Table table, final DbObjectDifferenceCollection colsDiff, final List<SqlOperation> result) {
		addDeleteColumn(originalTable, table, colsDiff.getList(State.Deleted), result);
		addAddColumn(originalTable, table, colsDiff.getList(State.Added), result);
		addRenameOrAlterColumn(originalTable, table, colsDiff.getList(State.Modified), result);
	}

	protected void addDeleteColumn(final Table originalTable, final Table table, final List<DbObjectDifference> diffs,
			final List<SqlOperation> result) {
		if (CommonUtils.isEmpty(diffs)) {
			return;
		}
		final OracleSqlBuilder builder = createSqlBuilder();
		builder.alter().table();
		builder.name(table, this.getOptions().isDecorateSchemaName());
		builder.drop().space()._add("( ");
		builder.appendIndent(1);
		int i = 0;
		final List<Column> columns = CommonUtils.list();
		for (final DbObjectDifference diff : diffs) {
			final Column column = diff.getOriginal(Column.class);
			builder._add(", ", i > 0);
			builder.name(column.getName());
			columns.add(column);
			i++;
		}
		builder.appendIndent(-1);
		builder._add(" )");
		add(result, createOperation(builder.toString(), SqlType.ALTER, columns));
	}

	protected void addAddColumn(final Table originalTable, final Table table, final List<DbObjectDifference> diffs,
			final List<SqlOperation> result) {
		if (CommonUtils.isEmpty(diffs)) {
			return;
		}
		final List<Column> objects = CommonUtils.list();
		final OracleSqlBuilder builder = createSqlBuilder();
		builder.alter().table();
		builder.name(table, this.getOptions().isDecorateSchemaName());
		builder.add().space()._add("(");
		builder.appendIndent(1);
		int i = 0;
		for (final DbObjectDifference diff : diffs) {
			final Column column = diff.getTarget(Column.class);
			objects.add(column);
			builder.lineBreak(i == 0)._add("  ", i == 0);
			builder.lineBreak(i > 0)._add(", ", i > 0);
			builder.name(column);
			builder.space().definition(column, this.getTableOptions().getWithColumnRemarks().test(column));
			i++;
		}
		builder.appendIndent(-1);
		builder.lineBreak()._add(")");
		add(result, createOperation(builder.toString(), SqlType.ALTER, objects));
	}

	protected void addRenameOrAlterColumn(final Table originalTable, final Table table,
			final List<DbObjectDifference> diffs, final List<SqlOperation> result) {
		if (CommonUtils.isEmpty(diffs)) {
			return;
		}
		final List<DbObjectDifference> renameList = CommonUtils.list();
		final List<DbObjectDifference> modifyList = CommonUtils.list();
		final List<DbObjectDifference> dropAddList = CommonUtils.list();
		for (final DbObjectDifference diff : diffs) {
			final Column oldColumn = diff.getOriginal(Column.class);
			final Column column = diff.getTarget(Column.class);
			boolean generated = oldColumn.isIdentity() || column.isIdentity()
					|| !CommonUtils.isEmpty(oldColumn.getFormula()) || !CommonUtils.isEmpty(column.getFormula());
			Column commentOnly = oldColumn.clone().setRemarks(column.getRemarks());
			if (commentOnly.equals(column))
				continue;
			if (!CommonUtils.isEmpty(oldColumn.getFormula()) && !CommonUtils.isEmpty(column.getFormula())
					&& !CommonUtils.eq(oldColumn.getFormula(), column.getFormula())) {
				if (column.getFormula().isBlank() || column.isIdentity() || column.isFormulaPersisted())
					throw new IllegalArgumentException(
							"Invalid Oracle virtual column expression settings: " + column.getName());
				Column expressionOnly = oldColumn.clone().setFormula(column.getFormula());
				if (!expressionOnly.equals(column))
					throw new IllegalArgumentException(
							"Change virtual column expression separately from other properties: " + column.getName());
				if (!(getDialect() instanceof com.sqlapp.data.db.dialect.oracle.Oracle11g)
						&& !(getDialect() instanceof com.sqlapp.data.db.dialect.oracle.Oracle11gR2))
					throw new IllegalArgumentException("Virtual columns require Oracle 11g or later");
				if (!originalTable.getIndexes().isEmpty() || !originalTable.getConstraints().isEmpty())
					throw new IllegalArgumentException(
							"Remove modeled indexes/constraints before changing a virtual column expression: "
									+ column.getName());
				OracleSqlBuilder expression = createSqlBuilder();
				expression.alter().table().name(table, getOptions().isDecorateSchemaName()).modify().space()._add("(")
						.name(column).space()._add("GENERATED ALWAYS AS (")._add(column.getFormula())
						._add(") VIRTUAL)");
				addSql(result, expression, SqlType.ALTER, column);
				continue;
			}
			if (!CommonUtils.eq(oldColumn.getSpecifics().get("oracle.column.invisible"),
					column.getSpecifics().get("oracle.column.invisible"))) {
				Column oldVisible = oldColumn.clone(), newVisible = column.clone();
				oldVisible.getSpecifics().remove("oracle.column.invisible");
				newVisible.getSpecifics().remove("oracle.column.invisible");
				if (!oldVisible.equals(newVisible))
					throw new IllegalArgumentException(
							"Change column visibility separately from other column properties: " + column.getName());
				if (!(getDialect() instanceof com.sqlapp.data.db.dialect.oracle.Oracle12c))
					throw new IllegalArgumentException("Invisible columns require Oracle 12c or later");
				String value = column.getSpecifics().get("oracle.column.invisible");
				if (value != null && !"true".equalsIgnoreCase(value) && !"false".equalsIgnoreCase(value))
					throw new IllegalArgumentException("Invalid column visibility: " + column.getName());
				OracleSqlBuilder visibility = createSqlBuilder();
				visibility.alter().table().name(table, getOptions().isDecorateSchemaName()).modify().space()._add("(")
						.name(column).space()._add(Boolean.parseBoolean(value) ? "INVISIBLE" : "VISIBLE")._add(")");
				addSql(result, visibility, SqlType.ALTER, column);
				continue;
			}
			boolean renameOnly = !CommonUtils.eq(oldColumn.getName(), column.getName())
					&& diff.getProperties().size() == 1;
			if (oldColumn.isIdentity() && column.isIdentity() && !renameOnly) {
				Column modeOnly = oldColumn.clone().setIdentityGenerationType(column.getIdentityGenerationType());
				var identityOptions = OracleIdentityOptions.changes(oldColumn.getSequence(), column.getSequence());
				modeOnly.setSequence(column.getSequence() == null ? null : column.getSequence().clone());
				modeOnly.getSpecifics().remove("DEFAULT_ON_NULL");
				if (column.getSpecifics().containsKey("DEFAULT_ON_NULL"))
					modeOnly.getSpecifics().put("DEFAULT_ON_NULL", column.getSpecifics().get("DEFAULT_ON_NULL"));
				if (!modeOnly.equals(column))
					throw new IllegalArgumentException(
							"Change identity generation mode separately; identity sequence and physical column changes are not supported: "
									+ column.getName());
				if (!(getDialect() instanceof com.sqlapp.data.db.dialect.oracle.Oracle12c))
					throw new IllegalArgumentException("Identity columns require Oracle 12c or later");
				String onNull = column.getSpecifics().get("DEFAULT_ON_NULL");
				if (onNull != null && !"true".equalsIgnoreCase(onNull) && !"false".equalsIgnoreCase(onNull))
					throw new IllegalArgumentException("Invalid DEFAULT_ON_NULL: " + column.getName());
				if (column.getIdentityGenerationType() == null
						|| column.getIdentityGenerationType() == com.sqlapp.data.schemas.IdentityGenerationType.Always
								&& Boolean.parseBoolean(onNull))
					throw new IllegalArgumentException("Invalid identity generation mode: " + column.getName());
				OracleSqlBuilder identity = createSqlBuilder();
				identity.alter().table().name(table, getOptions().isDecorateSchemaName()).modify().space()._add("(")
						.name(column).space()._add("GENERATED ")._add(column.getIdentityGenerationType().getSqlValue());
				if (Boolean.parseBoolean(onNull))
					identity.space()._add("ON NULL");
				identity.space()._add("AS IDENTITY");
				if (!identityOptions.isEmpty())
					identity.space()._add("(")._add(String.join(" ", identityOptions))._add(")");
				identity._add(")");
				addSql(result, identity, SqlType.ALTER, column);
				continue;
			}
			if (generated && !renameOnly)
				throw new IllegalArgumentException(
						"Structured modification of Oracle identity/virtual columns is not supported: "
								+ column.getName());
			if (!CommonUtils.eq(oldColumn.getName(), column.getName())) {
				if (diff.getProperties().size() == 1) {
					renameList.add(diff);
				} else {
					dropAddList.add(diff);
				}
			} else {
				modifyList.add(diff);
			}
		}
		addRenameColumn(originalTable, table, renameList, result);
		addModifyColumn(originalTable, table, modifyList, result);
		addDeleteColumn(originalTable, table, dropAddList, result);
		addAddColumn(originalTable, table, dropAddList, result);
	}

	protected void addRenameColumn(final Table originalTable, final Table table, final List<DbObjectDifference> diffs,
			final List<SqlOperation> result) {
		if (CommonUtils.isEmpty(diffs)) {
			return;
		}
		for (final DbObjectDifference diff : diffs) {
			addRenameColumn(originalTable, table, diff, result);
		}
	}

	protected void addRenameColumn(final Table originalTable, final Table table, final DbObjectDifference diff,
			final List<SqlOperation> result) {
		final OracleSqlBuilder builder = createSqlBuilder();
		builder.alter().table();
		builder.name(table, this.getOptions().isDecorateSchemaName());
		builder.rename().column();
		final Column orgColumn = diff.getOriginal(Column.class);
		builder.name(orgColumn);
		final Column column = diff.getTarget(Column.class);
		builder.to();
		builder.name(column);
		add(result, createOperation(builder.toString(), SqlType.ALTER, orgColumn, column));
	}

	protected void addModifyColumn(final Table originalTable, final Table table, final List<DbObjectDifference> diffs,
			final List<SqlOperation> result) {
		if (CommonUtils.isEmpty(diffs)) {
			return;
		}
		final OracleSqlBuilder builder = createSqlBuilder();
		builder.alter().table();
		builder.name(table, this.getOptions().isDecorateSchemaName());
		builder.modify().space()._add("(");
		builder.appendIndent(1);
		int i = 0;
		final List<Column> originals = CommonUtils.list();
		final List<Column> targets = CommonUtils.list();
		for (final DbObjectDifference diff : diffs) {
			final Column orgColumn = diff.getOriginal(Column.class);
			originals.add(orgColumn);
			final Column column = diff.getTarget(Column.class);
			targets.add(column);
			builder.lineBreak(i == 0)._add("  ", i == 0);
			builder.lineBreak(i > 0)._add(", ", i > 0);
			builder.name(column);
			builder.space().definition(column, this.getTableOptions().getWithColumnRemarks().test(column));
			i++;
		}
		builder.appendIndent(-1);
		builder.lineBreak()._add(")");
		add(result, createOperation(builder.toString(), SqlType.ALTER, originals, targets));
	}

	/**
	 * Partition定義を追加します
	 * 
	 * @param partitionInfoProp
	 * @param sqlBuilder
	 */
	@Override
	protected void addPartitionDefinition(final Map<String, Difference<?>> allDiff, final Table originalTable,
			final Table table, final DbObjectDifference partitioningProp, final List<SqlOperation> result) {
		final Partitioning originalPartitioning = partitioningProp.getOriginal(Partitioning.class);
		final Partitioning partitioning = partitioningProp.getTarget(Partitioning.class);
		final AddObjectDetail<Partitioning, AbstractSqlBuilder<?>> addObjectDetail = getAddObjectDetail(
				new Partitioning(), SqlType.CREATE);
		if (addObjectDetail == null) {
			return;
		}
		final OracleSqlBuilder builder = createSqlBuilder();
		builder.alter().table();
		builder.name(table, this.getOptions().isDecorateSchemaName());
		if (originalPartitioning == null) {
			if (partitioning != null) {
				addObjectDetail.addObjectDetail(partitioning, builder);
			}
			add(result, createOperation(builder.toString(), SqlType.CREATE, null, partitioning));
		} else if (partitioning == null) {
			builder.remove().partitioning();
			add(result, createOperation(builder.toString(), SqlType.DROP, originalPartitioning));
		} else {
			modifyPartitionByDefinition(partitioningProp, originalPartitioning, partitioning, builder);
			add(result, createOperation(builder.toString(), SqlType.ALTER, originalPartitioning, partitioning));
		}
	}

	/**
	 * Partition定義を変更します
	 * 
	 * @param partitioningProp
	 * @param originalPartitioning
	 * @param partitioning
	 * @param builder
	 */
	protected void modifyPartitionByDefinition(final DbObjectDifference partitioningProp,
			final Partitioning originalPartitioning, final Partitioning partitioning, final OracleSqlBuilder builder) {
		final Map<String, Difference<?>> allDiff = partitioningProp.getProperties(State.Modified, State.Added,
				State.Deleted);
		final DbObjectDifferenceCollection partitionsDifference = (DbObjectDifferenceCollection) allDiff
				.get(SchemaObjectProperties.PARTITIONS.getLabel());
		final DbObjectPropertyDifference partitionSizeDifference = (DbObjectPropertyDifference) allDiff
				.get(SchemaProperties.PARTITION_SIZE.getLabel());
		final DbObjectPropertyDifference subpartitionSizeDifference = (DbObjectPropertyDifference) allDiff
				.get(SchemaProperties.SUB_PARTITION_SIZE.getLabel());
		final AddObjectDetail<Partitioning, AbstractSqlBuilder<?>> addObjectDetail = getAddObjectDetail(partitioning,
				SqlType.CREATE);
		if (originalPartitioning.getPartitioningType() == partitioning.getPartitioningType()) {
			if (originalPartitioning.getSubPartitioningType() == partitioning.getSubPartitioningType()) {
				// パーティション種類未変更
				if (partitioning.getPartitioningType().isSizePartitioning() && partitionsDifference == null) {
					final Integer size = partitionSizeDifference.getTarget(Integer.class);
					// Hashパーティションサイズ変更
					builder.coalesce().partition().space()._add(size);
				} else {
					modifyPartitions(originalPartitioning, partitioning, partitionsDifference, builder);
				}
			} else {
				builder.remove().partitioning();
				builder.comma();
				addObjectDetail.addObjectDetail(partitioning, builder);
			}
		} else {
			builder.remove().partitioning();
			builder.comma();
			addObjectDetail.addObjectDetail(partitioning, builder);
		}
	}

	protected void modifyPartitions(final Partitioning originalPartitioning, final Partitioning partitioning,
			final DbObjectDifferenceCollection partitionsDifference, final OracleSqlBuilder builder) {
		List<DbObjectDifference> child = null;
		child = partitionsDifference.getList(State.Added);
		if (!CommonUtils.isEmpty(child)) {
			addPartitionsDefinition(partitioning, child, builder);
		}
		child = partitionsDifference.getList(State.Deleted);
		if (!CommonUtils.isEmpty(child)) {
			deletePartitionsDefinition(child, builder);
		}
		child = partitionsDifference.getList(State.Modified);
		if (!CommonUtils.isEmpty(child)) {
			deletePartitionsDefinition(child, builder);
			addPartitionsDefinition(partitioning, child, builder);
		}
	}

	protected void addPartitionsDefinition(final Partitioning partitioning, final List<DbObjectDifference> child,
			final OracleSqlBuilder builder) {
		final OracleCreatePartitioningFactory factory = this.getSqlFactoryRegistry().getSqlFactory(partitioning,
				SqlType.CREATE);
		if (factory == null) {
			return;
		}
		builder.add().partition();
		builder.lineBreak()._add("(");
		builder.appendIndent(1);
		int i = 0;
		for (final DbObjectDifference partitionDifference : child) {
			final Partition partition = partitionDifference.getTarget(Partition.class);
			builder.lineBreak().comma(i > 0);
			factory.appendPartitionDefinition(false, partitioning, partition, builder);
			i++;
		}
		builder.appendIndent(-1);
		builder.lineBreak()._add(")");
	}

	protected void deletePartitionsDefinition(final List<DbObjectDifference> child, final OracleSqlBuilder builder) {
		builder.comma(!builder.isFirstElement()).drop().partition();
		builder.setFirstElement(false);
		for (int i = 0; i < child.size(); i++) {
			final DbObjectDifference partitionDifference = child.get(i);
			final Partition partition = partitionDifference.getOriginal(Partition.class);
			builder.space(i == 0).comma(i != 0);
			builder.name(partition);
		}
	}
}
