/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-mysql.
 *
 * sqlapp-core-mysql is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-mysql is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-mysql.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.mysql.sql;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.sqlapp.data.db.dialect.mysql.util.MySqlSqlBuilder;
import com.sqlapp.data.db.sql.AbstractAlterTableFactory;
import com.sqlapp.data.db.sql.AddObjectDetail;
import com.sqlapp.data.db.sql.AddTableObjectDetailFactory;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.ColumnCollection;
import com.sqlapp.data.schemas.Constraint;
import com.sqlapp.data.schemas.DbObjectDifference;
import com.sqlapp.data.schemas.DbObjectDifferenceCollection;
import com.sqlapp.data.schemas.DbObjectPropertyDifference;
import com.sqlapp.data.schemas.Difference;
import com.sqlapp.data.schemas.ForeignKeyConstraint;
import com.sqlapp.data.schemas.Index;
import com.sqlapp.data.schemas.Partition;
import com.sqlapp.data.schemas.Partitioning;
import com.sqlapp.data.schemas.SchemaObjectProperties;
import com.sqlapp.data.schemas.SchemaProperties;
import com.sqlapp.data.schemas.State;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.UniqueConstraint;
import com.sqlapp.util.AbstractSqlBuilder;
import com.sqlapp.util.CommonUtils;

/**
 * MySQLのalter tableコマンド
 * 
 * @author tatsuo satoh
 * 
 */
public class MySqlAlterTableFactory extends AbstractAlterTableFactory<MySqlSqlBuilder> {

	@Override
	public List<SqlOperation> createDiffSql(final DbObjectDifference difference) {
		final Map<String, Difference<?>> allDiff = difference.toDifference()
				.getChangedProperties(this.getDialect());
		final List<SqlOperation> result = CommonUtils.list();
		final MySqlSqlBuilder builder = createSqlBuilder();
		final Table originalTable = difference.getOriginal(Table.class);
		final Table table = difference.getTarget(Table.class);
		addAlterTable(originalTable, builder);
		//
		Difference<?> tableProp = allDiff.get(SchemaProperties.NAME.getLabel());
		if (tableProp != null) {
			builder.rename().to();
			builder.name(table);
			builder.setFirstElement(false);
		}
		final String sql = builder.toString();
		addTableOptions(allDiff, builder);
		final DbObjectDifferenceCollection consDiff = (DbObjectDifferenceCollection) allDiff
				.get(SchemaObjectProperties.CONSTRAINTS.getLabel());
		if (consDiff != null) {
			addConstraintDefinitions(originalTable, table, consDiff.getList(State.Deleted), builder);
		}
		final DbObjectDifferenceCollection colsDiff = (DbObjectDifferenceCollection) allDiff
				.get(SchemaObjectProperties.COLUMNS.getLabel());
		if (colsDiff != null) {
			addColumnDefinitions(colsDiff, consDiff, builder);
		}
		if (consDiff != null) {
			addConstraintDefinitions(originalTable, table, consDiff.getList(State.Added, State.Modified), builder);
		}
		final DbObjectDifferenceCollection indexDiff = (DbObjectDifferenceCollection) allDiff
				.get(SchemaObjectProperties.INDEXES.getLabel());
		if (indexDiff != null) {
			addIndexDefinitions(originalTable, table, indexDiff, consDiff, builder);
		}
		tableProp = allDiff.get(SchemaObjectProperties.PARTITIONING.getLabel());
		if (tableProp != null) {
			addPartitionDefinition(originalTable, table, (DbObjectDifference) tableProp, builder);
		}
		final Difference<?> charSetProp = allDiff.get(SchemaProperties.CHARACTER_SET.getLabel());
		final Difference<?> collationProp = allDiff.get(SchemaProperties.COLLATION.getLabel());
		if (charSetProp != null || collationProp != null) {
			addCharSetDefinition(charSetProp, collationProp, builder);
		}
		SqlOperation operation = createOperation(builder.toString(), SqlType.ALTER, originalTable, table);
		if (!CommonUtils.eq(sql, operation.getSqlText())) {
			add(result, operation);
		}
		if (consDiff != null) {
			final List<SqlOperation> commands = getAddForeignKeyConstraintDefinitions(originalTable, table, 
					difference, consDiff);
			result.addAll(commands);
		}
		if (colsDiff != null) {
			operation = getAutoIncrementStart(difference, colsDiff);
			if (operation != null) {
				add(result, operation);
			}
		}
		return result;
	}

	protected void addTableOptions(final Map<String, Difference<?>> allDiff,
			final MySqlSqlBuilder sqlBuilder) {
		addEnginDefinition(allDiff.get("ENGINE"), sqlBuilder);
		addRowFormatDefinition(allDiff.get("ROW_FORMAT"), sqlBuilder);
		addRemarkDefinition(allDiff.get(SchemaProperties.REMARKS.getLabel()), sqlBuilder);
	}

	/**
	 * Engine定義を追加します
	 * 
	 * @param tableProp
	 * @param sqlBuilder
	 */
	protected void addEnginDefinition(final Difference<?> tableProp,
			final MySqlSqlBuilder sqlBuilder) {
		addTableOptionDefinition(tableProp, "ENGINE", sqlBuilder);
	}

	/**
	 * ROW_FORMAT定義を追加します
	 * 
	 * @param tableProp
	 * @param sqlBuilder
	 */
	protected void addRowFormatDefinition(final Difference<?> tableProp,
			final MySqlSqlBuilder sqlBuilder) {
		addTableOptionDefinition(tableProp, "ROW_FORMAT", sqlBuilder);
	}

	protected void addTableOptionDefinition(final Difference<?> tableProp, final String name,
			final MySqlSqlBuilder sqlBuilder) {
		if (tableProp == null) {
			return;
		}
		final String targetProp = tableProp.getTarget(String.class);
		if (!CommonUtils.isEmpty(targetProp)) {
			sqlBuilder.comma(!sqlBuilder.isFirstElement());
			sqlBuilder.property(name, targetProp);
			sqlBuilder.setFirstElement(false);
		}
	}

	/**
	 * COMMENT定義を追加します
	 * 
	 * @param tableProp
	 * @param sqlBuilder
	 */
	protected void addRemarkDefinition(final Difference<?> tableProp,
			final MySqlSqlBuilder builder) {
		if (tableProp == null) {
			return;
		}
		final String targetProp = tableProp.getTarget(String.class);
		if (!CommonUtils.isEmpty(targetProp)) {
			builder.comma(!builder.isFirstElement());
			builder.comment().space();
			builder.sqlChar(targetProp);
			builder.setFirstElement(false);
		}
	}

	/**
	 * カラム定義を追加します
	 * 
	 * @param colDiff
	 * @param sqlBuilder
	 */
	protected void addColumnDefinitions(final DbObjectDifferenceCollection colsDiff
			, final DbObjectDifferenceCollection consDiff
			, final MySqlSqlBuilder sqlBuilder) {
		final ColumnCollection columns = (ColumnCollection) colsDiff.getTarget();
		for (final DbObjectDifference diff : colsDiff.getList(State.Deleted)) {
			final Column column = diff.getOriginal(Column.class);
			final List<ForeignKeyConstraint> fks=getForeignKeys(column);
			if (fks.size()==0){
				sqlBuilder.comma(!sqlBuilder.isFirstElement());
				sqlBuilder.drop().name(column);
				sqlBuilder.setFirstElement(false);
			} else{
				addDropForeignKeys(fks, consDiff, sqlBuilder);
				sqlBuilder.comma(!sqlBuilder.isFirstElement());
				sqlBuilder.drop().name(column);
				sqlBuilder.setFirstElement(false);
			}
		}
		final List<DbObjectDifference> columnDiffList=colsDiff.getList(State.Added, State.Modified);
		sortColumnDiff(columnDiffList);
		for (final DbObjectDifference diff : columnDiffList) {
			if (diff.getState()==State.Added){
				final Column column = diff.getTarget(Column.class);
				sqlBuilder.comma(!sqlBuilder.isFirstElement());
				sqlBuilder.add().name(column);
				sqlBuilder.space().definition(column, this.getOptions().getTableOptions().getWithColumnRemarks().test(column));
				addColumnPosition(column, columns, sqlBuilder);
				sqlBuilder.setFirstElement(false);
			} else{
				final Column oldColumn = diff.getOriginal(Column.class);
				final Column column = diff.getTarget(Column.class);
				sqlBuilder.comma(!sqlBuilder.isFirstElement());
				if (CommonUtils.eq(oldColumn.getName(), column.getName())) {
					sqlBuilder.modify().name(column);
					sqlBuilder.space().definition(column, this.getOptions().getTableOptions().getWithColumnRemarks().test(column));
				} else {
					sqlBuilder.change().name(oldColumn);
					sqlBuilder.name(column);
					sqlBuilder.space().definition(column, this.getOptions().getTableOptions().getWithColumnRemarks().test(column));
				}
				addColumnPosition(column, columns, sqlBuilder);
				sqlBuilder.setFirstElement(false);
			}
		}
	}

	private List<ForeignKeyConstraint> getForeignKeys(final Column column){
		final List<ForeignKeyConstraint> fks=column.getTable().getConstraints().getForeignKeyConstraints(column);
		return fks;
	}

	private void addDropForeignKeys(final List<ForeignKeyConstraint> fks, final DbObjectDifferenceCollection consDiff, final MySqlSqlBuilder sqlBuilder){
		final List<DbObjectDifference> diffs =consDiff.getList(State.Deleted);
		for(final ForeignKeyConstraint fk:fks){
			boolean find=false;
			for (final DbObjectDifference diff : diffs) {
				if (diff.getOriginal()==fk){
					find=true;
					break;
				}
			}
			if (!find){
				sqlBuilder.comma(!sqlBuilder.isFirstElement());
				dropConstraintDefinition(fk, sqlBuilder);
				sqlBuilder.setFirstElement(false);
			}
		}
	}
	
	private void addColumnPosition(final Column column, final ColumnCollection columns,
			final MySqlSqlBuilder sqlBuilder) {
		for (int i = 0; i < columns.size(); i++) {
			if (CommonUtils.eq(column.getName(), columns.get(i).getName())) {
				if (i > 0) {
					sqlBuilder.after().name(columns.get(i - 1));
				} else {
					sqlBuilder.first();
				}
			}
		}
	}

	/**
	 * AUTO_INCREMENTの初期値を設定DDLを取得します
	 * 
	 * @param colDiff
	 * @param sqlBuilder
	 */
	protected SqlOperation getAutoIncrementStart(final DbObjectDifference difference,
			final DbObjectDifferenceCollection colDiff) {
		for (final DbObjectDifference diff : colDiff.getList()) {
			if (diff.getState() == State.Added
					|| diff.getState() == State.Modified) {
				final Column column = diff.getTarget(Column.class);
				if (!column.isIdentity()) {
					return null;
				}
				Long current = column.getIdentityLastValue();
				if (current == null) {
					current = column.getIdentityStartValue();
				}
				if (current != null
						&& !CommonUtils.eq(current, Long.valueOf(1))) {
					final MySqlSqlBuilder sqlBuilder = createSqlBuilder();
					final Table orgTable = difference.getOriginal(Table.class);
					final Table table = difference.getTarget(Table.class);
					addAlterTable(table, sqlBuilder);
					sqlBuilder.space()
							.property("AUTO_INCREMENT", current);
					final SqlOperation operation = createOperation(sqlBuilder.toString(), SqlType.ALTER, orgTable, table);
					return operation;
				}
			}
		}
		return null;
	}

	/**
	 * 制約定義を追加します
	 * 
	 * @param consDiff
	 * @param sqlBuilder
	 */
	protected void addConstraintDefinitions(final Table originalTable, final Table table
			, final List<DbObjectDifference> consDiff, final MySqlSqlBuilder sqlBuilder) {
		for (final DbObjectDifference diff : consDiff) {
			final Constraint originalConstraint = diff.getOriginal(Constraint.class);
			final Constraint constraint = diff.getTarget(Constraint.class);
			if (diff.getState() == State.Deleted) {
				sqlBuilder.comma(!sqlBuilder.isFirstElement());
				dropConstraintDefinition(originalConstraint, sqlBuilder);
				sqlBuilder.setFirstElement(false);
			}
			if (diff.getState() == State.Modified) {
				sqlBuilder.comma(!sqlBuilder.isFirstElement());
				dropConstraintDefinition(originalConstraint, sqlBuilder);
				if (constraint instanceof ForeignKeyConstraint) {
					continue;
				}
				sqlBuilder.comma();
				addConstraintDefinition(originalTable, table, constraint, sqlBuilder);
				sqlBuilder.setFirstElement(false);
			}
			if (diff.getState() == State.Added) {
				if (constraint instanceof ForeignKeyConstraint) {
					continue;
				}
				sqlBuilder.comma(!sqlBuilder.isFirstElement());
				addConstraintDefinition(originalTable, table, constraint, sqlBuilder);
				sqlBuilder.setFirstElement(false);
			}
		}
	}

	/**
	 * ForeignKey制約追加の定義を取得します
	 * 
	 * @param difference
	 * @param consDiff
	 * @param sqlBuilder
	 * @return ForeignKey制約追加の定義
	 */
	protected List<SqlOperation> getAddForeignKeyConstraintDefinitions(final Table originalTable, final Table table
			, final DbObjectDifference difference, final DbObjectDifferenceCollection consDiff) {
		final List<SqlOperation> operationList = CommonUtils.list();
		for (final DbObjectDifference diff : consDiff.getList()) {
			final Constraint constraint = diff.getTarget(Constraint.class);
			final MySqlSqlBuilder sqlBuilder = createSqlBuilder();
			addAlterTable(originalTable, sqlBuilder);
			if (diff.getState() == State.Modified
					|| diff.getState() == State.Added) {
				if (!(constraint instanceof ForeignKeyConstraint)) {
					continue;
				}
				addConstraintDefinition(originalTable, table, constraint, sqlBuilder);
				final SqlOperation operation = this.createOperation(sqlBuilder.toString(), SqlType.ALTER, constraint);
				add(operationList, operation);
			}
		}
		return operationList;
	}
	
	/**
	 * 制約を追加します
	 * 
	 * @param constraint
	 * @param constraint
	 * @param builder
	 * @param constraint
	 */
	@Override
	protected void addConstraintDefinition(final Table table, final Constraint constraint, final AddTableObjectDetailFactory<Constraint, AbstractSqlBuilder<?>> sqlFactory, final MySqlSqlBuilder builder) {
		builder.add();
		sqlFactory.addObjectDetail(constraint, table, builder);
	}

	@Override
	protected void dropConstraintDefinition(final Constraint constraint,
			final MySqlSqlBuilder builder) {
		if (constraint instanceof UniqueConstraint) {
			builder.drop();
			final UniqueConstraint uc = (UniqueConstraint) constraint;
			if (uc.isPrimaryKey()) {
				builder.primaryKey();
			} else {
				builder.index();
				builder.space().name(uc, false);
			}
		} else if (constraint instanceof ForeignKeyConstraint) {
			final ForeignKeyConstraint fc = (ForeignKeyConstraint) constraint;
			builder.drop().foreignKey();
			builder.space().name(fc, false);
		}
	}

	/**
	 * インデックス定義を追加します
	 * 
	 * @param consDiff
	 * @param sqlBuilder
	 */
	protected void addIndexDefinitions(final Table originalTable, final Table table
			, final DbObjectDifferenceCollection indexDiff
			, final DbObjectDifferenceCollection consDiff
			, final MySqlSqlBuilder builder) {
		Map<String, DbObjectDifference> consMap;
		if (consDiff!=null){
			consMap=consDiff.toMap(obj->obj.getOriginal() instanceof UniqueConstraint||obj.getTarget() instanceof UniqueConstraint);
		}else{
			consMap=Collections.emptyMap();
		}
		for (final DbObjectDifference diff : indexDiff.getList(State.Deleted)) {
			final Index originalIndex = diff.getOriginal(Index.class);
			final DbObjectDifference conDiff=consMap.get(originalIndex.getName());
			if (conDiff!=null&&conDiff.getState().isDeleted()){
				return;
			}
			builder.comma(!builder.isFirstElement());
			addDropIndexDefinition(originalIndex, builder);
			builder.setFirstElement(false);
		}
		for (final DbObjectDifference diff : indexDiff.getList(State.Modified)) {
			final Index originalIndex = diff.getOriginal(Index.class);
			final Index index = diff.getTarget(Index.class);
			builder.comma(!builder.isFirstElement());
			addDropIndexDefinition(originalIndex, builder);
			builder.comma();
			addIndexDefinition(index, builder);
			builder.setFirstElement(false);
		}
		for (final DbObjectDifference diff : indexDiff.getList(State.Added)) {
			final Index index = diff.getTarget(Index.class);
			builder.comma(!builder.isFirstElement());
			addIndexDefinition(index, builder);
			builder.setFirstElement(false);
		}
	}

	protected void addIndexDefinition(final Index index, final MySqlSqlBuilder builder) {
		if (index == null) {
			return;
		}
		final AddTableObjectDetailFactory<Index, MySqlSqlBuilder> indexOperation =this.getAddTableObjectDetailOperationFactory(index);
		if (indexOperation!=null) {
			builder.add();
			indexOperation.addObjectDetail(index, null, builder);
		}
	}

	@Override
	protected void addDropIndexDefinition(final Index obj, final MySqlSqlBuilder builder) {
		builder.drop().index();
		builder.name(obj, false);
	}
	
	/**
	 * Partition定義を追加します
	 * 
	 * @param partitionInfoProp
	 * @param sqlBuilder
	 */
	protected void addPartitionDefinition(final Table originalTable, final Table table
			,final DbObjectDifference partitioningProp,
			final MySqlSqlBuilder builder) {
		final Partitioning originalPartitioning = partitioningProp.getOriginal(Partitioning.class);
		final Partitioning partitioning = partitioningProp.getTarget(Partitioning.class);
		final AddObjectDetail<Partitioning, AbstractSqlBuilder<?>> addObjectDetail=getAddObjectDetail(new Partitioning(), SqlType.CREATE);
		if (addObjectDetail==null){
			return;
		}
		if (originalPartitioning == null) {
			builder.comma(!builder.isFirstElement());
			if (partitioning != null) {
				addObjectDetail.addObjectDetail(partitioning, builder);
			}
		} else if (partitioning == null) {
			builder.comma(!builder.isFirstElement());
			builder.remove().partitioning();
		} else {
			builder.comma(!builder.isFirstElement());
			modifyPartitionByDefinition(partitioningProp,
					originalPartitioning, partitioning, builder);
		}
		builder.setFirstElement(false);
	}

	/**
	 * Partition定義を変更します
	 * 
	 * @param partitioningProp
	 * @param originalPartitioning
	 * @param partitioning
	 * @param builder
	 */
	protected void modifyPartitionByDefinition(
			final DbObjectDifference partitioningProp,
			final Partitioning originalPartitioning, final Partitioning partitioning,
			final MySqlSqlBuilder builder) {
		final Map<String, Difference<?>> allDiff = partitioningProp.getChangedProperties(this.getDialect());
		final DbObjectDifferenceCollection partitionsDifference = (DbObjectDifferenceCollection) allDiff
				.get(SchemaObjectProperties.PARTITIONS.getLabel());
		final DbObjectPropertyDifference partitionSizeDifference = (DbObjectPropertyDifference) allDiff
				.get(SchemaProperties.PARTITION_SIZE.getLabel());
		final DbObjectPropertyDifference subPartitionSizeDifference = (DbObjectPropertyDifference) allDiff
				.get(SchemaProperties.SUB_PARTITION_SIZE.getLabel());
		final AddObjectDetail<Partitioning, AbstractSqlBuilder<?>> addObjectDetail=getAddObjectDetail(partitioning, SqlType.CREATE);
		if (originalPartitioning.getPartitioningType() == partitioning.getPartitioningType()) {
			if (originalPartitioning.getSubPartitioningType() == partitioning
					.getSubPartitioningType()) {
				// パーティション種類未変更
				if (partitioning.getPartitioningType().isSizePartitioning()
						&& partitionsDifference == null) {
					final Integer size = partitionSizeDifference.getTarget(Integer.class);
					// Hashパーティションサイズ変更
					builder.coalesce().partition().space()._add(size);
				} else {
					modifyPartitions(originalPartitioning, partitioning,
							partitionsDifference, builder);
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

	protected void modifyPartitions(final Partitioning originalPartitioning,
			final Partitioning partitioning,
			final DbObjectDifferenceCollection partitionsDifference,
			final MySqlSqlBuilder builder) {
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

	protected void addPartitionsDefinition(final Partitioning partitioning,
			final List<DbObjectDifference> child, final MySqlSqlBuilder builder) {
		final MySqlCreatePartitioningFactory factory=this.getSqlFactoryRegistry().getSqlFactory(partitioning, SqlType.CREATE);
		if (factory==null){
			return;
		}
		builder.add().partition();
		builder.lineBreak()._add("(");
		builder.appendIndent(1);
		int i=0;
		for (final DbObjectDifference partitionDifference : child) {
			final Partition partition = partitionDifference.getTarget(Partition.class);
			builder.lineBreak().comma(i>0);
			factory.appendPartitionDefinition(false, partitioning, partition, builder);
			i++;
		}
		builder.appendIndent(-1);
		builder.lineBreak()._add(")");
	}

	protected void deletePartitionsDefinition(final List<DbObjectDifference> child,
			final MySqlSqlBuilder builder) {
		builder.comma(!builder.isFirstElement()).drop().partition();
		builder.setFirstElement(false);
		for (int i = 0; i < child.size(); i++) {
			final DbObjectDifference partitionDifference = child.get(i);
			final Partition partition = partitionDifference.getOriginal(Partition.class);
			builder.space(i == 0).comma(i != 0);
			builder.name(partition);
		}
	}

	/**
	 * CHARACTER SET定義もしくはCOLLATE定義を追加します
	 * 
	 * @param charSetProp
	 * @param collationProp
	 * @param builder
	 */
	protected void addCharSetDefinition(final Difference<?> charSetProp,
			final Difference<?> collationProp, final MySqlSqlBuilder builder) {
		String charsetTarget = null;
		String collationTarget = null;
		if (collationProp!=null){
			collationTarget = collationProp.getTarget(String.class);
		}
		if (charSetProp == null) {
			if (collationTarget!=null){
				charsetTarget = CommonUtils.first(collationTarget.split("_"));
			}
		} else {
			charsetTarget = charSetProp.getTarget(String.class);
		}
		if (charsetTarget!=null){
			builder.comma(!builder.isFirstElement());
			builder.convert().to().characterSet().space()._add(charsetTarget);
		}
		if (collationTarget != null) {
			builder.setFirstElement(false);
			builder.collate().space()._add(collationTarget);
		}
	}
}
