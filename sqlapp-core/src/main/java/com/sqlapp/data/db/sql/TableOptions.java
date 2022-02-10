/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
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
 * along with sqlapp-core.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.sqlapp.data.db.sql;

import java.io.Serializable;
import java.util.function.Function;

import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.function.ColumnFunction;
import com.sqlapp.data.schemas.function.ColumnPredicate;
import com.sqlapp.data.schemas.function.ColumnStringFunction;
import com.sqlapp.data.schemas.function.RowColumnStringFunction;
import com.sqlapp.data.schemas.function.SerializableFunction;
import com.sqlapp.data.schemas.function.SerializablePredicate;
import com.sqlapp.data.schemas.function.StringPredicate;
import com.sqlapp.data.schemas.function.StringSupplier;
import com.sqlapp.data.schemas.function.TableIntegerFunction;
import com.sqlapp.data.schemas.function.TablePredicate;
import com.sqlapp.data.schemas.function.TableSqlBuilder;
import com.sqlapp.data.schemas.function.TableStringFunction;
import com.sqlapp.util.AbstractSqlBuilder;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * テーブルオプション
 * 
 * @author tatsuo satoh
 * 
 */
/**
 * @author tatsuo satoh
 *
 */
@Data
@EqualsAndHashCode(callSuper=true)
@ToString(callSuper=true)
public class TableOptions extends AbstractBean implements Serializable {
	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = 7979457626849940402L;
	/**
	 * Foreign Key Constraintを出力するか?
	 */
	private TablePredicate withForeignKeyConstraint = (table->true);

	public void setWithForeignKeyConstraint(final TablePredicate withForeignKeyConstraint){
		this.withForeignKeyConstraint= withForeignKeyConstraint;
	}

	public void setWithForeignKeyConstraint(final boolean bool){
		this.withForeignKeyConstraint= (table->bool);
	}

	/**
	 * Unique Constraintを出力するか?
	 */
	private TablePredicate withUniqueConstraint = (table->true);

	public void setWithUniqueConstraint(final boolean bool){
		this.withUniqueConstraint= (table->bool);
	}

	public void setWithUniqueConstraint(final TablePredicate withUniqueConstraint){
		this.withUniqueConstraint= withUniqueConstraint;
	}

	/**
	 * Check Constraintを出力するか?
	 */
	private TablePredicate withCheckConstraint = (table->true);

	public void setWithCheckConstraint(final boolean bool){
		this.withCheckConstraint= (table->bool);
	}

	public void setWithCheckConstraint(final TablePredicate withCheckConstraint){
		this.withCheckConstraint= withCheckConstraint;
	}
	
	/**
	 * Exclude Constraintを出力するか?
	 */
	private TablePredicate withExcludeConstraint = (table->true);

	public void setWithExcludeConstraint(final boolean bool){
		this.withExcludeConstraint= (table->bool);
	}

	public void setWithExcludeConstraint(final TablePredicate withExcludeConstraint){
		this.withExcludeConstraint= withExcludeConstraint;
	}

	/**
	 * DROP PARTITIONを出力するか?
	 */
	private TablePredicate allowDropPartition = (table->true);

	public void setAllowDropPartition(final boolean bool){
		this.allowDropPartition= (table->bool);
	}

	public void setAllowDropPartition(final TablePredicate allowDropPartition){
		this.allowDropPartition= allowDropPartition;
	}

	/**
	 * ADD PARTITIONを出力するか?
	 */
	private TablePredicate allowAddPartition = (table->true);

	public void setAllowAddPartition(final boolean bool){
		this.allowAddPartition= (table->bool);
	}

	public void setAllowAddPartition(final TablePredicate allowAddPartition){
		this.allowAddPartition= allowAddPartition;
	}
	/**
	 * DML COMMIT PER TABLE
	 */
	private TablePredicate commitPerTable = (table->false);

	private ColumnStringFunction parameterExpression =(column, def)->{
		if (def == null) {
			return "/*"+column.getName()+"*/1";
		} else {
			if (def.contains("(")) {
				return "/*"+column.getName()+"*/''";
			}
			return "/*"+column.getName()+"*/"+def;
		}
	};

	private SerializableFunction<String,String> ifStartExpression = (condition)->{
		return ("/*if " + condition + " */");
	};

	private SerializableFunction<String,String> isNotEmptyExpression = (condition)->{
		return ("isNotEmpty(" + condition + ")");
	};
	
	private StringSupplier endIfExpression =()->"/*end*/";

	public void setEndIfExpression(final StringSupplier endIfExpression) {
		this.endIfExpression=endIfExpression;
	}

	public void setEndIfExpression(final String expression) {
		this.endIfExpression=()->expression;
	}

	public void setCommitPerTable(final TablePredicate commitPerTable) {
		this.commitPerTable=commitPerTable;
	}

	public void setCommitPerTable(final boolean bool) {
		this.commitPerTable=(table->bool);
	}

	private SerializablePredicate<SqlType> commitPerSqlType=(sqlType)->false;
	
	public void setCommitPerSqlType(final SerializablePredicate<SqlType> commitPerSqlType) {
		this.commitPerSqlType=commitPerSqlType;
	}

	public void setCommitPerSqlType(final  boolean bool) {
		this.commitPerSqlType=(sqlType)->bool;
	}

	/**
	 * MERGE ALL時にDELETEをするか?
	 */
	private TablePredicate mergeAllWithDelete = (table->false);

	public void setMergeAllWithDelete(final TablePredicate mergeAllWithDelete) {
		this.mergeAllWithDelete=mergeAllWithDelete;
	}

	public void setMergeAllWithDelete(final boolean bool) {
		mergeAllWithDelete=(table->bool);
	}
	
	private static int DEFAULT_DML_BATCH_SIZE=1;
	/**
	 * Batch Size for INSERT or UPDATE OR DELETE OR MERGE
	 */
	private TableIntegerFunction dmlBatchSize=(t->DEFAULT_DML_BATCH_SIZE);

	public void setDmlBatchSize(final TableIntegerFunction dmlBatchSize){
		this.dmlBatchSize= dmlBatchSize;
	}
			
	public void setDmlBatchSize(final int value){
		this.dmlBatchSize= (table->value);
	}
	/**
	 * Temporary Alias 
	 */
	private TableStringFunction temporaryAlias=(t->"_target");

	public void setTemporaryAlias(final TableStringFunction temporaryAlias){
		this.temporaryAlias= temporaryAlias;
	}
			
	public void setTemporaryAlias(final String value){
		this.temporaryAlias= (table->value);
	}
	
	/**
	 * Created At column Predicate
	 */
	private ColumnPredicate createdAtColumn = (c->c.getDataType().isDateTime()&&c.getName().equalsIgnoreCase("created_at"));
	
	public void setCreatedAtColumn(final ColumnPredicate createdAtColumn) {
		this.createdAtColumn=createdAtColumn;
	}

	public void setCreatedAtColumn(final String columnName) {
		this.createdAtColumn=(c->c.getDataType().isDateTime()&&c.getName().equalsIgnoreCase(columnName));
	}

	/**
	 * Updated At column Predicate
	 */
	private ColumnPredicate updatedAtColumn = (c->c.getDataType().isDateTime()&&c.getName().equalsIgnoreCase("updated_at"));
	public void setUpdatedAtColumn(final ColumnPredicate updatedAtColumn) {
		this.updatedAtColumn=updatedAtColumn;
	}

	public void setUpdatedAtColumn(final String columnName) {
		this.updatedAtColumn=(c->c.getDataType().isDateTime()&&c.getName().equalsIgnoreCase(columnName));
	}
	/**
	 * Optimistic Lock column Predicate
	 */
	private ColumnPredicate optimisticLockColumn = (c->c.getName().equalsIgnoreCase("lock_version")||c.getName().equalsIgnoreCase("version_no"));
	public void setOptimisticLockColumn(final ColumnPredicate optimisticLockColumn) {
		this.optimisticLockColumn=optimisticLockColumn;
	}
	public void setOptimisticLockColumn(final String columnName) {
		this.optimisticLockColumn=(c->c.getName().equalsIgnoreCase(columnName));
	}
	
	/**
	 * Insertable column Predicate
	 */
	private ColumnPredicate insertableColumn = (c->true);
	public void setInsertableColumnn(final ColumnPredicate insertableColumn) {
		this.insertableColumn=insertableColumn;
	}
	public void setInsertableColumnn(final boolean bool) {
		this.insertableColumn=(c->bool);
	}
	
	/**
	 * Updateable column Predicate
	 */
	private ColumnPredicate updateableColumn = (c->true);
	public void setUpdateableColumn(final ColumnPredicate updateableColumn) {
		this.updateableColumn=updateableColumn;
	}
	public void setUpdateableColumn(final boolean bool) {
		this.updateableColumn=(c->bool);
	}
	/**
	 * Function for insert table column.
	 */
	private ColumnFunction<String> insertTableColumnValue=(c)->c.getName();
	/**
	 * Function for update table column.
	 */
	private ColumnFunction<String> updateTableColumnValue=(c)->c.getName();
	/**
	 * Function for insert row value.
	 */
	private RowColumnStringFunction insertRowSqlValue=(r, c, v)->v;
	/**
	 * ${readFileAsBytes('src/main/resources/path')}
	 */
	private StringPredicate dynamicValue=(v)->v!=null&&v.startsWith("${")&&v.endsWith("}");
	/**
	 * Function for insert row value.
	 */
	private RowColumnStringFunction updateRowSqlValue=(r, c, v)->v;
	/**
	 * Optimistic Lock column insert
	 * COALESCE( column, 0 )
	 */
	private ColumnPredicate withCoalesceAtInsert = (c->false);

	public void setWithCoalesceAtInsert(final boolean bool){
		this.withCoalesceAtInsert= (c->bool);
	}

	public void setWithCoalesceAtInsert(final ColumnPredicate withCoalesceAtInsert){
		this.withCoalesceAtInsert= withCoalesceAtInsert;
	}
	
	/** temp table name */
	private TableStringFunction tempTableName = (t->t.getName()+"_temp");
	
	/** column remarks */
	private ColumnPredicate withColumnRemarks=(c->false);
	public void setWithColumnRemarks(final boolean bool){
		this.withColumnRemarks= (c->bool);
	}

	public void setWithColumnRemarks(final ColumnPredicate withColumnRemarks){
		this.withColumnRemarks= withColumnRemarks;
	}

	private TablePredicate selectAllColumnASAsterisk=t->true;

	public void setSelectAllColumnASAsterisk(final boolean bool){
		this.selectAllColumnASAsterisk= (t->bool);
	}

	public void setSelectAllColumnASAsterisk(final TablePredicate selectAllColumnASAsterisk){
		this.selectAllColumnASAsterisk= selectAllColumnASAsterisk;
	}

	/**
	 * Optimistic Lock column update
	 * COALESCE( column, 0 )
	 */
	private ColumnPredicate withCoalesceAtUpdate = (c->false);

	public ColumnPredicate getWithCoalesceAtUpdate(){
		return this.withCoalesceAtUpdate;
	}
	
	public void setWithCoalesceAtUpdate(final boolean bool){
		this.withCoalesceAtUpdate= (c->bool);
	}

	public void setWithCoalesceAtUpdate(final ColumnPredicate withCoalesceAtUpdate){
		this.withCoalesceAtUpdate= withCoalesceAtUpdate;
	}

	public void setTempTableName(final TableStringFunction tempTableName){
		this.tempTableName= tempTableName;
	}

	/**
	 * Auto Increment column Predicate
	 */
	private ColumnPredicate autoIncrementColumn = (c->c.isIdentity()||c.getDataType().isAutoIncrementable());
	/**
	 * SELECT ALLのWHERE以降の条件
	 */
	private TableSqlBuilder<AbstractSqlBuilder<?>> selectAllCondition=null;

	public void setSelectAllCondition(final TableSqlBuilder<AbstractSqlBuilder<?>> selectAllCondition) {
		this.selectAllCondition=selectAllCondition;
	}
	/**
	 * UPDATE ALLのWHERE以降の条件
	 */
	private TableSqlBuilder<AbstractSqlBuilder<?>> updateAllCondition=null;

	public void setUpdateAllCondition(final TableSqlBuilder<AbstractSqlBuilder<?>> updateAllCondition) {
		this.updateAllCondition=updateAllCondition;
	}
	/**
	 * UPDATE ALLのWHERE以降の条件
	 */
	private TableSqlBuilder<AbstractSqlBuilder<?>> deleteAllCondition=null;

	public void setDeleteAllCondition(final TableSqlBuilder<AbstractSqlBuilder<?>> deleteAllCondition) {
		this.deleteAllCondition=deleteAllCondition;
	}

	/**
	 * INSERT SQL TYPE
	 */
	private SqlType insertSqlType = SqlType.INSERT;
	/**
	 * UPDATE SQL TYPE
	 */
	private SqlType updateSqlType = SqlType.UPDATE;
	/**
	 * DELETE SQL TYPE
	 */
	private SqlType deleteSqlType = SqlType.DELETE_BY_PK;
	/**
	 * TRUNCATE SQL TYPE
	 */
	private SqlType truncateSqlType = SqlType.TRUNCATE;
	/**
	 * TABLE LOCK MODE
	 */
	private Function<Table, TableLockMode> lockMode=t->TableLockMode.EXCLUSIVE;
	/**
	 * DDL column comment
	 */
	private ColumnFunction<String> columnComment=(c)->c.getRemarks();
	/**
	 * SELECT column comment
	 */
	private ColumnFunction<String> selectColumnComment=(c)->null;
	/**
	 * INSERT column comment
	 */
	private ColumnFunction<String> insertColumnComment=(c)->null;
	/**
	 * UPDATE column comment
	 */
	private ColumnFunction<String> updateColumnComment=(c)->null;
	
	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Object#clone()
	 */
	@Override
	public TableOptions clone() {
		return (TableOptions) super.clone();
	}

}
