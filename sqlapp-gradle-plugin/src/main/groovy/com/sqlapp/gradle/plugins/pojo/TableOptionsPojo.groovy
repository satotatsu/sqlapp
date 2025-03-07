/*
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
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

package com.sqlapp.gradle.plugins.pojo;

import java.util.function.Function
import java.util.function.Predicate

import org.gradle.api.Project;

import com.sqlapp.data.db.sql.SqlType
import com.sqlapp.data.db.sql.TableLockMode
import com.sqlapp.data.db.sql.TableOptions;
import com.sqlapp.data.schemas.Table
import com.sqlapp.data.schemas.function.ColumnPredicate
import com.sqlapp.data.schemas.function.RowColumnStringFunction
import com.sqlapp.data.schemas.function.TableIntegerFunction
import com.sqlapp.data.schemas.function.TablePredicate
import com.sqlapp.data.schemas.function.TableSqlBuilder
import com.sqlapp.data.schemas.function.TableStringFunction
import com.sqlapp.util.AbstractSqlBuilder
import com.sqlapp.util.SimpleBeanUtils;

import groovy.lang.Closure

public class TableOptionsPojo extends TableOptions{
	
	Project project;

	public TableOptionsPojo(final TableOptions tableOption, final Project project) {
		SimpleBeanUtils.copyProperties(tableOption, this);
		this.project=project;
	}

	public TableOptionsPojo() {
		this.project=null;
	}
	
	public TableOptionsPojo(Project project) {
		this.project=project;
	}
	
	public void withForeignKeyConstraint(TablePredicate p){
		super.setWithForeignKeyConstraint(p);
	}

	public void withForeignKeyConstraint(boolean bool){
		super.setWithForeignKeyConstraint(bool);
	}

	public void withUniqueConstraint(boolean bool){
		super.setWithUniqueConstraint(bool);
	}

	public void withUniqueConstraint(TablePredicate p){
		super.setWithUniqueConstraint(p);
	}

	public void withCheckConstraint(boolean bool){
		super.setWithCheckConstraint(bool);
	}

	public void withCheckConstraint(TablePredicate p){
		super.setWithCheckConstraint(p);
	}

	public void withExcludeConstraint(boolean bool){
		super.setWithExcludeConstraint(bool);
	}

	public void withExcludeConstraint(TablePredicate p){
		super.setWithExcludeConstraint(p);
	}

	public void allowDropPartition(boolean bool){
		super.setAllowDropPartition(bool);
	}

	public void allowDropPartition(TablePredicate p){
		super.setAllowDropPartition(p);
	}

	public void allowAddPartition(boolean bool){
		super.setAllowAddPartition(bool);
	}

	public void allowAddPartition(TablePredicate p){
		super.setAllowAddPartition(p);
	}

	public void dmlBatchSize(TableIntegerFunction dmlBatchSize){
		super.setDmlBatchSize(dmlBatchSize);
	}
			
	public void setDmlBatchSize(int value){
		super.setDmlBatchSize(value);
	}

	public void temporaryAlias(TableStringFunction temporaryAlias){
		super.setTemporaryAlias(temporaryAlias);
	}
			
	public void temporaryAlias(String value){
		super.setTemporaryAlias(value);
	}

	public void withCoalesceAtInsert(boolean bool){
		super.setWithCoalesceAtInsert(bool);
	}

	public void withCoalesceAtInsert(ColumnPredicate withCoalesceAtInsert){
		super.setWithCoalesceAtInsert(withCoalesceAtInsert);
	}
	
	public void withCoalesceAtUpdate(boolean bool){
		super.setWithCoalesceAtUpdate(bool);
	}

	public void withCoalesceAtUpdate(ColumnPredicate withCoalesceAtUpdate){
		super.setWithCoalesceAtInsert(withCoalesceAtUpdate);
	}

	public void autoIncrementColumn(ColumnPredicate withCoalesceAtUpdate){
		super.setAutoIncrementColumn(withCoalesceAtUpdate);
	}

	public void selectAllSql(TableSqlBuilder<AbstractSqlBuilder<?>> selectAllSql){
		super.setSelectAllSql(selectAllSql);
	}

	public void insertSqlType(SqlType insertSqlType){
		super.setInsertSqlType(insertSqlType);
	}

	public void updateSqlType(SqlType updateSqlType){
		super.setUpdateSqlType(updateSqlType);
	}

	public void deleteSqlType(SqlType deleteSqlType){
		super.setDeleteSqlType(deleteSqlType);
	}

	public void truncateSqlType(SqlType truncateSqlType){
		super.setTruncateSqlType(truncateSqlType);
	}

	public void truncateSqlType(Function<Table, TableLockMode> lockMode){
		super.setTruncateSqlType(lockMode);
	}

	public void truncateSqlType(TableLockMode lockMode){
		super.setTruncateSqlType({t->lockMode});
	}

}
