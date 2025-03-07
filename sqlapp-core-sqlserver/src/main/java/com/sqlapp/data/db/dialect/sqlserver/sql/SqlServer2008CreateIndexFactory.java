/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
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
 * along with sqlapp-core-sqlserver.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.sqlserver.sql;

import java.util.Map;

import com.sqlapp.data.db.dialect.sqlserver.util.SqlServerSqlBuilder;
import com.sqlapp.data.schemas.Index;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.OnOffType;

public class SqlServer2008CreateIndexFactory extends
	SqlServer2005CreateIndexFactory{

	@Override
	protected void addIncludesAfter(final Index obj, final Table table,
			final SqlServerSqlBuilder builder) {
		addFilter(obj, table, builder);
	}
	
	protected void addFilter(final Index obj, final Table table,
			final SqlServerSqlBuilder builder) {
		if (!CommonUtils.isEmpty(obj.getWhere())){
			builder.lineBreak().where().space()._add(obj.getWhere());
		}
	}
	
	@Override
	protected Map<String,String> createIndexWithOption(final Index obj, final Table table) {
		final Map<String,String> map=super.createIndexWithOption(obj, table);
		final String val=obj.getSpecifics().get("ONLINE");
		if (val!=null){
			OnOffType onOffType=OnOffType.parse(val);
			map.put("ONLINE", onOffType.toString());
		}
		return map;
	}

}
