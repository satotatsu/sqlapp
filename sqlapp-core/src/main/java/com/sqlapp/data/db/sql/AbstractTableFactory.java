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

import java.util.List;

import com.sqlapp.data.db.datatype.DbDataType;
import com.sqlapp.data.schemas.AbstractDbObject;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Index;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.util.AbstractSqlBuilder;

/**
 * TABLE関係の生成クラス
 * 
 * @author satoh
 * 
 */
public abstract class AbstractTableFactory<S extends AbstractSqlBuilder<?>>
		extends SimpleSqlFactory<Table, S> {
	
	protected void addColumnDefinition(Column column, S builder){
		builder._add(getValueDefinitionSimple(column));
	}

	
	/**
	 * ユニークカラムの検索条件を追加します
	 * 
	 * @param table
	 * @param builder
	 */
	protected void addUniqueColumnsCondition(Table table,
			S builder) {
		builder.setQuateObjectName(this.isQuateColumnName());
		List<Column> columns = table.getUniqueColumns();
		builder.appendIndent(1);
		for (Column column : columns) {
			builder.lineBreak();
			builder.and().name(column);
			builder.space().eq().space()._add(getValueDefinitionSimple(column));
		}
		builder.appendIndent(-1);
		builder.setQuateObjectName(false);
	}
	
	/**
	 * 楽観的ロックカラムの検索条件を追加します
	 * 
	 * @param table
	 * @param builder
	 */
	protected void addLockVersionColumnCondition(Table table,
			S builder) {
		builder.appendIndent(1);
		for(Column column:table.getColumns()){
			if (isOptimisticLockColumn(column)){
				builder.lineBreak();
				builder.and().name(column);
				String value=this.getOptimisticLockColumnCondition(column);
				builder.space().eq().space()._add(value);
				break;
			}
		}
		builder.appendIndent(-1);
	}

	/**
	 * 全カラムの条件を追加します。
	 * @param obj
	 * @param builder
	 */
	protected void addConditionColumns(Table obj, S builder) {
		builder.appendIndent(+1);
		for(Column column:obj.getColumns()){
			addConditions(column, builder);
		}
		builder.appendIndent(-1);
	}

	protected void addConditions(Column column, S builder) {
		addConditionIn(column, builder);
		addConditionNotIn(column, builder);
		if (column.getDataType()==null){
			return;
		}
		if (column.getDataType().isCharacter()){
			addConditionStartsWith(column, builder);
			addConditionEndsWith(column, builder);
			addConditionContains(column, builder);
		}else{
			if (!column.getDataType().isBinary()&&!column.getDataType().isBoolean()){
				addConditionGt(column, builder);
				addConditionLt(column, builder);
				addConditionGte(column, builder);
				addConditionLte(column, builder);
			}
		}
	}

	protected TableLockMode getLockMode(Table table){
		return this.getOptions().getTableOptions().getLockMode().apply(table);
	}
	
	protected void addConditionIn(Column column, S builder) {
		builder.lineBreak()._add("/*if isNotEmpty("+column.getName()+ ") */");
		builder.lineBreak();
		builder.and().space().name(column).space().in().space()._add("/*"+column.getName()+"*/");
		addConditionInValue(column, builder);
	}

	protected void addConditionNotIn(Column column, S builder) {
		builder.lineBreak()._add("/*if isNotEmpty("+column.getName()+ "_neq) */");
		builder.lineBreak();
		builder.and().space().name(column).space().not().in().space()._add("/*"+column.getName()+"_neq*/");
		addConditionInValue(column, builder);
	}

	protected void addConditionContains(Column column, S builder) {
		builder.lineBreak()._add("/*if isNotEmpty("+column.getName()+ "_contains) */");
		builder.lineBreak();
		builder.and().space().name(column).space().like().space()._add("/*'%' + "+column.getName()+"_contains + '%'*/");
		addConditionValue(column, builder);
	}
	
	protected void addConditionStartsWith(Column column, S builder) {
		builder.lineBreak()._add("/*if isNotEmpty("+column.getName()+ "_startsWith) */");
		builder.lineBreak();
		builder.and().space().name(column).space().like().space()._add("/*"+column.getName()+"_startsWith + '%'*/");
		addConditionValue(column, builder);
	}

	protected void addConditionEndsWith(Column column, S builder) {
		builder.lineBreak()._add("/*if isNotEmpty("+column.getName()+ "_endsWith) */");
		builder.lineBreak();
		builder.and().space().name(column).space().like().space()._add("/*'%' + "+column.getName()+"_endsWith*/");
		addConditionValue(column, builder);
	}

	protected void addConditionGt(Column column, S builder) {
		builder.lineBreak()._add("/*if isNotEmpty("+column.getName()+ "_gt) */");
		builder.lineBreak();
		builder.and().space().name(column).space().gt().space()._add("/*"+column.getName()+"_gt*/");
		addConditionValue(column, builder);
	}

	protected void addConditionLt(Column column, S builder) {
		builder.lineBreak()._add("/*if isNotEmpty("+column.getName()+ "_lt) */");
		builder.lineBreak();
		builder.and().space().name(column).space().lt().space()._add("/*"+column.getName()+"_lt*/");
		addConditionValue(column, builder);
	}

	protected void addConditionGte(Column column, S builder) {
		builder.lineBreak()._add("/*if isNotEmpty("+column.getName()+ "_gte) */");
		builder.lineBreak();
		builder.and().space().name(column).space().gt().eq().space()._add("/*"+column.getName()+"_gte*/");
		addConditionValue(column, builder);
	}

	protected void addConditionLte(Column column, S builder) {
		builder.lineBreak()._add("/*if isNotEmpty("+column.getName()+ "_lte) */");
		builder.lineBreak();
		builder.and().space().name(column).space().lt().eq().space()._add("/*"+column.getName()+"_lte*/");
		addConditionValue(column, builder);
	}

	protected void addConditionInValue(Column column, S builder) {
		if (column.getDataType()==null){
			builder._add("('')");
		} else{
			builder._add("(");
			builder._add(getDefaultValueLiteral(column));
			builder._add(")");
		}
		builder.lineBreak();
		builder._add("/*end*/");
	}

	protected void addConditionValue(Column column, S builder) {
		if (column.getDataType()==null){
			builder._add("''");
		} else{
			builder._add(getDefaultValueLiteral(column));
		}
		builder.lineBreak();
		builder._add("/*end*/");
	}

	protected String getDefaultValueLiteral(Column column) {
		DbDataType<?> dbDataType = this.getDialect().getDbDataType(column);
		return dbDataType.getDefaultValueLiteral();
	}
	
	protected void addCreateIndexDefinition(Index index, S builder) {
		if (index == null) {
			return;
		}
		AddTableObjectDetailFactory<Index, AbstractSqlBuilder<?>> sqlFactory
			=getAddTableObjectDetailOperationFactory(index);
		if (sqlFactory!=null) {
			builder.create();
			sqlFactory.addObjectDetail(index, index.getTable(),
					builder);
		}
	}
	
	@SuppressWarnings("unchecked")
	protected <X extends AbstractDbObject<?>,Y extends AbstractSqlBuilder<?>> AddTableObjectDetailFactory<X, Y> getAddTableObjectDetailOperationFactory(X obj){
		SqlFactory<X> factory = getSqlFactoryRegistry()
				.getSqlFactory(obj, SqlType.CREATE);
		if (factory instanceof AddTableObjectDetailFactory<?,?>) {
			return (AddTableObjectDetailFactory<X, Y>)factory;
		}
		return null;
	}
}
