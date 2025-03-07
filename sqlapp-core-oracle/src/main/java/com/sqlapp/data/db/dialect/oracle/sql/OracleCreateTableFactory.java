/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-oracle.
 *
 * sqlapp-core-oracle is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-oracle is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-oracle.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.oracle.sql;

import java.util.List;
import java.util.Map;

import com.sqlapp.data.db.dialect.oracle.util.OracleSqlBuilder;
import com.sqlapp.data.db.sql.AbstractCreateTableFactory;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.util.CommonUtils;

/**
 * Oracleのalter tableコマンド
 * 
 * @author tatsuo satoh
 * 
 */
public class OracleCreateTableFactory extends AbstractCreateTableFactory<OracleSqlBuilder> {

	@Override
	protected void addOption(final Table table, OracleSqlBuilder builder) {
		Map<String,String> map=table.getSpecifics();
		if (CommonUtils.isEmpty(map)){
			return;
		}
		boolean hasProperty=false;
		for(Map.Entry<String, String> entry:map.entrySet()){
			if (entry.getValue()!=null){
				hasProperty=true;
			}
		}
		if (!hasProperty){
			return;
		}
		boolean hasStorage=false;
		for(Map.Entry<String, String> entry:map.entrySet()){
			if (entry.getValue()==null){
				continue;
			}
			if (builder.isStoragePropertyName(entry.getKey())){
				hasStorage=true;
				continue;
			}
			builder.lineBreak().oracleProperty(entry.getKey(), entry.getValue());
		}
		if (hasStorage){
			builder.lineBreak().storage();
			builder.lineBreak()._add("(");
			builder.appendIndent(1);
			for(Map.Entry<String, String> entry:map.entrySet()){
				if (entry.getValue()==null){
					continue;
				}
				if (!builder.isStoragePropertyName(entry.getKey())){
					continue;
				}
				builder.lineBreak().oracleProperty(entry.getKey(), entry.getValue());
			}
			builder.appendIndent(-1);
			builder.lineBreak()._add(")");
		}
	}
	
	
	@Override
	protected void addOtherDefinitions(Table table, List<SqlOperation> result){
		if (table.getRemarks()!=null){
			OracleSqlBuilder builder=this.createSqlBuilder();
			builder.comment().on().table().space().name(table, this.getOptions().isDecorateSchemaName()).is().sqlChar(table.getRemarks());
			addSql(result, builder, SqlType.SET_COMMENT, table);
		}
		table.getColumns().stream().filter(c->c.getRemarks()!=null).forEach(c->{
			OracleSqlBuilder builder=this.createSqlBuilder();
			builder.comment().on().column().space().columnName(c, true, this.getOptions().isDecorateSchemaName()).is().sqlChar(c.getRemarks());
			addSql(result, builder, SqlType.SET_COMMENT, c);
		});
		table.getIndexes().stream().filter(c->c.getRemarks()!=null).forEach(c->{
			OracleSqlBuilder builder=this.createSqlBuilder();
			builder.comment().on().index().space().name(c, this.getOptions().isDecorateSchemaName()).is().sqlChar(c.getRemarks());
			addSql(result, builder, SqlType.SET_COMMENT, c);
		});
		table.getConstraints().stream().filter(c->c.getRemarks()!=null).forEach(c->{
			OracleSqlBuilder builder=this.createSqlBuilder();
			builder.comment().on().constraint().space().name(c, this.getOptions().isDecorateSchemaName()).on().name(table, this.getOptions().isDecorateSchemaName()).is().sqlChar(c.getRemarks());
			addSql(result, builder, SqlType.SET_COMMENT, c);
		});
	}
}
