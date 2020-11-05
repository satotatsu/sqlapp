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
import com.sqlapp.data.db.sql.AbstractCreatePartitionSchemeFactory;
import com.sqlapp.data.schemas.PartitionScheme;
import com.sqlapp.data.schemas.TableSpace;

public class SqlServer2005CreatePartitionSchemeFactory extends
		AbstractCreatePartitionSchemeFactory<SqlServerSqlBuilder> {

	@Override
	protected void addCreateObject(final PartitionScheme obj,
			SqlServerSqlBuilder builder) {
		builder.create().partition().scheme();
		builder.name(obj);
		builder.lineBreak();
		builder.as().partition().space()
				.name(obj.getPartitionFunctionName());
		builder.lineBreak();
		
		builder.to().space()._add("(");
		boolean first=true;
		for(TableSpace tableSpace:obj.getTableSpaces()){
			builder._add(", ", !first);
			builder._add(tableSpace.getName());
			first=false;
		}
		builder._add(")");
	}
}
