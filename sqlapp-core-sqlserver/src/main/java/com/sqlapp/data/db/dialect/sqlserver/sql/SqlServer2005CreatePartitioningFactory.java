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

import com.sqlapp.data.db.dialect.sqlserver.util.SqlServerSqlBuilder;
import com.sqlapp.data.db.sql.AbstractCreatePartitioningFactory;
import com.sqlapp.data.schemas.Partitioning;
import com.sqlapp.util.CommonUtils;

public class SqlServer2005CreatePartitioningFactory extends
		AbstractCreatePartitioningFactory<SqlServerSqlBuilder> {
	
	@Override
	public void addObjectDetail(final Partitioning obj, final SqlServerSqlBuilder builder) {
		if (obj != null) {
			if (!CommonUtils.isEmpty(obj.getPartitionSchemeName())&&!CommonUtils.isEmpty(obj.getPartitioningColumns())){
				builder.lineBreak();
				builder.on().space()._add(obj.getPartitionSchemeName()).space().brackets(()->{
					builder.names(obj.getPartitioningColumns());
				});
			}
		}
	}


}
