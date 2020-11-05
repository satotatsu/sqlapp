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

import com.sqlapp.data.db.dialect.mysql.util.MySqlSqlBuilder;
import com.sqlapp.data.db.sql.AbstractCreateFunctionFactory;
import com.sqlapp.data.schemas.Function;
import com.sqlapp.util.CommonUtils;

public class MySqlCreateFunctionFactory extends
		AbstractCreateFunctionFactory<MySqlSqlBuilder> {

	@Override
	protected void addCreateObject(final Function obj, MySqlSqlBuilder builder) {
		builder.create().function();
		builder.name(obj);
		builder.space().arguments(obj.getArguments());
		builder.space().returns().space()._add(obj.getReturning());
		if (obj.getDeterministic() != null) {
			if (obj.getDeterministic().booleanValue()) {
				builder.lineBreak();
			} else {
				builder.lineBreak().not();
			}
			builder.lineBreak().not().deterministic();
		}
		if (obj.getSqlDataAccess() != null) {
			builder.lineBreak()._add(obj.getSqlDataAccess());
		}
		if (obj.getSqlSecurity() != null) {
			builder.lineBreak()._add(obj.getSqlSecurity());
		}
		builder.lineBreak()._add(obj.getStatement());
		if (!CommonUtils.isEmpty(obj.getRemarks())) {
			builder.lineBreak().comment().space();
			builder.sqlChar(obj.getRemarks());
		}
		builder.lineBreak()._add(obj.getStatement());
	}

}
