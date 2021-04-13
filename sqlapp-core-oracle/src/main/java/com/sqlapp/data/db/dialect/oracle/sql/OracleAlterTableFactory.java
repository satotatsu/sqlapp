/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-oracle.
 *
 * sqlapp-core-oracle is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-oracle is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with sqlapp-core-oracle.  If not, see <http://www.gnu.org/licenses/>.
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
	protected void addOtherDefinitions(final Map<String, Difference<?>> allDiff
			, final Table originalTable, final Table table, final List<SqlOperation> result){
		final Map<String, Difference<?>> diff=this.getAll(allDiff, OracleUtils.getTableStatisticsKeys());
		if (diff.isEmpty()){
			return;
		}
		final OracleSqlBuilder builder = createSqlBuilder();
		builder.alter().table();
		builder.name(table);
		builder.appendIndent(1);
		boolean hasChange=false;
		boolean hasStorage=false;
		for(final Map.Entry<String, Difference<?>> entry:diff.entrySet()){
			if (!entry.getValue().getState().isChanged()){
				continue;
			}
			final Difference<?> diffValue=entry.getValue();
			if (diffValue.getTarget()==null){
				continue;
			}
			if (builder.isStoragePropertyName(entry.getKey())){
				continue;
			} else{
				hasStorage=true;
			}
			builder.lineBreak().oracleProperty(entry.getKey(), diffValue.getTarget(String.class));
			hasChange=true;
		}
		if (hasStorage){
			builder.lineBreak().storage();
			builder.lineBreak()._add("(");
			builder.appendIndent(1);
			for(final Map.Entry<String, Difference<?>> entry:diff.entrySet()){
				if (!entry.getValue().getState().isChanged()){
					continue;
				}
				if (!builder.isStoragePropertyName(entry.getKey())){
					continue;
				}
				final Difference<?> diffValue=entry.getValue();
				if (diffValue.getTarget()==null){
					continue;
				}
				builder.lineBreak().oracleProperty(entry.getKey(), diffValue.getTarget(String.class));
			}
			builder.appendIndent(-1);
			builder.lineBreak()._add(")");
			hasChange=true;
		}
		builder.appendIndent(-1);
		if (hasChange){
			add(result, createOperation(builder.toString(),SqlType.ALTER, originalTable, table));
		}
		addCommentDefinitions(allDiff
				, originalTable, table, result);
	}
	
	protected void addCommentDefinitions(final Map<String, Difference<?>> allDiff
			, final Table originalTable, final Table table, final List<SqlOperation> result){
		final Difference<?> tableProp = allDiff.get(SchemaProperties.REMARKS.getLabel());
		if (tableProp!=null&&tableProp.getState().isChanged()){
			final OracleSqlBuilder builder=this.createSqlBuilder();
			builder.comment().on().table().space().name(table, this.getOptions().isDecorateSchemaName()).is()
				.$if(table.getRemarks()!=null, ()->builder.sqlChar(table.getRemarks()), ()->builder.is()._null());
			addSql(result, builder, SqlType.SET_COMMENT, table);
		}
		final DbObjectDifferenceCollection colsDiff = (DbObjectDifferenceCollection) allDiff
				.get(SchemaObjectProperties.COLUMNS.getLabel());
		if (colsDiff != null) {
			final List<DbObjectPropertyDifference> diffs=colsDiff.findModifiedProperties(this.getDialect(), SchemaProperties.REMARKS.getLabel(), Column.class);
			for (final DbObjectPropertyDifference diff : diffs) {
				final Column obj=diff.getTarget(Column.class);
				final OracleSqlBuilder builder=this.createSqlBuilder();
				builder.comment().on().column().space().columnName(obj, true, this.getOptions().isDecorateSchemaName()).is()
					.$if(obj.getRemarks()!=null, ()->builder.sqlChar(obj.getRemarks()), ()->builder.is()._null());
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
	protected void addColumnDefinitions(final Map<String, Difference<?>> allDiff
			, final Table originalTable, final Table table,
			final DbObjectDifferenceCollection colsDiff, final List<SqlOperation> result) {
		addDeleteColumn(originalTable, table, colsDiff.getList(State.Deleted), result);
		addAddColumn(originalTable, table, colsDiff.getList(State.Added), result);
		addRenameOrAlterColumn(originalTable, table, colsDiff.getList(State.Modified), result);
	}
	
	protected void addDeleteColumn(final Table originalTable, final Table table, final List<DbObjectDifference> diffs,final List<SqlOperation> result){
		if (CommonUtils.isEmpty(diffs)){
			return;
		}
		final OracleSqlBuilder builder = createSqlBuilder();
		builder.alter().table();
		builder.name(table, this.getOptions().isDecorateSchemaName());
		builder.drop().space()._add("( ");
		builder.appendIndent(1);
		int i=0;
		final List<Column> columns=CommonUtils.list();
		for (final DbObjectDifference diff : diffs) {
			final Column column = diff.getOriginal(Column.class);
			builder._add(", ", i>0);
			builder.name(column.getName());
			columns.add(column);
			i++;
		}
		builder.appendIndent(-1);
		builder._add(" )");
		add(result, createOperation(builder.toString(), SqlType.ALTER, columns));
	}
	
	protected void addAddColumn(final Table originalTable, final Table table, final List<DbObjectDifference> diffs,final List<SqlOperation> result){
		if (CommonUtils.isEmpty(diffs)){
			return;
		}
		final List<Column> objects=CommonUtils.list();
		final OracleSqlBuilder builder = createSqlBuilder();
		builder.alter().table();
		builder.name(table, this.getOptions().isDecorateSchemaName());
		builder.add().space()._add("(");
		builder.appendIndent(1);
		int i=0;
		for (final DbObjectDifference diff : diffs) {
			final Column column = diff.getTarget(Column.class);
			objects.add(column);
			builder.lineBreak(i==0)._add("  ", i==0);
			builder.lineBreak(i>0)._add(", ", i>0);
			builder.name(column);
			builder.space().definition(column, this.getOptions().getTableOptions().getWithColumnRemarks().test(column));
			i++;
		}
		builder.appendIndent(-1);
		builder.lineBreak()._add(")");
		add(result, createOperation(builder.toString(), SqlType.ALTER, objects));
	}
	
	protected void addRenameOrAlterColumn(final Table originalTable, final Table table, final List<DbObjectDifference> diffs,final List<SqlOperation> result){
		if (CommonUtils.isEmpty(diffs)){
			return;
		}
		final List<DbObjectDifference> renameList=CommonUtils.list();
		final List<DbObjectDifference> modifyList=CommonUtils.list();
		final List<DbObjectDifference> dropAddList=CommonUtils.list();
		for (final DbObjectDifference diff : diffs) {
			final Column oldColumn = diff.getOriginal(Column.class);
			final Column column = diff.getTarget(Column.class);
			if (!CommonUtils.eq(oldColumn.getName(), column.getName())) {
				if (diff.getProperties().size()==1){
					renameList.add(diff);
				} else{
					dropAddList.add(diff);
				}
			} else{
				modifyList.add(diff);
			}
		}
		addRenameColumn(originalTable, table, renameList, result);
		addModifyColumn(originalTable, table, modifyList, result);
		addDeleteColumn(originalTable, table, dropAddList, result);
		addAddColumn(originalTable, table, dropAddList, result);
	}

	protected void addRenameColumn(final Table originalTable, final Table table, final List<DbObjectDifference> diffs,final List<SqlOperation> result){
		if (CommonUtils.isEmpty(diffs)){
			return;
		}
		for (final DbObjectDifference diff : diffs) {
			addRenameColumn(originalTable, table, diff, result);
		}
	}

	protected void addRenameColumn(final Table originalTable, final Table table, final DbObjectDifference diff,final List<SqlOperation> result){
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
	
	protected void addModifyColumn(final Table originalTable, final Table table, final List<DbObjectDifference> diffs,final List<SqlOperation> result){
		if (CommonUtils.isEmpty(diffs)){
			return;
		}
		final OracleSqlBuilder builder = createSqlBuilder();
		builder.alter().table();
		builder.name(table, this.getOptions().isDecorateSchemaName());
		builder.modify().space()._add("(");
		builder.appendIndent(1);
		int i=0;
		final List<Column> originals=CommonUtils.list();
		final List<Column> targets=CommonUtils.list();
		for (final DbObjectDifference diff : diffs) {
			final Column orgColumn = diff.getOriginal(Column.class);
			originals.add(orgColumn);
			final Column column = diff.getTarget(Column.class);
			targets.add(column);
			builder.lineBreak(i==0)._add("  ", i==0);
			builder.lineBreak(i>0)._add(", ", i>0);
			builder.name(column);
			builder.space().definition(column, this.getOptions().getTableOptions().getWithColumnRemarks().test(column));
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
	protected void addPartitionDefinition(final Map<String, Difference<?>> allDiff
			, final Table originalTable, final Table table
			,final DbObjectDifference partitioningProp
			,final List<SqlOperation> result) {
		final Partitioning originalPartitioning = partitioningProp.getOriginal(Partitioning.class);
		final Partitioning partitioning = partitioningProp.getTarget(Partitioning.class);
		final AddObjectDetail<Partitioning, AbstractSqlBuilder<?>> addObjectDetail=getAddObjectDetail(new Partitioning(), SqlType.CREATE);
		if (addObjectDetail==null){
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
			modifyPartitionByDefinition(partitioningProp,
					originalPartitioning, partitioning, builder);
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
	protected void modifyPartitionByDefinition(
			final DbObjectDifference partitioningProp,
			final Partitioning originalPartitioning, final Partitioning partitioning,
			final OracleSqlBuilder builder) {
		final Map<String, Difference<?>> allDiff = partitioningProp.getProperties(
				State.Modified, State.Added, State.Deleted);
		final DbObjectDifferenceCollection partitionsDifference = (DbObjectDifferenceCollection) allDiff
				.get(SchemaObjectProperties.PARTITIONS.getLabel());
		final DbObjectPropertyDifference partitionSizeDifference = (DbObjectPropertyDifference) allDiff
				.get(SchemaProperties.PARTITION_SIZE.getLabel());
		final DbObjectPropertyDifference subpartitionSizeDifference = (DbObjectPropertyDifference) allDiff
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
			final OracleSqlBuilder builder) {
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
			final List<DbObjectDifference> child, final OracleSqlBuilder builder) {
		final OracleCreatePartitioningFactory factory=this.getSqlFactoryRegistry().getSqlFactory(partitioning, SqlType.CREATE);
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
			final OracleSqlBuilder builder) {
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
