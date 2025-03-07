/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
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
 * along with sqlapp-core-mysql.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.mysql.sql;

import com.sqlapp.data.db.dialect.mysql.util.MySqlSqlBuilder;
import com.sqlapp.data.db.sql.AbstractCreateSchemaFactory;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.util.CommonUtils;

public class MySqlCreateSchemaFactory extends AbstractCreateSchemaFactory<MySqlSqlBuilder> {

	@Override
	protected void addCreateObject(final Schema schema, MySqlSqlBuilder builder) {
		builder.create().schema().ifNotExists();
		builder.name(schema);
		if (!CommonUtils.isEmpty(schema.getCollation())){
			builder._default().collate().space()._add(schema.getCollation());
		} else{
			if (!CommonUtils.isEmpty(schema.getCharacterSet())){
				builder._default().characterSet().space()._add(schema.getCharacterSet());
			}
		}
	}

}
