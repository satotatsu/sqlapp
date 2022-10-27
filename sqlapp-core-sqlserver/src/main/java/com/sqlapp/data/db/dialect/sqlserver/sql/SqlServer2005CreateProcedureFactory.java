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
import com.sqlapp.data.db.sql.AbstractCreateProcedureFactory;
import com.sqlapp.data.schemas.Procedure;
import com.sqlapp.util.CommonUtils;

public class SqlServer2005CreateProcedureFactory extends
		AbstractCreateProcedureFactory<SqlServerSqlBuilder> {

	@Override
	protected void addCreateObject(final Procedure obj,
			SqlServerSqlBuilder builder) {
		if (this.getOptions().isDropIfExists()) {
			builder.createOrAlter();
		} else {
			builder.create();
		}
		builder.procedure();
		builder.name(obj);
		if (!CommonUtils.isEmpty(obj.getArguments())) {
			builder.arguments(obj.getArguments());
		}
		if (obj.getExecuteAs() != null) {
			String conditionKey = "with";
			builder.lineBreak().with();
			builder.setCondition(conditionKey, false);
			if (obj.getExecuteAs() != null) {
				builder.comma(builder.getCondition(conditionKey)).space()
						.execute().as();
				if ("CALLER".equalsIgnoreCase(obj.getExecuteAs())
						|| "SELF".equalsIgnoreCase(obj.getExecuteAs())
						|| "OWNER".equalsIgnoreCase(obj.getExecuteAs())) {
					builder.space()._add(obj.getExecuteAs().toUpperCase());
				} else {
					builder.space().sqlNchar(obj.getExecuteAs());
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
