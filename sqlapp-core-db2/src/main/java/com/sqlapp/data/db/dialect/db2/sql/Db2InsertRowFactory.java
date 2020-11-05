/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-db2.
 *
 * sqlapp-core-db2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-db2 is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-db2.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.sqlapp.data.db.dialect.db2.sql;

import java.util.Collection;
import java.util.List;

import com.sqlapp.data.db.dialect.db2.util.Db2SqlBuilder;
import com.sqlapp.data.db.sql.AbstractInsertRowFactory;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Row;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.util.CommonUtils;

public class Db2InsertRowFactory extends AbstractInsertRowFactory<Db2SqlBuilder>{
	
	@Override
	protected List<SqlOperation> getOperations(Table table, final Collection<Row> rows){
		List<SqlOperation> sqlList = CommonUtils.list();
		if (rows==null){
			return sqlList;
		}
		Row firstRow=CommonUtils.first(rows);
		Db2SqlBuilder builder = createSqlBuilder();
		builder.insert().into();
		builder.space().name(table, this.getOptions().isDecorateSchemaName());
		builder.lineBreak()._add("(");
		builder.appendIndent(1);
		boolean[] first=new boolean[]{true};
		for(Column column:table.getColumns()){
			String def=this.getValueDefinitionForInsert(firstRow, column);
			builder.$if(!CommonUtils.isEmpty(def), ()->{
				builder.comma(!first[0]).name(column);
				first[0]=false;
			});
		}
		builder.appendIndent(-1);
		builder.space()._add(")");
		builder.lineBreak();
		builder.values();
		for(Row row:rows){
			first[0]=true;
			builder.lineBreak().comma(row!=firstRow).space(row!=firstRow)._add("(");
			builder.appendIndent(1);
			for(Column column:row.getTable().getColumns()){
				String def=this.getValueDefinitionForInsert(row, column);
				builder.$if(!CommonUtils.isEmpty(def), ()->{
					builder.comma(!first[0])._add(def);
					first[0]=false;
				});
			}
			builder.appendIndent(-1);
			builder._add(")");
		}
		addSql(sqlList, builder, SqlType.INSERT_ROW, CommonUtils.list(rows));
		return sqlList;
	}

}
