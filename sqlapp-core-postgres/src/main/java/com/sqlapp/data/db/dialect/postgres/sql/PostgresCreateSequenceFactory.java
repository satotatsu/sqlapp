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
import com.sqlapp.data.db.sql.AbstractCreateSequenceFactory;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Sequence;

public class PostgresCreateSequenceFactory extends
		AbstractCreateSequenceFactory<PostgresSqlBuilder> {

	@Override
	protected void addDataType(final Sequence obj, PostgresSqlBuilder builder){
	}
	
	@Override
	protected void addOptions(final Sequence obj, List<SqlOperation> sqlList) {
		if (obj.getRemarks()!=null){
			PostgresSqlBuilder builder=this.createSqlBuilder();
			builder.comment().on().sequence().space().name(obj, this.getOptions().isDecorateSchemaName()).is().sqlChar(obj.getRemarks());
			addSql(sqlList, builder, SqlType.SET_COMMENT, obj);
		}
	}
}
