/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-db2.
 *
 * sqlapp-core-db2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-db2 is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-db2.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.sqlapp.data.db.dialect.db2.sql;

import static com.sqlapp.util.CommonUtils.list;

import java.util.List;

import com.sqlapp.data.db.dialect.db2.util.Db2SqlBuilder;
import com.sqlapp.data.db.sql.AbstractTruncateTableFactory;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Table;

public class Db2_970TruncateTableFactory extends
		AbstractTruncateTableFactory<Db2SqlBuilder> {

	@Override
	public List<SqlOperation> createSql(Table obj) {
		Db2SqlBuilder builder = createSqlBuilder();
		addTruncateTable(obj, builder);
		List<SqlOperation> sqlList = list();
		addSql(sqlList, builder, SqlType.TRUNCATE, obj);
		return sqlList;
	}

	protected void addTruncateTable(Table obj, Db2SqlBuilder builder) {
		builder.truncate().table();
		builder.name(obj, this.getOptions().isDecorateSchemaName());
	}

}
