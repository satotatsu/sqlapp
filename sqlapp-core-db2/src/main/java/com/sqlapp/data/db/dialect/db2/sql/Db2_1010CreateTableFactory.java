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

import com.sqlapp.data.db.dialect.db2.util.Db2SqlBuilder;
import com.sqlapp.data.schemas.Table;

public class Db2_1010CreateTableFactory extends Db2CreateTableFactory{

	@Override
	protected void addCreateObject(final Table table, final Db2SqlBuilder builder) {
		builder.create().table().ifNotExists(this.getOptions().isCreateIfNotExists());
		builder.name(table, this.getOptions().isDecorateSchemaName());
	}

	/**
	 * オプションを追加します
	 * 
	 * @param table
	 * @param builder
	 */
	@Override
	protected void addOption(final Table table, final Db2SqlBuilder builder) {
		super.addOption(table, builder);
		addCompress(table, builder);
	}

	protected void addCompress(final Table table,
			final Db2SqlBuilder builder) {
		if (table.isCompression()) {
			builder.compress().yes();
			if ("VALUE".equalsIgnoreCase(table.getCompressionType())) {
				builder.value().compression();
			}
		}
	}

	
}
