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
import com.sqlapp.data.db.sql.AbstractSetSearchPathToSchemaFactory;
import com.sqlapp.data.schemas.DbObject;
import com.sqlapp.data.schemas.properties.SchemaNameProperty;

/**
 * カレントスキーマ選択コマンド
 * 
 * @author tatsuo satoh
 * 
 */
public class PostgresSetSearchPathToSchemaFactory extends
		AbstractSetSearchPathToSchemaFactory<DbObject<?>, PostgresSqlBuilder> {

	@Override
	protected void addSetSearchPath(DbObject<?> obj, PostgresSqlBuilder builder) {
		if (obj instanceof SchemaNameProperty) {
			SchemaNameProperty<?> schemaName = ((SchemaNameProperty<?>) obj);
			if (schemaName.getSchemaName() != null) {
				builder.set().search().path().to();
				addSchemaName(obj, builder);
			}
		}
	}
}
