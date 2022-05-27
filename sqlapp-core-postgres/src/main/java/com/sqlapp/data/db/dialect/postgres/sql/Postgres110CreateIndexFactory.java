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

import com.sqlapp.data.db.dialect.postgres.util.PostgresSqlBuilder;
import com.sqlapp.data.db.dialect.sqlserver.util.SqlServerSqlBuilder;
import com.sqlapp.data.db.sql.AbstractCreateIndexFactory;
import com.sqlapp.data.schemas.Index;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.util.CommonUtils;

/**
 * INDEX生成クラス
 * 
 * @author satoh
 * 
 */
public class Postgres110CreateIndexFactory extends PostgresCreateIndexFactory<PostgresSqlBuilder> {

	@Override
	protected void addObjectDetailAfter(final Index obj, final Table table,
			final SqlServerSqlBuilder builder) {
		addIncludes(obj, table, builder);
		addIncludesAfter(obj, table, builder);
		addIndexWithOption(obj, table, builder);
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
}
