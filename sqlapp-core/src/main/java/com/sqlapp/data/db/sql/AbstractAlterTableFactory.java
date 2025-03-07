/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core.
 *
 * sqlapp-core is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.sql;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Constraint;
import com.sqlapp.data.schemas.DbObjectDifference;
import com.sqlapp.data.schemas.DbObjectDifferenceCollection;
import com.sqlapp.data.schemas.Difference;
import com.sqlapp.data.schemas.Index;
import com.sqlapp.data.schemas.SchemaObjectProperties;
import com.sqlapp.data.schemas.SchemaProperties;
import com.sqlapp.data.schemas.State;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.util.AbstractSqlBuilder;
import com.sqlapp.util.CommonUtils;

public abstract class AbstractAlterTableFactory<S extends AbstractSqlBuilder<?>>
		extends AbstractTableFactory<S> {

	@Override
	public List<SqlOperation> createDiffSql(final DbObjectDifference difference) {
		final List<SqlOperation> result = CommonUtils.list();
		final Map<String, Difference<?>> allDiff = difference.toDifference()
				.getChangedProperties(this.getDialect());
		final Table originalTable = difference.getOriginal(Table.class);
		final Table table = difference.getTarget(Table.class);
		Difference<?> tableProp = allDiff.get(SchemaProperties.NAME.getLabel());
		if (tableProp != null) {
			final S builder = createSqlBuilder();
			builder.alter().table().name(originalTable, this.getOptions().isDecorateSchemaName());
			this.addTableComment(table, builder);
			builder.rename().to();
			builder.name(table, this.getOptions().isDecorateSchemaName());
			add(result, createOperation(builder.toString(), SqlType.ALTER, originalTable, table));
		}
		final DbObjectDifferenceCollection consDiff = (DbObjectDifferenceCollection) allDiff
				.get(SchemaObjectProperties.CONSTRAINTS.getLabel());
		if (consDiff != null) {
			addConstraintDefinitions(allDiff, originalTable, table, consDiff.getList(State.Deleted), result);
		}
		final DbObjectDifferenceCollection indexesDiff = (DbObjectDifferenceCollection) allDiff
				.get(SchemaObjectProperties.INDEXES.getLabel());
		if (indexesDiff != null) {
			addIndexDefinitions(allDiff, originalTable, table, indexesDiff.getList(State.Deleted), result);
		}
		final DbObjectDifferenceCollection colsDiff = (DbObjectDifferenceCollection) allDiff
				.get(SchemaObjectProperties.COLUMNS.getLabel());
		if (colsDiff != null) {
			addColumnDefinitions(allDiff, originalTable, table, colsDiff, result);
		}
		if (consDiff != null) {
			addConstraintDefinitions(allDiff, originalTable, table, consDiff.getList(State.Added, State.Modified), result);
		}
		if (indexesDiff != null) {
			addIndexDefinitions(allDiff, originalTable, table, indexesDiff.getList(State.Added, State.Modified), result);
		}
		tableProp = allDiff.get(SchemaObjectProperties.PARTITIONING.getLabel());
		if (tableProp != null) {
			addPartitionDefinition(allDiff, originalTable, table, (DbObjectDifference) tableProp, result);
		}
		addOtherDefinitions(allDiff, originalTable, table, result);
		return result;
	}
	
	protected void addOtherDefinitions(final Map<String, Difference<?>> allDiff
			, final Table originalTable, final Table table, final List<SqlOperation> result){
		
	}

	/**
	 * カラム定義を追加します
	 * 
	 * @param originalTable
	 * @param table
	 * @param colDiff
	 * @param sqlBuilder
	 */
	protected void addColumnDefinitions(final Map<String, Difference<?>> allDiff
			, final Table originalTable, final Table table,
			final DbObjectDifferenceCollection colsDiff, final List<SqlOperation> result) {
		for (final DbObjectDifference diff : colsDiff.getList(State.Deleted)) {
			addDeleteColumn(originalTable, table, diff, result);
		}
		final List<DbObjectDifference> columnDiffList=colsDiff.getList(State.Added, State.Modified);
		sortColumnDiff(columnDiffList);
		for (final DbObjectDifference diff : columnDiffList) {
			if (diff.getState()==State.Added){
				addAddColumn(originalTable, table, diff, result);
			} else{
				addRenameOrAlterColumn(originalTable, table, diff, result);
			}
		}
	}

	protected void sortColumnDiff(final List<DbObjectDifference> columnDiffList){
		Collections.sort(columnDiffList, new Comparator<DbObjectDifference>(){

			@Override
			public int compare(final DbObjectDifference o1, final DbObjectDifference o2) {
				final Column column1 = o1.getTarget(Column.class);
				final Column column2 = o2.getTarget(Column.class);
				return column1.getOrdinal()-column2.getOrdinal();
			}
		});
	}
	
	protected void addDeleteColumn(final Table originalTable, final Table table, final DbObjectDifference diff,final List<SqlOperation> result){
		final S builder = createSqlBuilder();
		final Column column = diff.getOriginal(Column.class);
		builder.alter().table();
		builder.name(table, this.getOptions().isDecorateSchemaName());
		builder.drop().column();
		builder.name(column);
		add(result, createOperation(builder.toString(), SqlType.ALTER, column));
	}

	protected void addAddColumn(final Table originalTable, final Table table, final DbObjectDifference diff,final List<SqlOperation> result){
		final Column column = diff.getTarget(Column.class);
		final S builder = createSqlBuilder();
		builder.alter().table();
		builder.name(table, this.getOptions().isDecorateSchemaName());
		builder.add().column();
		builder.name(column);
		builder.space().definition(column, this.getOptions().getTableOptions().getWithColumnRemarks().test(column));
		add(result, createOperation(builder.toString(), SqlType.ALTER, null, column));
	}

	protected void addRenameOrAlterColumn(final Table originalTable, final Table table, final DbObjectDifference diff,final List<SqlOperation> result){
		final Column oldColumn = diff.getOriginal(Column.class);
		final Column column = diff.getTarget(Column.class);
		final Map<String, Difference<?>> colDiff = diff.toDifference()
				.getProperties(this.getDialect(), State.Added, State.Modified);
		final Difference<?> nameDiff = colDiff.get(SchemaProperties.NAME.getLabel());
		if (colDiff.size() > 1
				|| (nameDiff == null && colDiff.size() == 1)) {
			addAlterColumn(originalTable, table,oldColumn, column, diff, result);
		}
		if (nameDiff != null) {
			addRenameColumn(originalTable, table,oldColumn, column, diff, result);
		}
	}

	protected void addAlterColumn(final Table originalTable, final Table table, final Column oldColumn, final Column column, final DbObjectDifference diff,final List<SqlOperation> result){
		final S builder = createSqlBuilder();
		builder.alter().table();
		builder.name(table, this.getOptions().isDecorateSchemaName());
		builder.alterColumn();
		builder.name(column);
		builder.space().definitionForAlterColumn(column);
		add(result, createOperation(builder.toString(), SqlType.ALTER, oldColumn, column));
	}

	protected void addRenameColumn(final Table originalTable, final Table table, final Column oldColumn, final Column column, final DbObjectDifference diff,final List<SqlOperation> result){
		final S builder = createSqlBuilder();
		builder.alter().table();
		builder.name(table, this.getOptions().isDecorateSchemaName());
		builder.rename().column();
		builder.name(oldColumn);
		builder.space().to().space();
		builder.name(column);
		add(result, createOperation(builder.toString(), SqlType.ALTER, oldColumn, column));
	}

	/**
	 * インデックス定義を追加します
	 * 
	 * @param originalTable
	 * @param table
	 * @param indexesDiff
	 * @param result
	 */
	protected void addIndexDefinitions(final Map<String, Difference<?>> allDiff
			, final Table originalTable, final Table table,
			final List<DbObjectDifference> indexesDiff, final List<SqlOperation> result) {
		for (final DbObjectDifference diff : indexesDiff) {
			final Index index = diff.getTarget(Index.class);
			final Index originalIndex = diff.getOriginal(Index.class);
			addIndexDefinition(originalTable, table, originalIndex, index, diff, result);
		}
	}

	/**
	 * インデックス定義を追加します
	 * 
	 * @param originalTable
	 * @param table
	 * @param originalIndex
	 * @param index
	 * @param diff
	 * @param result
	 */
	protected void addIndexDefinition(final Table originalTable, final Table table, final Index originalIndex, final Index index,
			final DbObjectDifference diff, final List<SqlOperation> result) {
		if (diff.getState() == State.Added) {
			addCreateIndexDefinition(originalTable, table, originalIndex, index, diff, result);
		}else if (diff.getState() == State.Modified) {
			addDropIndexDefinition(originalTable, table, originalIndex, index, diff, result);
			//
			addCreateIndexDefinition(originalTable, table, originalIndex, index, diff, result);
		}else if (diff.getState() == State.Deleted) {
			addDropIndexDefinition(originalTable, table, originalIndex, index, diff, result);
		}
	}

	protected void addCreateIndexDefinition(final Table originalTable, final Table table, final Index originalIndex, final Index index,
			final DbObjectDifference diff, final List<SqlOperation> result) {
		final S builder = createSqlBuilder();
		addCreateIndexDefinition(index, builder);
		add(result, createOperation(builder.toString(), SqlType.CREATE, index));
	}

	protected void addDropIndexDefinition(final Table originalTable, final Table table, final Index originalIndex, final Index index,
			final DbObjectDifference diff, final List<SqlOperation> result) {
		final S builder = createSqlBuilder();
		addDropIndexDefinition(originalIndex, builder);
		add(result, createOperation(builder.toString(), SqlType.DROP, originalIndex));
	}
	
	/**
	 * 制約定義を追加します
	 * 
	 * @param originalTable
	 * @param table
	 * @param consDiff
	 * @param result
	 */
	protected void addConstraintDefinitions(final Map<String, Difference<?>> allDiff
			, final Table originalTable, final Table table,
			final List<DbObjectDifference> consDiff, final List<SqlOperation> result) {
		for (final DbObjectDifference diff : consDiff) {
			final Constraint originalConstraint = diff.getOriginal(Constraint.class);
			final Constraint constraint = diff.getTarget(Constraint.class);
			addConstraintDefinition(originalTable, table, originalConstraint, constraint, diff, result);
		}
	}

	/**
	 * 制約定義を追加します
	 * 
	 * @param originalTable
	 * @param table
	 * @param originalConstraint
	 * @param constraint
	 * @param diff
	 * @param result
	 */
	protected void addConstraintDefinition(final Table originalTable, final Table table,
			final Constraint originalConstraint,final Constraint constraint,
			final DbObjectDifference diff, final List<SqlOperation> result) {
		if (diff.getState() == State.Deleted) {
			addDropConstraintDefinition(originalTable, table, originalConstraint, constraint, diff, result);
		}else if (diff.getState() == State.Modified) {
			addDropConstraintDefinition(originalTable, table, originalConstraint, constraint, diff, result);
			//
			addCreateConstraintDefinition(originalTable, table, originalConstraint, constraint, diff, result);
		}else if (diff.getState() == State.Added) {
			addCreateConstraintDefinition(originalTable, table, originalConstraint, constraint, diff, result);
		}
	}
	
	/**
	 * CREATE制約定義を追加します
	 * 
	 * @param originalTable
	 * @param table
	 * @param originalConstraint
	 * @param constraint
	 * @param diff
	 * @param result
	 */
	protected void addCreateConstraintDefinition(final Table originalTable, final Table table,
			final Constraint originalConstraint,
			final Constraint constraint,
			final DbObjectDifference diff, final List<SqlOperation> result) {
		final S builder = createSqlBuilder();
		addConstraintDefinition(originalTable, table, constraint, builder);
		add(result, createOperation(builder.toString(), SqlType.CREATE, constraint));
	}
	
	/**
	 * DROP制約定義を追加します
	 * 
	 * @param originalTable
	 * @param table
	 * @param originalConstraint
	 * @param constraint
	 * @param diff
	 * @param result
	 */
	protected void addDropConstraintDefinition(final Table originalTable, final Table table,
			final Constraint originalConstraint,final Constraint constraint,
			final DbObjectDifference diff, final List<SqlOperation> result) {
		final S builder = createSqlBuilder();
		dropConstraintDefinition(originalConstraint, builder);
		add(result, createOperation(builder.toString(), SqlType.ALTER, originalConstraint));
	}
	
	protected void addConstraintDefinition(final Table originalTable, final Table table, final Constraint obj, final S builder) {
		if (obj == null) {
			return;
		}
		final AddTableObjectDetailFactory<Constraint, AbstractSqlBuilder<?>> sqlFactory=getAddTableObjectDetailOperationFactory(obj);
		if (sqlFactory!=null){
			addConstraintDefinition(table, obj, sqlFactory, builder);
		}
	}

	/**
	 * 制約を追加します
	 * 
	 * @param constraint
	 * @param constraint
	 * @param builder
	 * @param constraint
	 */
	protected void addConstraintDefinition(final Table table, final Constraint constraint, final AddTableObjectDetailFactory<Constraint, AbstractSqlBuilder<?>> sqlFactory, final S builder) {
		builder.alter().table();
		builder.name(table, this.getOptions().isDecorateSchemaName());
		builder.add();
		sqlFactory.addObjectDetail(constraint, table, builder);
	}
	
	protected void addDropIndexDefinition(final Index obj, final S builder) {
		builder.drop().index();
		builder.name(obj);
	}

	protected void dropConstraintDefinition(final Constraint obj,
			final S builder) {
		builder.alter().table().name(obj.getParent().getTable(), this.getOptions().isDecorateSchemaName()).drop().constraint();
		builder.name(obj, false);
	}
	
	protected void addAlterTable(final Table obj, final S builder) {
		builder.alter().table();
		builder.name(obj, this.getOptions().isDecorateSchemaName());
	}

	/**
	 * Partition定義を追加します
	 * 
	 * @param partitionInfoProp
	 * @param sqlBuilder
	 */
	protected void addPartitionDefinition(final Map<String, Difference<?>> allDiff
			, final Table originalTable, final Table table
			,final DbObjectDifference partitioningProp
			, final List<SqlOperation> result) {
	}

	@SuppressWarnings("unchecked")
	@Override
	public List<SqlOperation> createSql(final Table table) {
		return Collections.EMPTY_LIST;
	}
}
