/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-mysql.
 *
 * sqlapp-core-mysql is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-mysql is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-mysql.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.sqlapp.data.db.dialect.mysql.sql;

import java.util.Collection;
import java.util.List;

import com.sqlapp.data.db.dialect.mysql.util.MySqlSqlBuilder;
import com.sqlapp.data.db.sql.AbstractMergeRowFactory;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Row;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.UniqueConstraint;
import com.sqlapp.util.CommonUtils;

public class MySqlMergeRowFactory extends AbstractMergeRowFactory<MySqlSqlBuilder>{

	@Override
	protected List<SqlOperation> getOperations(Table table, final Collection<Row> rows){
		List<SqlOperation> sqlList = CommonUtils.list();
		UniqueConstraint constraint=table.getConstraints().getPrimaryKeyConstraint();
		if (constraint==null){
			constraint=CommonUtils.first(table.getConstraints().getUniqueConstraints());
		}
		if (constraint==null){
			for(Row row:rows){
				sqlList.addAll(super.getOperations(row));
			}
			return sqlList;
		}
		Row firstRow=CommonUtils.first(rows);
		MySqlSqlBuilder builder = createSqlBuilder();
		builder.insert().into().space().name(table, this.getOptions().isDecorateSchemaName());
		builder.space()._add('(');
		boolean[] first=new boolean[]{true};
		for(Column column:table.getColumns()){
			String def=this.getValueDefinitionForInsert(firstRow, column);
			builder.$if(!CommonUtils.isEmpty(def), ()->{
				if (!isFormulaColumn(column)) {
					builder.comma(!first[0]).name(column);
					first[0]=false;
				}
			});
		}
		builder.space()._add(')');
		builder.lineBreak();
		builder.values();
		boolean isFirstRow=true;
		for(Row row:rows){
			builder.space().comma(!isFirstRow)._add('(');
			first[0]=true;
			for(Column column:table.getColumns()){
				String def=this.getValueDefinitionForInsert(row, column);
				builder.$if(!CommonUtils.isEmpty(def), ()->{
					if (!isFormulaColumn(column)) {
						builder.comma(!first[0])._add(def);
						first[0]=false;
					}
				});
			}
			builder.space()._add(')');
			isFirstRow=false;
		}
		builder.lineBreak();
		builder.on().duplicate().key().update();
		MySqlSqlBuilder childBuilder=builder.clone()._clear();
		first[0]=true;
		for(Column column:table.getColumns()){
			if (constraint.getColumns().contains(column.getName())){
				continue;
			}
			if (!isFormulaColumn(column)) {
				String def=this.getValueDefinitionForUpdate(firstRow, column);
				if (this.isOptimisticLockColumn(column)||this.isUpdatedAtColumn(column)){
					if (!CommonUtils.isEmpty(def)){
						childBuilder.comma(!first[0]).name(column).eq();
						childBuilder._add(def);
						first[0]=false;
						continue;
					}
				}
				if (!CommonUtils.isEmpty(def)){
					first[0]=addUpdateValue(column, first[0], childBuilder);
				}
			}
		}
		if (first[0]){
			first[0]=true;
			for(Column column:table.getColumns()){
				if (!isFormulaColumn(column)) {
					String def=this.getValueDefinitionForUpdate(firstRow, column);
					if (this.isOptimisticLockColumn(column)||this.isUpdatedAtColumn(column)){
						if (!CommonUtils.isEmpty(def)){
							builder.comma(!first[0]).name(column).eq();
							builder._add(def);
							first[0]=false;
							continue;
						}
					}
					if (!CommonUtils.isEmpty(def)){
						first[0]=addUpdateValue(column, first[0], builder);
					}
				}
			}
		} else{
			builder.space()._merge(childBuilder);
		}
		addSql(sqlList, builder, SqlType.MERGE_ROW, CommonUtils.list(rows));
		return sqlList;
	}
	
	protected boolean addUpdateValue(Column column, boolean first, MySqlSqlBuilder builder){
		builder.comma(!first).name(column).eq();
		if(this.withCoalesceAtUpdate(column)){
			builder.coalesce()._add('(', ()->{
				builder.name(column).comma();
				builder.values()._add('(', ()->{
					builder.name(column).space();
				}, ')');
			}, ')');
		} else{
			builder.values()._add('(', ()->{
				builder.name(column).space();
			}, ')');
		}
		return false;
	}

}
