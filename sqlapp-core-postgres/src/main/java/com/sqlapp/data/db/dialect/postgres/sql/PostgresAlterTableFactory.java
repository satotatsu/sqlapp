/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-postgres.
 *
 * sqlapp-core-postgres is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-postgres is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-postgres.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.postgres.sql;

import java.util.List;
import java.util.Map;

import com.sqlapp.data.converter.Converters;
import com.sqlapp.data.db.dialect.postgres.util.PostgresSqlBuilder;
import com.sqlapp.data.db.sql.AbstractAlterTableFactory;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Constraint;
import com.sqlapp.data.schemas.DbObjectDifference;
import com.sqlapp.data.schemas.DbObjectDifferenceCollection;
import com.sqlapp.data.schemas.DbObjectPropertyDifference;
import com.sqlapp.data.schemas.Difference;
import com.sqlapp.data.schemas.Index;
import com.sqlapp.data.schemas.SchemaObjectProperties;
import com.sqlapp.data.schemas.SchemaProperties;
import com.sqlapp.data.schemas.Table;

/**
 * Postgresテーブル生成クラス
 * 
 * @author satoh
 * 
 */
public class PostgresAlterTableFactory extends
		AbstractAlterTableFactory<PostgresSqlBuilder> {

	@Override
	protected void addAddColumn(Table originalTable, Table table, DbObjectDifference diff,List<SqlOperation> result){
		Column column = diff.getTarget(Column.class);
		PostgresSqlBuilder builder = createSqlBuilder();
		String _default=column.getDefaultValue();
		boolean notNull=column.isNotNull();
		try{
			if (column.isNotNull()){
				column.setDefaultValue(null);
				column.setNotNull(false);
			}
			builder.alter().table();
			builder.name(table, this.getOptions().isDecorateSchemaName());
			this.addTableComment(table, builder);
			builder.add().column();
			builder.name(column);
			builder.space().definitionForAlterColumn(column);
			add(result, createOperation(builder.toString(), SqlType.ALTER, null, column));
			if (notNull){
				if (_default!=null){
					builder = createSqlBuilder();
					builder.alter().table();
					builder.name(table, this.getOptions().isDecorateSchemaName());
					builder.alter().column();
					builder.name(column);
					builder.set()._default().space()._add(_default);
					add(result, createOperation(builder.toString(), SqlType.ALTER, null, column));
					//
					builder = createSqlBuilder();
					builder.update().space().name(table, this.getOptions().isDecorateSchemaName());
					builder.lineBreak();
					builder.set().space().name(column).eq().space()._add(_default);
					builder.lineBreak();
					builder.where().space().name(column).is()._null();
					add(result, createOperation(builder.toString(), SqlType.UPDATE, null, column));
				}
				//
				builder = createSqlBuilder();
				builder.alter().table();
				builder.name(table, this.getOptions().isDecorateSchemaName());
				builder.alter().column();
				builder.name(column);
				builder.set().not()._null();
				add(result, createOperation(builder.toString(), SqlType.ALTER, null, column));
			}
		} finally{
			column.setDefaultValue(_default);
			column.setNotNull(notNull);
		}
	}
	
	@Override
	protected void addAlterColumn(Table originalTable, Table table, Column oldColumn, Column column, DbObjectDifference diff,List<SqlOperation> result){
		boolean changeNotNull=diff.getChangedProperties().containsKey(SchemaProperties.NOT_NULL.getLabel());
		boolean changeDefault=diff.getChangedProperties().containsKey(SchemaProperties.DEFAULT_VALUE.getLabel());
		String _default=column.getDefaultValue();
		boolean notNull=column.isNotNull();
		try{
			if (column.isNotNull()){
				column.setDefaultValue(null);
				column.setNotNull(false);
			}
			PostgresSqlBuilder builder = createSqlBuilder();
			builder.alter().table();
			builder.name(table, this.getOptions().isDecorateSchemaName());
			builder.alterColumn();
			builder.name(column);
			builder.space().definitionForAlterColumn(column);
			add(result, createOperation(builder.toString(), SqlType.ALTER, oldColumn, column));
			if (changeDefault){
				builder = createSqlBuilder();
				builder.alter().table();
				builder.name(table, this.getOptions().isDecorateSchemaName());
				builder.alter().column();
				builder.name(column);
				if (_default!=null){
					builder.set()._default().space()._add(_default);
					add(result, createOperation(builder.toString(), SqlType.ALTER, null, column));
					if (notNull){
						//
						builder = createSqlBuilder();
						builder.update().space().name(table, this.getOptions().isDecorateSchemaName());
						builder.lineBreak();
						builder.set().space().name(column).eq().space()._add(_default);
						builder.lineBreak();
						builder.where().space().name(column).is()._null();
						add(result, createOperation(builder.toString(), SqlType.UPDATE, null, column));
					}
				} else{
					builder.drop()._default();
					add(result, createOperation(builder.toString(), SqlType.ALTER, null, column));
				}
			}
			if (changeNotNull){
				builder = createSqlBuilder();
				builder.alter().table();
				builder.name(table, this.getOptions().isDecorateSchemaName());
				builder.alter().column();
				if (notNull){
					builder.set().not()._null();
					add(result, createOperation(builder.toString(), SqlType.ALTER, null, column));
				} else{
					builder.drop().not()._null();
					add(result, createOperation(builder.toString(), SqlType.ALTER, null, column));
				}
			}
		} finally{
			column.setDefaultValue(_default);
			column.setNotNull(notNull);
		}
	}
	
	@Override
	protected void addOtherDefinitions(Map<String, Difference<?>> allDiff
			, Table originalTable, Table table, List<SqlOperation> result){
		addPropertiesDefinitions(allDiff, originalTable,table, result);
		addCommentDefinitions(allDiff, originalTable,table, result);
	}

	protected void addPropertiesDefinitions(Map<String, Difference<?>> allDiff
			, Table originalTable, Table table, List<SqlOperation> result){
		Difference<?> diff=allDiff.get("oids");
		if (diff!=null){
			PostgresSqlBuilder builder=this.createSqlBuilder();
			builder.alter().table();
			builder.name(table, this.getOptions().isDecorateSchemaName());
			Boolean bool=Converters.getDefault().convertObject(diff.getTarget(), Boolean.class);
			if (bool!=null&&bool.booleanValue()){
				builder.set().with().oids();
			} else{
				builder.set().without().oids();
			}
			addSql(result, builder, SqlType.ALTER, table);
		}
	}
	
	protected void addCommentDefinitions(Map<String, Difference<?>> allDiff
			, Table originalTable, Table table, List<SqlOperation> result){
		Difference<?> tableProp = allDiff.get(SchemaProperties.REMARKS.getLabel());
		if (tableProp!=null&&tableProp.getState().isChanged()){
			PostgresSqlBuilder builder=this.createSqlBuilder();
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
				PostgresSqlBuilder builder=this.createSqlBuilder();
				builder.comment().on().column().space().columnName(obj, true, this.getOptions().isDecorateSchemaName()).is()
				.$if(obj.getRemarks()!=null, ()->builder.sqlChar(obj.getRemarks()), ()->builder.is()._null());
				addSql(result, builder, SqlType.SET_COMMENT, obj);
			}
		}
		DbObjectDifferenceCollection indexDiff = (DbObjectDifferenceCollection) allDiff
				.get(SchemaObjectProperties.INDEXES.getLabel());
		if (indexDiff != null) {
			List<DbObjectPropertyDifference> diffs=indexDiff.findModifiedProperties(this.getDialect(), SchemaProperties.REMARKS.getLabel(), Index.class);
			for (DbObjectPropertyDifference diff : diffs) {
				Index obj=diff.getTarget(Index.class);
				PostgresSqlBuilder builder=this.createSqlBuilder();
				builder.comment().on().index().space().name(obj, this.getOptions().isDecorateSchemaName()).is()
				.$if(obj.getRemarks()!=null, ()->builder.sqlChar(obj.getRemarks()), ()->builder.is()._null());
				addSql(result, builder, SqlType.SET_COMMENT, obj);
			}
		}
		DbObjectDifferenceCollection consDiff = (DbObjectDifferenceCollection) allDiff
				.get(SchemaObjectProperties.CONSTRAINTS.getLabel());
		if (consDiff != null) {
			List<DbObjectPropertyDifference> diffs=consDiff.findModifiedProperties(this.getDialect(), SchemaProperties.REMARKS.getLabel(), Constraint.class);
			for (DbObjectPropertyDifference diff : diffs) {
				Constraint obj=diff.getTarget(Constraint.class);
				PostgresSqlBuilder builder=this.createSqlBuilder();
				builder.comment().on().constraint().space().name(obj, this.getOptions().isDecorateSchemaName()).on().name(table, this.getOptions().isDecorateSchemaName()).is()
					.$if(obj.getRemarks()!=null, ()->builder.sqlChar(obj.getRemarks()), ()->builder.is()._null());
				addSql(result, builder, SqlType.SET_COMMENT, obj);
			}
		}
	}
}
