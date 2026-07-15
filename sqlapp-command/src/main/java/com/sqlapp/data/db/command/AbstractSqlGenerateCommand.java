/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-command.
 *
 * sqlapp-command is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-command is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-command.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.command;

import com.sqlapp.data.db.command.properties.SchemaOptionsProperty;
import com.sqlapp.data.db.command.properties.SqlFactoryRegistryProperty;
import com.sqlapp.data.db.command.properties.TableOptionsProperty;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.sql.Options;
import com.sqlapp.data.db.sql.SqlFactoryRegistry;
import com.sqlapp.data.schemas.DbCommonObject;
import com.sqlapp.data.schemas.SchemaUtils;

import lombok.Getter;
import lombok.Setter;

/**
 * SQL生成コマンド
 * 
 * @author tatsuo satoh
 *
 */
@Getter
@Setter
public abstract class AbstractSqlGenerateCommand extends AbstractCommand
		implements SchemaOptionsProperty, SqlFactoryRegistryProperty {

	private SqlFactoryRegistry sqlFactoryRegistry;

	private Options schemaOptions = new Options();

	protected SqlFactoryRegistry getSqlFactoryRegistry(final DbCommonObject<?> target) {
		SqlFactoryRegistry sqlFactoryRegistry = getSqlFactoryRegistry();
		if (sqlFactoryRegistry == null) {
			final Dialect dialect = SchemaUtils.getDialect(target);
			sqlFactoryRegistry = dialect.createSqlFactoryRegistry();
		}
		if (this.getSchemaOptions() != null) {
			sqlFactoryRegistry.setOptions(this.getSchemaOptions());
		}
		if (this instanceof TableOptionsProperty) {
			sqlFactoryRegistry.setTableOptions(((TableOptionsProperty) this).getTableOptions());
		}
		return sqlFactoryRegistry;
	}

}
