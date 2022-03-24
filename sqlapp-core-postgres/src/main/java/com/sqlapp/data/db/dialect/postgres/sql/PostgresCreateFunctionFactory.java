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

import java.util.List;

import com.sqlapp.data.db.dialect.postgres.util.PostgresSqlBuilder;
import com.sqlapp.data.db.sql.AbstractCreateFunctionFactory;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Function;
import com.sqlapp.util.CommonUtils;

public class PostgresCreateFunctionFactory extends
		AbstractCreateFunctionFactory<PostgresSqlBuilder> {

	@Override
	protected void addCreateObject(final Function obj,
			PostgresSqlBuilder builder) {
		builder.create().or().replace();
		builder.function();
		builder.name(obj);
		builder.space().arguments(obj.getArguments());
		builder.lineBreak().returns();
		builder.space()._add(obj.getReturning());
		String quate=getQuate(obj);
		builder.lineBreak();
		builder.as().space()._add(quate);
		builder.lineBreak();
		builder._add(obj.getStatement());
		builder.lineBreak();
		builder._add(quate);
		if (!CommonUtils.isEmpty(obj.getLanguage())) {
			builder.lineBreak();
			builder.language().space()._add(obj.getLanguage());
		}
		if (obj.getDeterministic()!=null) {
			builder.lineBreak();
			if (obj.getDeterministic().booleanValue()) {
				builder.immutable();
			} else{
				builder._volatile();
			}
		} else{
			if (obj.getStable()!=null&&obj.getStable().booleanValue()) {
				builder.lineBreak();
				builder.stable();
			}
		}
		if (obj.getOnNullCall() != null) {
			builder.lineBreak();
			builder._add(obj.getOnNullCall());
		}
		if (obj.getSqlSecurity() != null) {
			builder.lineBreak();
			builder._add(obj.getSqlSecurity());
		}
	}
	
	private String getQuate(Function obj){
		StringBuilder builder=new StringBuilder();
		for(String line:obj.getStatement()){
			builder.append(line);
			builder.append('\n');
		}
		String text=builder.toString();
		if (!text.contains("$$")){
			return "$$";
		}
		String name="$"+obj.getName()+"$";
		if (!text.contains(name)){
			return name;
		}
		int i=0;
		while(true){
			name="$"+i+"$";
			if (!text.contains(name)){
				return name;
			}
		}
	}
	
	@Override
	protected void addOptions(final Function obj, List<SqlOperation> sqlList) {
		if (obj.getRemarks()!=null){
			PostgresSqlBuilder builder=this.createSqlBuilder();
			builder.comment().on().function().space().specificName(obj, this.getOptions().isDecorateSchemaName()).is().sqlChar(obj.getRemarks());
			addSql(sqlList, builder, SqlType.SET_COMMENT, obj);
		}
	}
}
