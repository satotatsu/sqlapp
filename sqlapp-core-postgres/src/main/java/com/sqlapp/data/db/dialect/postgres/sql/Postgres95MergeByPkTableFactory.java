/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
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
 * along with sqlapp-core-postgres.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.sqlapp.data.db.dialect.postgres.sql;

import static com.sqlapp.util.CommonUtils.list;

import java.util.List;

import com.sqlapp.data.db.dialect.postgres.util.PostgresSqlBuilder;
import com.sqlapp.data.db.sql.AbstractMergeByPkTableFactory;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.UniqueConstraint;
import com.sqlapp.util.CommonUtils;

public class Postgres95MergeByPkTableFactory extends AbstractMergeByPkTableFactory<PostgresSqlBuilder>{


	@Override
	public List<SqlOperation> createSql(final Table table) {
		List<SqlOperation> sqlList = list();
		UniqueConstraint constraint=getUniqueConstraint(table);
		if (constraint==null){
			return super.createSql(table);
		}
		PostgresSqlBuilder builder = createSqlBuilder();
		builder.insert().into().space().name(table, this.getOptions().isDecorateSchemaName());
		this.addTableComment(table, builder);
		boolean[] first=new boolean[]{true};
		builder.lineBreak();
		builder.brackets(true, ()->{
			for(Column column:table.getColumns()){
				String def=this.getValueDefinitionForInsert(column);
				builder.$if(!CommonUtils.isEmpty(def), ()->{
					builder.lineBreak(!first[0]).comma(!first[0]).name(column);
					addInsertColumnComment(column, builder);
					first[0]=false;
				});
			}
		});
		builder.lineBreak();
		builder.values();
		builder.lineBreak();
		builder.brackets(true, ()->{
			first[0]=true;
			for(Column column:table.getColumns()){
				String def=this.getValueDefinitionForInsert(column);
				builder.$if(!CommonUtils.isEmpty(def), ()->{
					builder.lineBreak();
					builder.comma(!first[0])._add(def);
					first[0]=false;
				});
			}
		});
		builder.lineBreak().on().conflict().on().constraint().name(constraint, false);
		builder.lineBreak()._do().update();
		first[0]=true;
		builder.indent(()->{
			for(Column column:table.getColumns()){
				if (constraint.getColumns().contains(column.getName())){
					continue;
				}
				String def=this.getValueDefinitionForUpdate("EXCLUDED.", column);
				builder.$if(!CommonUtils.isEmpty(def), ()->{
					builder.lineBreak().set(first[0]).comma(!first[0]).name(column);
					addUpdateColumnComment(column, builder);
					builder.space().eq().space()._add(def);
					first[0]=false;
				});
			}
		});
		addSql(sqlList, builder, SqlType.MERGE_BY_PK, table);
		return sqlList;
	}


}
