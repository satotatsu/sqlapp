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
import com.sqlapp.data.db.sql.AbstractMergeRowFactory;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Row;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.UniqueConstraint;
import com.sqlapp.util.CommonUtils;

public class Postgres95MergeRowFactory extends AbstractMergeRowFactory<PostgresSqlBuilder>{

	@Override
	protected List<SqlOperation> getOperations(final Row row) {
		List<SqlOperation> sqlList = list();
		Table table=row.getTable();
		UniqueConstraint constraint=table.getConstraints().getPrimaryKeyConstraint();
		if (constraint==null){
			constraint=CommonUtils.first(table.getConstraints().getUniqueConstraints());
		}
		if (constraint==null){
			return super.getOperations(row);
		}
		PostgresSqlBuilder builder = createSqlBuilder();
		builder.insert().into().space().name(table, this.getOptions().isDecorateSchemaName());
		builder.space()._add('(');
		boolean[] first=new boolean[]{true};
		for(Column column:table.getColumns()){
			String def=this.getValueDefinitionForInsert(row, column);
			builder.$if(!CommonUtils.isEmpty(def), ()->{
				builder.comma(!first[0]).name(column);
				first[0]=false;
			});
		}
		builder.space()._add(')');
		builder.space().values();
		builder.space()._add('(');
		first[0]=true;
		for(Column column:table.getColumns()){
			String def=this.getValueDefinitionForInsert(row, column);
			builder.$if(!CommonUtils.isEmpty(def), ()->{
				builder.comma(!first[0])._add(def);
				first[0]=false;
			});
		}
		builder.space()._add(')');
		builder.space().on().conflict().on().constraint().name(constraint, false);
		builder._do().update();
		builder.space()._add('(');
		first[0]=true;
		for(Column column:table.getColumns()){
			if (constraint.getColumns().contains(column.getName())){
				continue;
			}
			String def=this.getValueDefinitionForUpdate(row, column);
			builder.$if(!CommonUtils.isEmpty(def), ()->{
				builder.comma(!first[0])._add(def);
				first[0]=false;
			});
		}
		builder._add(')');
		addSql(sqlList, builder, SqlType.MERGE_ROW, row);
		return sqlList;
	}

}
