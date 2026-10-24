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
		appendDefaults(schema, builder);
	}

	static void appendDefaults(Schema schema, MySqlSqlBuilder builder) {
		if (!CommonUtils.isEmpty(schema.getCharacterSet())) {
			builder.default_().characterSet().space()._add(option(schema.getCharacterSet(), "character set"));
		}
		if (!CommonUtils.isEmpty(schema.getCollation())) {
			builder.default_().collate().space()._add(option(schema.getCollation(), "collation"));
		}
	}

	static String option(String value, String description) {
		if (!value.matches("[A-Za-z0-9_]+")) {
			throw new IllegalArgumentException("Invalid MySQL " + description + ": " + value);
		}
		return value;
	}

}
