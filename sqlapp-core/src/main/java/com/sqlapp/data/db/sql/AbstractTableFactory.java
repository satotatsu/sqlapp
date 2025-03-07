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

import java.util.List;

import com.sqlapp.data.db.datatype.DbDataType;
import com.sqlapp.data.schemas.AbstractDbObject;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Index;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.UniqueConstraint;
import com.sqlapp.util.AbstractSqlBuilder;
import com.sqlapp.util.CommonUtils;

/**
 * TABLE関係の生成クラス
 * 
 * @author satoh
 * 
 */
public abstract class AbstractTableFactory<S extends AbstractSqlBuilder<?>>
		extends SimpleSqlFactory<Table, S> {

	protected void addSelectAllColumns(final Table obj,
			final S builder) {
		if (this.getOptions().getTableOptions().getSelectAllColumnASAsterisk().test(obj)) {
			builder.lineBreak();
			builder._add("*");
			builder.lineBreak();
		} else{
			boolean first=true;
			for(final Column column:obj.getColumns()) {
				builder.lineBreak();
				builder.comma(!first);
				builder.name(column);
				this.addSelectColumnComment(column, builder);
				first=false;
			}
			builder.lineBreak();
		}
	}

	/**
	 * ユニークカラムの検索条件を追加します
	 * 
	 * @param table
	 * @param builder
	 */
	protected void addUniqueColumnsCondition(final Table table,
			final S builder) {
		builder.setQuateObjectName(this.getOptions().isQuateColumnName());
		final List<Column> columns = table.getUniqueColumns();
		builder.indent(()->{
			if (CommonUtils.isEmpty(columns)) {
				for (final Column column : table.getColumns()) {
					builder.lineBreak();
					builder.and().name(column);
					this.addWhereColumnComment(column, builder);
					builder.space().eq().space()._add(getValueDefinitionSimple(column));
				}
			} else {
				for (final Column column : columns) {
					builder.lineBreak();
					builder.and().name(column);
					this.addWhereColumnComment(column, builder);
					builder.space().eq().space()._add(getValueDefinitionSimple(column));
				}
			}
		});
		builder.setQuateObjectName(false);
	}
	
	/**
	 * 楽観的ロックカラムの検索条件を追加します
	 * 
	 * @param table
	 * @param builder
	 */
	protected void addLockVersionColumnCondition(final Table table,
			final S builder) {
		builder.indent(()->{
			for(final Column column:table.getColumns()){
				if (isOptimisticLockColumn(column)){
					builder.lineBreak();
					builder.and().name(column);
					this.addWhereColumnComment(column, builder);
					final String value=this.getOptimisticLockColumnCondition(column);
					builder.space().eq().space()._add(value);
					break;
				}
			}
		});
	}

	/**
	 * 全カラムの条件を追加します。
	 * @param obj
	 * @param builder
	 */
	protected void addConditionColumns(final Table obj, final S builder) {
		builder.indent(()->{
			for(final Column column:obj.getColumns()){
				addConditions(column, builder);
			}
		});
	}

	protected void addConditions(final Column column, final S builder) {
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

	protected TableLockMode getLockMode(final Table table){
		return this.getOptions().getTableOptions().getLockMode().apply(table);
	}
	
	protected void addConditionIn(final Column column, final S builder) {
		builder.lineBreak()._add(toIfIsNotEmptyExpression(column.getName()));
		builder.lineBreak();
		builder.and().space().name(column);
		this.addWhereColumnComment(column, builder);
		builder.space().in().space().addComment(column.getName());
		addConditionInValue(column, builder);
		builder.lineBreak()._add(getEndIfExpression());
	}

	protected void addConditionNotIn(final Column column, final S builder) {
		builder.lineBreak()._add(toIfIsNotEmptyExpression(column.getName()+ "_neq"));
		builder.lineBreak();
		builder.and().space().name(column);
		this.addWhereColumnComment(column, builder);
		builder.space().not().in().space().addComment(column.getName()+"_neq");
		addConditionInValue(column, builder);
		builder.lineBreak()._add(getEndIfExpression());
	}

	protected String toIfExpression(final String column) {
		return this.getOptions().getTableOptions().getIfStartExpression().apply(column);
	}

	protected String toIfIsNotEmptyExpression(final String column) {
		return this.getOptions().getTableOptions().getIfStartExpression().apply(toIsNotEmptyExpression(column));
	}

	protected String toIsNotEmptyExpression(final String column) {
		return this.getOptions().getTableOptions().getIsNotEmptyExpression().apply(column);
	}

	protected String getEndIfExpression() {
		return this.getOptions().getTableOptions().getEndIfExpression().get();
	}

	protected void addConditionContains(final Column column, final S builder) {
		builder.lineBreak()._add(toIfIsNotEmptyExpression(column.getName()+ "_contains"));
		builder.lineBreak();
		builder.and().space().name(column);
		this.addWhereColumnComment(column, builder);
		builder.space().like().space().addComment("'%' + "+column.getName()+"_contains + '%'");
		addConditionValue(column, builder);
		builder.lineBreak()._add(getEndIfExpression());
	}
	
	protected void addConditionStartsWith(final Column column, final S builder) {
		builder.lineBreak()._add(toIfIsNotEmptyExpression(column.getName()+ "_startsWith"));
		builder.lineBreak();
		builder.and().space().name(column);
		this.addWhereColumnComment(column, builder);
		builder.space().like().space().addComment(column.getName()+"_startsWith + '%'");
		addConditionValue(column, builder);
		builder.lineBreak()._add(getEndIfExpression());
	}

	protected void addConditionEndsWith(final Column column, final S builder) {
		builder.lineBreak()._add(toIfIsNotEmptyExpression(column.getName()+ "_endsWith"));
		builder.lineBreak();
		builder.and().space().name(column);
		this.addWhereColumnComment(column, builder);
		builder.space().like().space().addComment("'%' + "+column.getName()+"_endsWith");
		addConditionValue(column, builder);
		builder.lineBreak()._add(getEndIfExpression());
	}

	protected void addConditionGt(final Column column, final S builder) {
		builder.lineBreak()._add(toIfIsNotEmptyExpression(column.getName()+ "_gt"));
		builder.lineBreak();
		builder.and().space().name(column);
		this.addWhereColumnComment(column, builder);
		builder.space().gt().space().addComment(column.getName()+"_gt");
		addConditionValue(column, builder);
		builder.lineBreak()._add(getEndIfExpression());
	}

	protected void addConditionLt(final Column column, final S builder) {
		builder.lineBreak()._add(toIfIsNotEmptyExpression(column.getName()+ "_lt"));
		builder.lineBreak();
		builder.and().space().name(column).space().lt().space().addComment(column.getName()+"_lt");
		addConditionValue(column, builder);
		builder.lineBreak()._add(getEndIfExpression());
	}

	protected void addConditionGte(final Column column, final S builder) {
		builder.lineBreak()._add(toIfIsNotEmptyExpression(column.getName()+ "_gte"));
		builder.lineBreak();
		builder.and().space().name(column).space().gte().space().addComment(column.getName()+"_gte");
		addConditionValue(column, builder);
		builder.lineBreak()._add(getEndIfExpression());
	}

	protected void addConditionLte(final Column column, final S builder) {
		builder.lineBreak()._add(toIfIsNotEmptyExpression(column.getName()+ "_lte"));
		builder.lineBreak();
		builder.and().space().name(column).space().lte().space().addComment(column.getName()+"_lte");
		addConditionValue(column, builder);
		builder.lineBreak()._add(getEndIfExpression());
	}

	protected void addConditionInValue(final Column column, final S builder) {
		if (column.getDataType()==null){
			builder._add("('')");
		} else{
			builder._add("(");
			builder._add(getDefaultValueLiteral(column));
			builder._add(")");
		}
	}

	protected void addConditionValue(final Column column, final S builder) {
		if (column.getDataType()==null){
			builder._add("''");
		} else{
			builder._add(getDefaultValueLiteral(column));
		}
	}

	protected String getDefaultValueLiteral(final Column column) {
		final DbDataType<?> dbDataType = this.getDialect().getDbDataType(column);
		return dbDataType.getDefaultValueLiteral();
	}
	
	protected void addCreateIndexDefinition(final Index index, final S builder) {
		if (index == null) {
			return;
		}
		final AddTableObjectDetailFactory<Index, AbstractSqlBuilder<?>> sqlFactory
			=getAddTableObjectDetailOperationFactory(index);
		if (sqlFactory!=null) {
			builder.create();
			sqlFactory.addObjectDetail(index, index.getTable(),
					builder);
		}
	}
	
	@SuppressWarnings("unchecked")
	protected <X extends AbstractDbObject<?>,Y extends AbstractSqlBuilder<?>> AddTableObjectDetailFactory<X, Y> getAddTableObjectDetailOperationFactory(final X obj){
		final SqlFactory<X> factory = getSqlFactoryRegistry()
				.getSqlFactory(obj, SqlType.CREATE);
		if (factory instanceof AddTableObjectDetailFactory<?,?>) {
			return (AddTableObjectDetailFactory<X, Y>)factory;
		}
		return null;
	}

	
	protected UniqueConstraint getUniqueConstraint(final Table table) {
		UniqueConstraint constraint=table.getConstraints().getPrimaryKeyConstraint();
		if (constraint==null){
			constraint=CommonUtils.first(table.getConstraints().getUniqueConstraints());
		}
		return constraint;
	}

}
