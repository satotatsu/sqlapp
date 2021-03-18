/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-sqlserver.
 *
 * sqlapp-core-sqlserver is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-sqlserver is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-sqlserver.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.sqlapp.data.db.dialect.sqlserver.sql;

import static com.sqlapp.util.CommonUtils.list;

import java.util.Collection;
import java.util.List;

import com.sqlapp.data.db.dialect.sqlserver.util.SqlServerSqlBuilder;
import com.sqlapp.data.db.sql.AbstractMergeRowFactory;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Row;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.UniqueConstraint;
import com.sqlapp.util.CommonUtils;

public class SqlServer2008MergeRowFactory extends AbstractMergeRowFactory<SqlServerSqlBuilder>{
	
	@Override
	protected List<SqlOperation> getOperations(final Table table, final Collection<Row> rows){
		final List<SqlOperation> sqlList = list();
		UniqueConstraint constraint=table.getConstraints().getPrimaryKeyConstraint();
		if (constraint==null){
			constraint=CommonUtils.first(table.getConstraints().getUniqueConstraints());
		}
		if (constraint==null){
			for(final Row row:rows){
				sqlList.addAll(super.getOperations(row));
			}
			return sqlList;
		}
		final Row firstRow=CommonUtils.first(rows);
		final String targetTable=this.getOptions().getTableOptions().getTemporaryAlias().apply(table);
		final SqlServerSqlBuilder builder = createSqlBuilder();
		builder.merge().space().name(table, this.getOptions().isDecorateSchemaName());
		builder.lineBreak();
		builder.using();
		builder.lineBreak();
		final boolean[] first=new boolean[]{true};
		builder.brackets(()->{
			builder.indent(()->{
				for(final Row row:rows){
					builder.lineBreak();
					if (row!=firstRow){
						builder.union().all();
						builder.lineBreak();
					}
					builder.select().space();
					first[0]=true;
					for(final Column column:table.getColumns()){
						final String def=this.getValueDefinitionForInsert(row, column);
						builder.$if(!CommonUtils.isEmpty(def), ()->{
							if (!this.isFormulaColumn(column)) {
								builder.comma(!first[0])._add(def).as().name(column);
								first[0]=false;
							}
						});
					}
				}
			});
			builder.lineBreak();
		});
		builder.lineBreak().as().space()._add(targetTable);
		builder.lineBreak();
		builder.on();
		first[0]=true;
		for(final Column column:table.getColumns()){
			if (!constraint.getColumns().contains(column.getName())){
				continue;
			}
			builder.and(!first[0]).columnName(column, true).eq().names(targetTable, column.getName());
			first[0]=false;
		}
		final SqlServerSqlBuilder childBuilder=builder.clone()._clear();
		childBuilder.lineBreak();
		childBuilder.when().matched().then();
		childBuilder.appendIndent(1);
		childBuilder.lineBreak();
		childBuilder.update().set();
		first[0]=true;
		for(final Column column:table.getColumns()){
			if (constraint.getColumns().contains(column.getName())){
				continue;
			}
			if (!isUpdateable(column)) {
				continue;
			}
			final String def=this.getValueDefinitionForUpdate(firstRow, column);
			if (!this.isFormulaColumn(column)) {
				childBuilder.lineBreak().comma(!first[0]).name(column).eq();
				if (this.isOptimisticLockColumn(column)){
					childBuilder._add(def);
				} else{
					if (this.withCoalesceAtUpdate(column)){
						childBuilder.coalesce(()->{
							childBuilder.names(column.getName()).comma();
							childBuilder.names(targetTable, column.getName()).space();
						});
					} else{
						childBuilder.names(targetTable, column.getName());
					}
				}
				first[0]=false;
			}
		}
		childBuilder.appendIndent(-1);
		if (!first[0]){
			builder._merge(childBuilder);
		}
		final List<Column> insertableColumns=CommonUtils.list();
		builder.lineBreak();
		builder.when().not().matched().then();
		builder.appendIndent(1);
		builder.lineBreak();
		builder.insert();
		builder.lineBreak().brackets(()->{
			builder.indent(()->{
				first[0]=true;
				for(final Column column:table.getColumns()){
					if (!isInsertable(column)) {
						continue;
					}
					final String def=this.getValueDefinitionForInsert(firstRow, column);
					builder.$if(!CommonUtils.isEmpty(def), ()->{
						if (!this.isFormulaColumn(column)) {
							builder.lineBreak().comma(!first[0]).name(column);
							insertableColumns.add(column);
							first[0]=false;
						}
					});
				}
			});
			builder.lineBreak();
		});
		builder.lineBreak().values();
		builder.lineBreak().brackets(()->{
			builder.indent(()->{
				first[0]=true;
				for(final Column column:insertableColumns){
					final String def=this.getValueDefinitionForInsert(firstRow, column);
					builder.$if(!CommonUtils.isEmpty(def), ()->{
						if (!this.isFormulaColumn(column)) {
							builder.lineBreak().comma(!first[0]).names(targetTable, column.getName());
							first[0]=false;
						}
					});
				}
			});
			builder.lineBreak();
		});
		addSql(sqlList, builder, SqlType.MERGE_ROW, CommonUtils.list(rows));
		return sqlList;
	}

}
