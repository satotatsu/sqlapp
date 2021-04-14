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

import com.sqlapp.data.db.dialect.sqlserver.metadata.SqlServer2005IndexReader;
import com.sqlapp.data.db.dialect.sqlserver.util.SqlServerSqlBuilder;
import com.sqlapp.data.db.sql.AddObjectDetail;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Index;
import com.sqlapp.data.schemas.Partitioning;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.util.CommonUtils;

public class SqlServer2005CreateIndexFactory extends
	SqlServerCreateIndexFactory{

	@Override
	protected void addObjectDetailAfter(final Index obj, final Table table,
			final SqlServerSqlBuilder builder) {
		addIncludes(obj, table, builder);
		addIncludesAfter(obj, table, builder);
		addIndexOption(obj, table, builder);
		addPartitioning(obj, table, builder);
	}

	protected void addIncludes(final Index obj, final Table table,
			final SqlServerSqlBuilder builder) {
		if (!CommonUtils.isEmpty(obj.getIncludes())){
			builder.lineBreak().include().space().brackets(()->{
				builder.space();
				builder.names(obj.getIncludes());
				builder.space();
			});
		}
	}

	protected void addIncludesAfter(final Index obj, final Table table,
			final SqlServerSqlBuilder builder) {
	}

	@Override
	protected void addIndexOption(final Index obj, final Table table,
			final SqlServerSqlBuilder builder) {
		super.addIndexOption(obj, table, builder);
		String key=SqlServer2005IndexReader.IGNORE_DUP_KEY;
		String val=obj.getSpecifics().get(key);
		if (val!=null){
			builder.lineBreak()._add(key).eq().space()._add(val);
		}
		key=SqlServer2005IndexReader.STATISTICS_NORECOMPUTE;
		val=obj.getSpecifics().get(key);
		if (val!=null){
			builder.lineBreak()._add(key).eq().space()._add(val);
		}
	}
	
	protected void addPartitioning(final Index obj, final Table table,
			final SqlServerSqlBuilder builder) {
		if (obj.getPartitioning()!=null) {
			final AddObjectDetail<Partitioning,SqlServerSqlBuilder> addObjectDetail=this.getAddObjectDetail(obj.getPartitioning(), SqlType.CREATE);
			if (addObjectDetail!=null){
				addObjectDetail.addObjectDetail(obj.getPartitioning(), builder);
			}
		}

	}
}
