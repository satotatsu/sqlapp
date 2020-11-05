/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-sybase.
 *
 * sqlapp-core-sybase is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-sybase is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-sybase.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.sqlapp.data.db.dialect.sybase.sql;

import java.util.List;

import com.sqlapp.data.db.dialect.sybase.util.SybaseSqlBuilder;
import com.sqlapp.data.db.sql.AbstractTableFactory;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.util.CommonUtils;

public class SybaseIdentityOffFactory extends
		AbstractTableFactory<SybaseSqlBuilder> {

	@Override
	public List<SqlOperation> createSql(Table obj) {
		SybaseSqlBuilder builder = createSqlBuilder();
		builder.set().identityInsert().name(obj).off();
		List<SqlOperation> list = CommonUtils.list();
		list.add(this.createOperation(builder.toString(), SqlType.IDENTITY_OFF, obj));
		return list;
	}
}
