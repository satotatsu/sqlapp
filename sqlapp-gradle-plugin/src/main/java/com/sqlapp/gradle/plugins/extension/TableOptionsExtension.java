/**
 * Copyright (C) 2007-2025 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-gradle-plugin.
 *
 * sqlapp-gradle-plugin is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-gradle-plugin is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-gradle-plugin.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.gradle.plugins.extension;

import java.util.function.Function;

import org.gradle.api.Action;
import org.gradle.api.tasks.Input;

import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.db.sql.TableLockMode;
import com.sqlapp.data.db.sql.TableOptions;
import com.sqlapp.data.schemas.Index;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.function.ColumnFunction;
import com.sqlapp.data.schemas.function.ColumnPredicate;
import com.sqlapp.data.schemas.function.ColumnStringFunction;
import com.sqlapp.data.schemas.function.RowColumnStringFunction;
import com.sqlapp.data.schemas.function.SerializableFunction;
import com.sqlapp.data.schemas.function.SerializablePredicate;
import com.sqlapp.data.schemas.function.StringPredicate;
import com.sqlapp.data.schemas.function.StringSupplier;
import com.sqlapp.data.schemas.function.TableBiPredicate;
import com.sqlapp.data.schemas.function.TableFunction;
import com.sqlapp.data.schemas.function.TableIntegerFunction;
import com.sqlapp.data.schemas.function.TablePredicate;
import com.sqlapp.data.schemas.function.TableSqlBuilder;
import com.sqlapp.data.schemas.function.TableStringFunction;
import com.sqlapp.util.AbstractSqlBuilder;

public abstract class TableOptionsExtension extends TableOptions {

	/** serialVersionUID */
	private static final long serialVersionUID = 6587668825226256702L;

	public TableOptionsExtension() {
		this.setDmlBatchSize(DEFAULT_DML_BATCH_SIZE);
	}

	public void call(Action<TableOptionsExtension> cons) {
		cons.execute(this);
	}

	/**
	 * Foreign Key Constraintを出力するか?
	 */
	@Input
	TablePredicate withForeignKeyConstraint = super.getWithForeignKeyConstraint();

	@Override
	public void setWithForeignKeyConstraint(final boolean bool) {
		this.setWithForeignKeyConstraint(table -> bool);
	}

	@Override
	public void setWithForeignKeyConstraint(final TablePredicate withForeignKeyConstraint) {
		super.setWithForeignKeyConstraint(withForeignKeyConstraint);
		this.withForeignKeyConstraint = withForeignKeyConstraint;
	}

	/**
	 * Unique Constraintを出力するか?
	 */
	@Input
	TablePredicate withUniqueConstraint = super.getWithUniqueConstraint();

	@Override
	public void setWithUniqueConstraint(final boolean bool) {
		this.setWithUniqueConstraint(table -> bool);
	}

	@Override
	public void setWithUniqueConstraint(final TablePredicate withUniqueConstraint) {
		super.setWithUniqueConstraint(withUniqueConstraint);
		this.withUniqueConstraint = withUniqueConstraint;
	}

	/**
	 * オンラインインデックス
	 */
	@Input
	TableBiPredicate<Index> onlineIndex = super.getOnlineIndex();

	@Override
	public void setOnlineIndex(final boolean bool) {
		this.setOnlineIndex((table, index) -> bool);
	}

	@Override
	public void setOnlineIndex(TableBiPredicate<Index> onlineIndex) {
		super.setOnlineIndex(onlineIndex);
		this.onlineIndex = onlineIndex;
	}

	/**
	 * Check Constraintを出力するか?
	 */
	@Input
	private TablePredicate withCheckConstraint = super.getWithCheckConstraint();

	@Override
	public void setWithCheckConstraint(final boolean bool) {
		this.setWithCheckConstraint(table -> bool);
	}

	@Override
	public void setWithCheckConstraint(final TablePredicate withCheckConstraint) {
		super.setWithCheckConstraint(withCheckConstraint);
		this.withCheckConstraint = withCheckConstraint;
	}

	/**
	 * Exclude Constraintを出力するか?
	 */
	@Input
	TablePredicate withExcludeConstraint = super.getWithExcludeConstraint();

	@Override
	public void setWithExcludeConstraint(final boolean bool) {
		this.setWithExcludeConstraint(table -> bool);
	}

	@Override
	public void setWithExcludeConstraint(final TablePredicate withExcludeConstraint) {
		super.setWithExcludeConstraint(withExcludeConstraint);
		this.withExcludeConstraint = withExcludeConstraint;
	}

	/**
	 * DROP PARTITIONを出力するか?
	 */
	@Input
	TablePredicate allowDropPartition = super.getAllowDropPartition();

	@Override
	public void setAllowDropPartition(final boolean bool) {
		this.setAllowDropPartition(table -> bool);
	}

	@Override
	public void setAllowDropPartition(final TablePredicate allowDropPartition) {
		super.setAllowDropPartition(allowDropPartition);
		this.allowDropPartition = allowDropPartition;
	}

	/**
	 * ADD PARTITIONを出力するか?
	 */
	@Input
	TablePredicate allowAddPartition = super.getAllowAddPartition();

	@Override
	public void setAllowAddPartition(final boolean bool) {
		this.setAllowAddPartition(table -> bool);
	}

	@Override
	public void setAllowAddPartition(final TablePredicate allowAddPartition) {
		super.setAllowAddPartition(allowAddPartition);
		this.allowAddPartition = allowAddPartition;
	}

	/**
	 * DML COMMIT PER TABLE
	 */
	@Input
	private TablePredicate commitPerTable = super.getCommitPerTable();

	@Override
	public void setCommitPerTable(final boolean bool) {
		this.setAllowAddPartition(table -> bool);
	}

	@Override
	public void setCommitPerTable(final TablePredicate commitPerTable) {
		super.setCommitPerTable(commitPerTable);
		this.commitPerTable = commitPerTable;
	}

	@Input
	private ColumnStringFunction parameterExpression = super.getParameterExpression();

	@Override
	public void setParameterExpression(ColumnStringFunction parameterExpression) {
		super.setParameterExpression(parameterExpression);
		this.parameterExpression = parameterExpression;
	}

	@Input
	SerializableFunction<String, String> ifStartExpression = super.getIfStartExpression();

	@Override
	public void setIfStartExpression(SerializableFunction<String, String> ifStartExpression) {
		super.setIfStartExpression(ifStartExpression);
		this.ifStartExpression = ifStartExpression;
	}

	@Input
	SerializableFunction<String, String> isNotEmptyExpression = super.getIsNotEmptyExpression();

	@Override
	public void setIsNotEmptyExpression(SerializableFunction<String, String> isNotEmptyExpression) {
		super.setIsNotEmptyExpression(isNotEmptyExpression);
		this.isNotEmptyExpression = isNotEmptyExpression;
	}

	@Input
	StringSupplier endIfExpression = super.getEndIfExpression();

	@Override
	public void setEndIfExpression(final StringSupplier endIfExpression) {
		super.setEndIfExpression(endIfExpression);
		this.endIfExpression = endIfExpression;
	}

	@Override
	public void setEndIfExpression(final String expression) {
		this.setEndIfExpression(() -> expression);
	}

	@Input
	SerializablePredicate<SqlType> commitPerSqlType = super.getCommitPerSqlType();

	@Override
	public void setCommitPerSqlType(final boolean bool) {
		this.setCommitPerSqlType(sqlType -> bool);
	}

	@Override
	public void setCommitPerSqlType(final SerializablePredicate<SqlType> commitPerSqlType) {
		super.setCommitPerSqlType(commitPerSqlType);
		this.commitPerSqlType = commitPerSqlType;
	}

	/**
	 * MERGE ALL時にDELETEをするか?
	 */
	@Input
	TablePredicate mergeAllWithDelete = super.getMergeAllWithDelete();

	@Override
	public void setMergeAllWithDelete(final boolean bool) {
		this.setMergeAllWithDelete(table -> bool);
	}

	@Override
	public void setMergeAllWithDelete(final TablePredicate mergeAllWithDelete) {
		super.setMergeAllWithDelete(mergeAllWithDelete);
		this.mergeAllWithDelete = mergeAllWithDelete;
	}

	private static int DEFAULT_DML_BATCH_SIZE = 500;
	/**
	 * Batch Size for INSERT or UPDATE OR DELETE OR MERGE
	 */
	@Input
	private TableIntegerFunction dmlBatchSize = (t -> DEFAULT_DML_BATCH_SIZE);

	@Override
	public void setDmlBatchSize(final int value) {
		this.setDmlBatchSize(table -> value);
	}

	@Override
	public void setDmlBatchSize(final TableIntegerFunction dmlBatchSize) {
		super.setDmlBatchSize(dmlBatchSize);
		this.dmlBatchSize = dmlBatchSize;
	}

	/**
	 * Temporary Alias
	 */
	@Input
	TableStringFunction temporaryAlias = super.getTemporaryAlias();

	@Override
	public void setTemporaryAlias(final String value) {
		this.setTemporaryAlias(table -> value);
	}

	@Override
	public void setTemporaryAlias(final TableStringFunction temporaryAlias) {
		super.setTemporaryAlias(temporaryAlias);
		this.temporaryAlias = temporaryAlias;
	}

	/**
	 * Created At column Predicate
	 */
	@Input
	ColumnPredicate createdAtColumn = (c -> c.getDataType().isDateTime() && c.getName().equalsIgnoreCase("created_at"));

	@Override
	public void setCreatedAtColumn(final String columnName) {
		this.setCreatedAtColumn(c -> c.getDataType().isDateTime() && c.getName().equalsIgnoreCase(columnName));
	}

	@Override
	public void setCreatedAtColumn(final ColumnPredicate createdAtColumn) {
		super.setCreatedAtColumn(createdAtColumn);
		this.createdAtColumn = createdAtColumn;
	}

	/**
	 * Updated At column Predicate
	 */
	@Input
	ColumnPredicate updatedAtColumn = (c -> c.getDataType().isDateTime() && c.getName().equalsIgnoreCase("updated_at"));

	@Override
	public void setUpdatedAtColumn(final String columnName) {
		this.setUpdatedAtColumn(c -> c.getDataType().isDateTime() && c.getName().equalsIgnoreCase(columnName));
	}

	@Override
	public void setUpdatedAtColumn(final ColumnPredicate updatedAtColumn) {
		super.setUpdatedAtColumn(updatedAtColumn);
		this.updatedAtColumn = updatedAtColumn;
	}

	/**
	 * Optimistic Lock column Predicate
	 */
	@Input
	ColumnPredicate optimisticLockColumn = (c -> c.getName().equalsIgnoreCase("lock_version")
			|| c.getName().equalsIgnoreCase("version_no"));

	@Override
	public void setOptimisticLockColumn(final String columnName) {
		this.setOptimisticLockColumn(c -> c.getName().equalsIgnoreCase(columnName));
	}

	@Override
	public void setOptimisticLockColumn(final ColumnPredicate optimisticLockColumn) {
		super.setOptimisticLockColumn(optimisticLockColumn);
		this.optimisticLockColumn = optimisticLockColumn;
	}

	/**
	 * Insertable column Predicate
	 */
	@Input
	private ColumnPredicate insertableColumn = super.getInsertableColumn();

	@Override
	public void setInsertableColumn(final boolean bool) {
		this.setInsertableColumn(c -> bool);
	}

	@Override
	public void setInsertableColumn(final ColumnPredicate insertableColumn) {
		super.setInsertableColumn(insertableColumn);
		this.insertableColumn = insertableColumn;
	}

	/**
	 * Updateable column Predicate
	 */
	@Input
	ColumnPredicate updateableColumn = super.getUpdateableColumn();

	@Override
	public void setUpdateableColumn(final boolean bool) {
		this.setUpdateableColumn(c -> bool);
	}

	@Override
	public void setUpdateableColumn(final ColumnPredicate updateableColumn) {
		super.setUpdateableColumn(updateableColumn);
		this.updateableColumn = updateableColumn;
	}

	/**
	 * Function for insert table column.
	 */
	@Input
	ColumnFunction<String> insertTableColumnValue = super.getInsertTableColumnValue();

	@Override
	public void setInsertTableColumnValue(final ColumnFunction<String> insertTableColumnValue) {
		super.setInsertTableColumnValue(insertTableColumnValue);
		this.insertTableColumnValue = insertTableColumnValue;
	}

	/**
	 * Function for update table column.
	 */
	@Input
	ColumnFunction<String> updateTableColumnValue = super.getUpdateTableColumnValue();

	@Override
	public void setUpdateTableColumnValue(final ColumnFunction<String> updateTableColumnValue) {
		super.setUpdateTableColumnValue(updateTableColumnValue);
		this.updateTableColumnValue = updateTableColumnValue;
	}

	/**
	 * Function for insert row value.
	 */
	@Input
	RowColumnStringFunction insertRowSqlValue = super.getInsertRowSqlValue();

	@Override
	public void setInsertRowSqlValue(final RowColumnStringFunction insertRowSqlValue) {
		super.setInsertRowSqlValue(insertRowSqlValue);
		this.insertRowSqlValue = insertRowSqlValue;
	}

	/**
	 * ${readFileAsBytes('src/main/resources/path')}
	 */
	@Input
	StringPredicate dynamicValue = super.getDynamicValue();

	@Override
	public void setDynamicValue(final StringPredicate dynamicValue) {
		super.setDynamicValue(dynamicValue);
		this.dynamicValue = dynamicValue;
	}

	/**
	 * Function for insert row value.
	 */
	@Input
	RowColumnStringFunction updateRowSqlValue = super.getUpdateRowSqlValue();

	@Override
	public void setUpdateRowSqlValue(final RowColumnStringFunction updateRowSqlValue) {
		super.setUpdateRowSqlValue(updateRowSqlValue);
		this.updateRowSqlValue = updateRowSqlValue;
	}

	/**
	 * Optimistic Lock column insert COALESCE( column, 0 )
	 */
	@Input
	ColumnPredicate withCoalesceAtInsert = super.getWithCoalesceAtInsert();

	@Override
	public void setWithCoalesceAtInsert(final boolean bool) {
		this.setWithCoalesceAtInsert(c -> bool);
	}

	public void setWithCoalesceAtInsert(final ColumnPredicate withCoalesceAtInsert) {
		super.setWithCoalesceAtInsert(withCoalesceAtInsert);
		this.withCoalesceAtInsert = withCoalesceAtInsert;
	}

	/** temp table name */
	@Input
	TableStringFunction tempTableName = super.getTempTableName();

	@Override
	public void setTempTableName(final TableStringFunction tempTableName) {
		super.setTempTableName(tempTableName);
		this.tempTableName = tempTableName;
	}

	/** column remarks */
	@Input
	ColumnPredicate withColumnRemarks = super.getWithColumnRemarks();

	@Override
	public void setWithColumnRemarks(final boolean bool) {
		this.setWithColumnRemarks(c -> bool);
	}

	@Override
	public void setWithColumnRemarks(final ColumnPredicate withColumnRemarks) {
		super.setWithColumnRemarks(withColumnRemarks);
		this.withColumnRemarks = withColumnRemarks;
	}

	@Input
	private TablePredicate selectAllColumnAsAsterisk = super.getSelectAllColumnAsAsterisk();

	public void setSelectAllColumnAsAsterisk(final boolean bool) {
		this.setSelectAllColumnAsAsterisk(t -> bool);
	}

	public void setSelectAllColumnAsAsterisk(final TablePredicate selectAllColumnAsAsterisk) {
		super.setSelectAllColumnAsAsterisk(selectAllColumnAsAsterisk);
		this.selectAllColumnAsAsterisk = selectAllColumnAsAsterisk;
	}

	/**
	 * Optimistic Lock column update COALESCE( column, 0 )
	 */
	@Input
	private ColumnPredicate withCoalesceAtUpdate = super.getWithCoalesceAtUpdate();

	public void setWithCoalesceAtUpdate(final boolean bool) {
		this.setWithCoalesceAtUpdate(c -> bool);
	}

	public void setWithCoalesceAtUpdate(final ColumnPredicate withCoalesceAtUpdate) {
		super.setWithCoalesceAtUpdate(withCoalesceAtUpdate);
		this.withCoalesceAtUpdate = withCoalesceAtUpdate;
	}

	/**
	 * Auto Increment column Predicate
	 */
	@Input
	private ColumnPredicate autoIncrementColumn = (c -> c.isIdentity() || c.getDataType().isAutoIncrementable());

	@Override
	public void setAutoIncrementColumn(final ColumnPredicate autoIncrementColumn) {
		super.setAutoIncrementColumn(autoIncrementColumn);
		this.autoIncrementColumn = autoIncrementColumn;
	}

	/**
	 * SELECT ALLのWHERE以降の条件
	 */
	@Input
	private TableSqlBuilder<AbstractSqlBuilder<?>> selectAllCondition = null;

	public void setSelectAllCondition(final TableSqlBuilder<AbstractSqlBuilder<?>> selectAllCondition) {
		super.setSelectAllCondition(selectAllCondition);
		this.selectAllCondition = selectAllCondition;
	}

	/**
	 * UPDATE ALLのWHERE以降の条件
	 */
	@Input
	private TableSqlBuilder<AbstractSqlBuilder<?>> updateAllCondition = null;

	public void setUpdateAllCondition(final TableSqlBuilder<AbstractSqlBuilder<?>> updateAllCondition) {
		super.setUpdateAllCondition(updateAllCondition);
		this.updateAllCondition = updateAllCondition;
	}

	/**
	 * UPDATE ALLのWHERE以降の条件
	 */
	@Input
	private TableSqlBuilder<AbstractSqlBuilder<?>> deleteAllCondition = null;

	public void setDeleteAllCondition(final TableSqlBuilder<AbstractSqlBuilder<?>> deleteAllCondition) {
		super.setDeleteAllCondition(deleteAllCondition);
		this.deleteAllCondition = deleteAllCondition;
	}

	/**
	 * INSERT SQL TYPE
	 */
	@Input
	SqlType insertSqlType = super.getInsertSqlType();

	@Override
	public void setInsertSqlType(final SqlType insertSqlType) {
		super.setInsertSqlType(insertSqlType);
		this.insertSqlType = insertSqlType;
	}

	/**
	 * UPDATE SQL TYPE
	 */
	@Input
	SqlType updateSqlType = super.getUpdateSqlType();

	@Override
	public void setUpdateSqlType(final SqlType updateSqlType) {
		super.setUpdateSqlType(updateSqlType);
		this.updateSqlType = updateSqlType;
	}

	/**
	 * DELETE SQL TYPE
	 */
	@Input
	SqlType deleteSqlType = super.getDeleteSqlType();

	@Override
	public void setDeleteSqlType(final SqlType deleteSqlType) {
		super.setDeleteSqlType(deleteSqlType);
		this.deleteSqlType = deleteSqlType;
	}

	/**
	 * TRUNCATE SQL TYPE
	 */
	@Input
	Function<Table, SqlType> truncateSqlType = super.getTruncateSqlType();

	@Override
	public void setTruncateSqlType(final SqlType sqlType) {
		this.setTruncateSqlType(t -> sqlType);
	}

	@Override
	public void setTruncateSqlType(final Function<Table, SqlType> truncateSqlType) {
		super.setTruncateSqlType(truncateSqlType);
		this.truncateSqlType = truncateSqlType;
	}

	/**
	 * TABLE LOCK MODE
	 */
	@Input
	Function<Table, TableLockMode> lockMode = super.getLockMode();

	@Override
	public void setLockMode(final Function<Table, TableLockMode> lockMode) {
		super.setLockMode(lockMode);
		this.lockMode = lockMode;
	}

	/**
	 * DDL column comment
	 */
	@Input
	ColumnFunction<String> columnComment = super.getColumnComment();

	@Override
	public void setColumnComment(final ColumnFunction<String> columnComment) {
		super.setColumnComment(columnComment);
		this.columnComment = columnComment;
	}

	/**
	 * SELECT column comment
	 */
	@Input
	ColumnFunction<String> selectColumnComment = super.getSelectColumnComment();

	@Override
	public void setSelectColumnComment(final ColumnFunction<String> selectColumnComment) {
		super.setSelectColumnComment(selectColumnComment);
		this.selectColumnComment = selectColumnComment;
	}

	/**
	 * INSERT column comment
	 */
	@Input
	ColumnFunction<String> insertColumnComment = super.getInsertColumnComment();

	@Override
	public void setInsertColumnComment(final ColumnFunction<String> insertColumnComment) {
		super.setInsertColumnComment(insertColumnComment);
		this.insertColumnComment = insertColumnComment;
	}

	/**
	 * UPDATE column comment
	 */
	@Input
	ColumnFunction<String> updateColumnComment = super.getUpdateColumnComment();

	@Override
	public void setUpdateColumnComment(final ColumnFunction<String> updateColumnComment) {
		super.setUpdateColumnComment(updateColumnComment);
		this.updateColumnComment = updateColumnComment;
	}

	/**
	 * WHERE column comment
	 */
	@Input
	ColumnFunction<String> whereColumnComment = super.getWhereColumnComment();

	@Override
	public void setWhereColumnComment(final ColumnFunction<String> whereColumnComment) {
		super.setWhereColumnComment(whereColumnComment);
		this.whereColumnComment = whereColumnComment;
	}

	/**
	 * table comment
	 */
	@Input
	TableFunction<String> tableComment = super.getTableComment();

	@Override
	public void setTableComment(final TableFunction<String> tableComment) {
		super.setTableComment(tableComment);
		this.tableComment = tableComment;
	}

}
