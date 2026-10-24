/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-hsql.
 *
 * sqlapp-core-hsql is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-hsql is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-hsql.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.db2.sql;

import com.sqlapp.data.db.dialect.db2.util.Db2SqlBuilder;
import com.sqlapp.data.db.sql.AbstractCreateTemporaryTableFactory;
import com.sqlapp.data.schemas.Table;

/**
 * CREATE TEMPORARY TABLE
 *
 * @author satoh
 *
 */
public class DB2CreateTemporaryTableFactory extends AbstractCreateTemporaryTableFactory<Db2SqlBuilder> {
	@Override
	protected void addCreateIndexDefinition(Table table, com.sqlapp.data.schemas.Index index,
			java.util.List<com.sqlapp.data.db.sql.SqlOperation> result) {
		String prefix = getTableOptions().getTemporaryTableNamePrefix().apply(table);
		String suffix = getTableOptions().getTemporaryTableNameSuffix().apply(table);
		Table temporary = new Table((prefix == null ? "" : prefix) + table.getName() + (suffix == null ? "" : suffix));
		temporary.setSchemaName("SESSION");
		temporary.setDialect(getDialect());
		com.sqlapp.data.schemas.Index copy = index.clone();
		copy.setSchemaName("SESSION");
		copy.getSpecifics().remove("INDEX_SCHEMA");
		temporary.getIndexes().add(copy);
		com.sqlapp.data.db.sql.SqlFactory<com.sqlapp.data.schemas.Index> factory = getSqlFactoryRegistry()
				.getSqlFactory(copy, com.sqlapp.data.db.sql.SqlType.CREATE);
		result.addAll(factory.createSql(copy));
	}

	@Override
	protected void addCreateObject(final Table obj, final Db2SqlBuilder builder) {
		String prefix = this.getTableOptions().getTemporaryTableNamePrefix().apply(obj);
		String suffix = this.getTableOptions().getTemporaryTableNameSuffix().apply(obj);
		builder.declare().global().temporary().table();
		builder.space()._add(
				this.getDialect().getTemporaryTableName(obj, prefix, suffix, this.getOptions().isDecorateSchemaName()));
	}
}
