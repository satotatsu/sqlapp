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

package com.sqlapp.data.db.dialect.hsql.sql;

import com.sqlapp.data.db.dialect.hsql.util.HsqlSqlBuilder;
import com.sqlapp.data.db.sql.AbstractCreateTableFactory;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.db.dialect.hsql.HsqlTextTableSources;
import com.sqlapp.data.db.dialect.hsql.HsqlTextTableSources.Source;
import java.util.List;
import java.util.Map;

/**
 * CREATE TABLE
 * 
 * @author 竜夫
 * 
 */
public class HsqlCreateTableFactory extends AbstractCreateTableFactory<HsqlSqlBuilder> {
	@Override
	public List<SqlOperation> createSql(Table table) {
		return createSql(table, Map.of());
	}

	public List<SqlOperation> createSql(Table table, Map<String, Source> bindings) {
		HsqlTextTableSources.validateBindings(bindings,
				HsqlTextTableSources.isText(table) ? java.util.Set.of(HsqlTextTableSources.id(table))
						: java.util.Set.of());
		Source source = null;
		if (HsqlTextTableSources.isText(table)) {
			source = HsqlTextTableSources.resolve(table, bindings);
		} else if (bindings.containsKey(HsqlTextTableSources.id(table))) {
			throw new IllegalArgumentException("HSQLDB text source binding requires a TEXT table: " + table.getName());
		}
		var operations = super.createSql(table);
		if (source != null) {
			var builder = createSqlBuilder().set().table().name(table, getOptions().isDecorateSchemaName()).space()
					._add("SOURCE").space().sqlChar(source.definition());
			if (source.descending())
				builder.space()._add("DESC");
			addSql(operations, builder, SqlType.ALTER, table);
		}
		if (Boolean.TRUE.equals(table.getReadonly())) {
			var builder = createSqlBuilder().set().table().name(table, getOptions().isDecorateSchemaName()).space()
					._add("READ ONLY");
			addSql(operations, builder, SqlType.ALTER, table);
		}
		return operations;
	}

	@Override
	protected void addCreateObject(Table table, HsqlSqlBuilder builder) {
		builder.create();
		if (HsqlTextTableSources.isText(table))
			builder.space()._add("TEXT");
		else if (table.getTableType() == Table.TableType.Cache)
			builder.space()._add("CACHED");
		else if (table.getTableType() == Table.TableType.Memory)
			builder.space()._add("MEMORY");
		builder.table().name(table, getOptions().isDecorateSchemaName());
	}

}
