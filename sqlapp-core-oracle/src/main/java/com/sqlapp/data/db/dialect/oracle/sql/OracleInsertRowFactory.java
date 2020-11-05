/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-oracle.
 *
 * sqlapp-core-oracle is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-oracle is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with sqlapp-core-oracle.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.sqlapp.data.db.dialect.oracle.sql;

import java.util.Collection;
import java.util.List;

import com.sqlapp.data.db.dialect.oracle.util.OracleSqlBuilder;
import com.sqlapp.data.db.sql.AbstractInsertRowFactory;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Row;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.util.CommonUtils;

public class OracleInsertRowFactory extends AbstractInsertRowFactory<OracleSqlBuilder>{
	
	@Override
	protected List<SqlOperation> getOperations(Table table, final Collection<Row> rows){
		List<SqlOperation> sqlList = CommonUtils.list();
		if (rows==null){
			return sqlList;
		}
		OracleSqlBuilder builder = createSqlBuilder();
		builder.insert().all();
		builder.appendIndent(1);
		boolean[] first=new boolean[]{true};
		for(Row row:rows){
			builder.lineBreak();
			builder.into().space().name(table, this.getOptions().isDecorateSchemaName());
			builder.using().space()._add("(");
			first[0]=true;
			for(Column column:row.getTable().getColumns()){
				String def=this.getValueDefinitionForInsert(row, column);
				builder.$if(!CommonUtils.isEmpty(def), ()->{
					if (!isFormulaColumn(column)) {
						builder.comma(!first[0]).name(column);
						first[0]=false;
					}
				});
			}
			builder._add(")");
			builder.values().space()._add("(");
			first[0]=true;
			for(Column column:row.getTable().getColumns()){
				String def=this.getValueDefinitionForInsert(row, column);
				builder.$if(!CommonUtils.isEmpty(def), ()->{
					if (!isFormulaColumn(column)) {
						builder.comma(!first[0])._add(def);
						first[0]=false;
					}
				});
			}
			builder._add(")");
		}
		addSql(sqlList, builder, SqlType.INSERT_ROW, CommonUtils.list(rows));
		return sqlList;
	}

}
