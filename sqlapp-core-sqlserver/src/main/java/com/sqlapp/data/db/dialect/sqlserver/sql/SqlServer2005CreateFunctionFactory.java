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

import com.sqlapp.data.db.dialect.sqlserver.util.SqlServerSqlBuilder;
import com.sqlapp.data.db.sql.AbstractCreateFunctionFactory;
import com.sqlapp.data.schemas.Function;
import com.sqlapp.data.schemas.SqlSecurity;
import com.sqlapp.util.CommonUtils;

public class SqlServer2005CreateFunctionFactory extends
		AbstractCreateFunctionFactory<SqlServerSqlBuilder> {

	@Override
	protected void addCreateObject(final Function obj,
			SqlServerSqlBuilder builder) {
		builder.create();
		if (obj.getFunctionType()!=null&&obj.getFunctionType().isAggregate()) {
			builder.aggregate();
		} else {
			builder.function();
		}
		builder.name(obj);
		builder.space().arguments(obj.getArguments());
		builder.lineBreak().returns();
		if (!CommonUtils.isEmpty(obj.getReturning().getName())) {
			builder.space()._add(obj.getReturning().getName());
		}
		if (obj.getFunctionType()!=null&&obj.getFunctionType().isTable()) {
			builder.table();
			if (!CommonUtils.isEmpty(obj.getReturning().getDefinition())) {
				builder.lineBreak()._add(obj.getReturning().getDefinition());
			}
		} else {
			builder.space()._add(obj.getReturning());
		}
		if (obj.getOnNullCall() != null || obj.getExecuteAs() != null||obj.getSqlSecurity()!=null) {
			String conditionKey = "with";
			builder.lineBreak().with();
			builder.setCondition(conditionKey, false);
			if (obj.getOnNullCall() != null) {
				builder.space()._add(obj.getOnNullCall());
				builder.setCondition(conditionKey, true);
			}
			if (obj.getExecuteAs() != null||obj.getSqlSecurity()!=null) {
				builder.$if(builder.getCondition(conditionKey), ()->{
					builder.lineBreak();
					builder.comma().space();
				});
				builder.execute().as();
				if (!CommonUtils.isEmpty(obj.getExecuteAs())) {
					builder.space().sqlNchar(obj.getExecuteAs());
				} else {
					if (obj.getSqlSecurity()==SqlSecurity.Invoker){
						builder.caller();
					}else if (obj.getSqlSecurity()==SqlSecurity.Definer){
						builder.owner();
					}
				}
			}
		}
		builder.lineBreak().as();
		if (CommonUtils.isEmpty(obj.getClassName())) {
			builder.lineBreak()._add(obj.getStatement());
		} else {
			builder.lineBreak().external().name().space();
			if (!CommonUtils.isEmpty(obj.getClassNamePrefix())) {
				builder._add(obj.getClassNamePrefix() + ".");
			}
			builder._add(obj.getClassName() + ".")
					._add(obj.getMethodName());
		}
	}
}
