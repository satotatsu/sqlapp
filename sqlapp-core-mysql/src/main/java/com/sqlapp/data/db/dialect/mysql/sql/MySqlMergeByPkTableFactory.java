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

import static com.sqlapp.util.CommonUtils.list;

import java.util.List;

import com.sqlapp.data.db.dialect.mysql.util.MySqlSqlBuilder;
import com.sqlapp.data.db.sql.AbstractMergeByPkTableFactory;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.UniqueConstraint;
import com.sqlapp.util.CommonUtils;

public class MySqlMergeByPkTableFactory extends AbstractMergeByPkTableFactory<MySqlSqlBuilder>{

	@Override
	public List<SqlOperation> createSql(final Table table) {
		List<SqlOperation> sqlList = list();
		UniqueConstraint constraint=table.getConstraints().getPrimaryKeyConstraint();
		if (constraint==null){
			constraint=CommonUtils.first(table.getConstraints().getUniqueConstraints());
		}
		if (constraint==null){
			return super.createSql(table);
		}
		MySqlSqlBuilder builder = createSqlBuilder();
		builder.insert().into().space().name(table, this.getOptions().isDecorateSchemaName());
		builder.space()._add('(');
		boolean[] first=new boolean[]{true};
		for(Column column:table.getColumns()){
			String def=this.getValueDefinitionForInsert(column);
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
		builder.space()._add('(');
		first[0]=true;
		for(Column column:table.getColumns()){
			String def=this.getValueDefinitionForInsert(column);
			builder.$if(!CommonUtils.isEmpty(def), ()->{
				if (!isFormulaColumn(column)) {
					builder.comma(!first[0])._add(def);
					first[0]=false;
				}
			});
		}
		builder.space()._add(')');
		builder.lineBreak();
		builder.on().duplicate().key().update();
		first[0]=true;
		for(Column column:table.getColumns()){
			if (constraint.getColumns().contains(column.getName())){
				continue;
			}
			if (!isFormulaColumn(column)) {
				String def=this.getValueDefinitionForUpdate(column);
				if (this.isOptimisticLockColumn(column)){
					if (this.withCoalesceAtUpdate(column)){
						builder.comma(!first[0]).name(column).eq().coalesce()._add('(').values().space()._add('(');
						builder.name(column).space()._add("), ");
						builder._add(getDefaultValueDefinition(column));
						builder._add(") + 1");
						first[0]=false;
						continue;
					} else{
						builder.comma(!first[0]).name(column).eq().coalesce()._add('(');
						builder.name(column).space().comma();
						builder._add(getDefaultValueDefinition(column));
						builder._add(" ) + 1");
						first[0]=false;
						continue;
					}
				} else if (this.isUpdatedAtColumn(column)){
					builder.comma(!first[0]).name(column).eq()._add(def);
					first[0]=false;
					continue;
				}
				if (def!=null){
					builder.comma(!first[0]).name(column).eq().values()._add('(');
					builder.name(column).space()._add(')');
					first[0]=false;
				}
			}
		}
		addSql(sqlList, builder, SqlType.MERGE_BY_PK, table);
		return sqlList;
	}

}
