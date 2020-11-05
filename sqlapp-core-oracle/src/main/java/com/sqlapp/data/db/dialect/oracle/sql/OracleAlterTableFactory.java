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
	protected void addOtherDefinitions(Map<String, Difference<?>> allDiff
			, Table originalTable, Table table, List<SqlOperation> result){
		Map<String, Difference<?>> diff=this.getAll(allDiff, OracleUtils.getTableStatisticsKeys());
		if (diff.isEmpty()){
			return;
		}
		OracleSqlBuilder builder = createSqlBuilder();
		builder.alter().table();
		builder.name(table);
		builder.appendIndent(1);
		boolean hasChange=false;
		boolean hasStorage=false;
		for(Map.Entry<String, Difference<?>> entry:diff.entrySet()){
			if (!entry.getValue().getState().isChanged()){
				continue;
			}
			Difference<?> diffValue=entry.getValue();
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
			for(Map.Entry<String, Difference<?>> entry:diff.entrySet()){
				if (!entry.getValue().getState().isChanged()){
					continue;
				}
				if (!builder.isStoragePropertyName(entry.getKey())){
					continue;
				}
				Difference<?> diffValue=entry.getValue();
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
	
	protected void addCommentDefinitions(Map<String, Difference<?>> allDiff
			, Table originalTable, Table table, List<SqlOperation> result){
		Difference<?> tableProp = allDiff.get(SchemaProperties.REMARKS.getLabel());
		if (tableProp!=null&&tableProp.getState().isChanged()){
			OracleSqlBuilder builder=this.createSqlBuilder();
			builder.comment().on().table().space().name(table, this.getOptions().isDecorateSchemaName()).is()
				.$if(table.getRemarks()!=null, ()->builder.sqlChar(table.getRemarks()), ()->builder.is()._null());
			addSql(result, builder, SqlType.SET_COMMENT, table);
		}
		DbObjectDifferenceCollection colsDiff = (DbObjectDifferenceCollection) allDiff
				.get(SchemaObjectProperties.COLUMNS.getLabel());
		if (colsDiff != null) {
			List<DbObjectPropertyDifference> diffs=colsDiff.findModifiedProperties(this.getDialect(), SchemaProperties.REMARKS.getLabel(), Column.class);
			for (DbObjectPropertyDifference diff : diffs) {
				Column obj=diff.getTarget(Column.class);
				OracleSqlBuilder builder=this.createSqlBuilder();
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
	protected void addColumnDefinitions(Map<String, Difference<?>> allDiff
			, Table originalTable, Table table,
			DbObjectDifferenceCollection colsDiff, List<SqlOperation> result) {
		addDeleteColumn(originalTable, table, colsDiff.getList(State.Deleted), result);
		addAddColumn(originalTable, table, colsDiff.getList(State.Added), result);
		addRenameOrAlterColumn(originalTable, table, colsDiff.getList(State.Modified), result);
	}
	
	protected void addDeleteColumn(Table originalTable, Table table, List<DbObjectDifference> diffs,List<SqlOperation> result){
		if (CommonUtils.isEmpty(diffs)){
			return;
		}
		OracleSqlBuilder builder = createSqlBuilder();
		builder.alter().table();
		builder.name(table, this.getOptions().isDecorateSchemaName());
		builder.drop().space()._add("( ");
		builder.appendIndent(1);
		int i=0;
		List<Column> columns=CommonUtils.list();
		for (DbObjectDifference diff : diffs) {
			Column column = diff.getOriginal(Column.class);
			builder._add(", ", i>0);
			builder.name(column.getName());
			columns.add(column);
			i++;
		}
		builder.appendIndent(-1);
		builder._add(" )");
		add(result, createOperation(builder.toString(), SqlType.ALTER, columns));
	}
	
	protected void addAddColumn(Table originalTable, Table table, List<DbObjectDifference> diffs,List<SqlOperation> result){
		if (CommonUtils.isEmpty(diffs)){
			return;
		}
		List<Column> objects=CommonUtils.list();
		OracleSqlBuilder builder = createSqlBuilder();
		builder.alter().table();
		builder.name(table, this.getOptions().isDecorateSchemaName());
		builder.add().space()._add("(");
		builder.appendIndent(1);
		int i=0;
		for (DbObjectDifference diff : diffs) {
			Column column = diff.getTarget(Column.class);
			objects.add(column);
			builder.lineBreak(i==0)._add("  ", i==0);
			builder.lineBreak(i>0)._add(", ", i>0);
			builder.name(column);
			builder.space().definition(column);
			i++;
		}
		builder.appendIndent(-1);
		builder.lineBreak()._add(")");
		add(result, createOperation(builder.toString(), SqlType.ALTER, objects));
	}
	
	protected void addRenameOrAlterColumn(Table originalTable, Table table, List<DbObjectDifference> diffs,List<SqlOperation> result){
		if (CommonUtils.isEmpty(diffs)){
			return;
		}
		List<DbObjectDifference> renameList=CommonUtils.list();
		List<DbObjectDifference> modifyList=CommonUtils.list();
		List<DbObjectDifference> dropAddList=CommonUtils.list();
		for (DbObjectDifference diff : diffs) {
			Column oldColumn = diff.getOriginal(Column.class);
			Column column = diff.getTarget(Column.class);
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

	protected void addRenameColumn(Table originalTable, Table table, List<DbObjectDifference> diffs,List<SqlOperation> result){
		if (CommonUtils.isEmpty(diffs)){
			return;
		}
		for (DbObjectDifference diff : diffs) {
			addRenameColumn(originalTable, table, diff, result);
		}
	}

	protected void addRenameColumn(Table originalTable, Table table, DbObjectDifference diff,List<SqlOperation> result){
		OracleSqlBuilder builder = createSqlBuilder();
		builder.alter().table();
		builder.name(table, this.getOptions().isDecorateSchemaName());
		builder.rename().column();
		Column orgColumn = diff.getOriginal(Column.class);
		builder.name(orgColumn);
		Column column = diff.getTarget(Column.class);
		builder.to();
		builder.name(column);
		add(result, createOperation(builder.toString(), SqlType.ALTER, orgColumn, column));
	}
	
	protected void addModifyColumn(Table originalTable, Table table, List<DbObjectDifference> diffs,List<SqlOperation> result){
		if (CommonUtils.isEmpty(diffs)){
			return;
		}
		OracleSqlBuilder builder = createSqlBuilder();
		builder.alter().table();
		builder.name(table, this.getOptions().isDecorateSchemaName());
		builder.modify().space()._add("(");
		builder.appendIndent(1);
		int i=0;
		List<Column> originals=CommonUtils.list();
		List<Column> targets=CommonUtils.list();
		for (DbObjectDifference diff : diffs) {
			Column orgColumn = diff.getOriginal(Column.class);
			originals.add(orgColumn);
			Column column = diff.getTarget(Column.class);
			targets.add(column);
			builder.lineBreak(i==0)._add("  ", i==0);
			builder.lineBreak(i>0)._add(", ", i>0);
			builder.name(column);
			builder.space().definition(column);
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
	protected void addPartitionDefinition(Map<String, Difference<?>> allDiff
			, Table originalTable, Table table
			,DbObjectDifference partitioningProp
			,List<SqlOperation> result) {
		Partitioning originalPartitioning = partitioningProp.getOriginal(Partitioning.class);
		Partitioning partitioning = partitioningProp.getTarget(Partitioning.class);
		AddObjectDetail<Partitioning, AbstractSqlBuilder<?>> addObjectDetail=getAddObjectDetail(new Partitioning(), SqlType.CREATE);
		if (addObjectDetail==null){
			return;
		}
		OracleSqlBuilder builder = createSqlBuilder();
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
			DbObjectDifference partitioningProp,
			Partitioning originalPartitioning, Partitioning partitioning,
			OracleSqlBuilder builder) {
		Map<String, Difference<?>> allDiff = partitioningProp.getProperties(
				State.Modified, State.Added, State.Deleted);
		DbObjectDifferenceCollection partitionsDifference = (DbObjectDifferenceCollection) allDiff
				.get(SchemaObjectProperties.PARTITIONS.getLabel());
		DbObjectPropertyDifference partitionSizeDifference = (DbObjectPropertyDifference) allDiff
				.get(SchemaProperties.PARTITION_SIZE.getLabel());
		DbObjectPropertyDifference subpartitionSizeDifference = (DbObjectPropertyDifference) allDiff
				.get(SchemaProperties.SUB_PARTITION_SIZE.getLabel());
		AddObjectDetail<Partitioning, AbstractSqlBuilder<?>> addObjectDetail=getAddObjectDetail(partitioning, SqlType.CREATE);
		if (originalPartitioning.getPartitioningType() == partitioning.getPartitioningType()) {
			if (originalPartitioning.getSubPartitioningType() == partitioning
					.getSubPartitioningType()) {
				// パーティション種類未変更
				if (partitioning.getPartitioningType().isSizePartitioning()
						&& partitionsDifference == null) {
					Integer size = partitionSizeDifference.getTarget(Integer.class);
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

	protected void modifyPartitions(Partitioning originalPartitioning,
			Partitioning partitioning,
			DbObjectDifferenceCollection partitionsDifference,
			OracleSqlBuilder builder) {
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

	protected void addPartitionsDefinition(Partitioning partitioning,
			List<DbObjectDifference> child, OracleSqlBuilder builder) {
		OracleCreatePartitioningFactory factory=this.getSqlFactoryRegistry().getSqlFactory(partitioning, SqlType.CREATE);
		if (factory==null){
			return;
		}
		builder.add().partition();
		builder.lineBreak()._add("(");
		builder.appendIndent(1);
		int i=0;
		for (DbObjectDifference partitionDifference : child) {
			Partition partition = partitionDifference.getTarget(Partition.class);
			builder.lineBreak().comma(i>0);
			factory.appendPartitionDefinition(false, partitioning, partition, builder);
			i++;
		}
		builder.appendIndent(-1);
		builder.lineBreak()._add(")");
	}

	protected void deletePartitionsDefinition(List<DbObjectDifference> child,
			OracleSqlBuilder builder) {
		builder.comma(!builder.isFirstElement()).drop().partition();
		builder.setFirstElement(false);
		for (int i = 0; i < child.size(); i++) {
			DbObjectDifference partitionDifference = child.get(i);
			Partition partition = partitionDifference.getOriginal(Partition.class);
			builder.space(i == 0).comma(i != 0);
			builder.name(partition);
		}
	}
}
