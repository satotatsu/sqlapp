/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
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
 * along with sqlapp-core-mysql.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.sqlapp.data.db.dialect.mysql.sql;

import com.sqlapp.data.db.dialect.mysql.util.MySqlSqlBuilder;
import com.sqlapp.data.db.sql.AbstractSetSearchPathToSchemaFactory;
import com.sqlapp.data.schemas.DbObject;
import com.sqlapp.data.schemas.properties.SchemaNameProperty;

/**
 * カレントスキーマ選択コマンド
 * 
 * @author tatsuo satoh
 * 
 */
public class MySqlSetSearchPathToSchemaFactory extends
		AbstractSetSearchPathToSchemaFactory<DbObject<?>, MySqlSqlBuilder> {

	@Override
	protected void addSetSearchPath(DbObject<?> obj, MySqlSqlBuilder builder) {
		if (obj instanceof SchemaNameProperty) {
			SchemaNameProperty<?> schemaName = ((SchemaNameProperty<?>) obj);
			if (schemaName.getSchemaName() != null) {
				builder.use();
				addSchemaName(obj, builder);
			}
		}
	}
}
