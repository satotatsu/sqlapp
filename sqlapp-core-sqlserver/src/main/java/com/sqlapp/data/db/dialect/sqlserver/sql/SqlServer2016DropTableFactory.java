/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-sqlserver.
 *
 * sqlapp-core-sqlserver is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-sqlserver is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-sqlserver.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.sqlserver.sql;

import com.sqlapp.data.db.dialect.sqlserver.util.SqlServerSqlBuilder;
import com.sqlapp.data.db.sql.AbstractDropTableFactory;
import com.sqlapp.data.schemas.Table;
import java.util.List;
import java.util.Collection;
import java.util.ArrayList;
import com.sqlapp.data.db.sql.SqlOperation;

/**
 * DROP TABLE
 * 
 */
public class SqlServer2016DropTableFactory extends AbstractDropTableFactory<SqlServerSqlBuilder> {
	@Override
	public List<SqlOperation> createSql(Table table) {
		List<SqlOperation> result = new ArrayList<>();
		if (SqlServerTemporalSupport.enabled(table))
			result.add(disableVersioning(table));
		result.addAll(super.createSql(table));
		return result;
	}

	@Override
	public List<SqlOperation> createSql(Collection<Table> tables) {
		List<SqlOperation> result = new ArrayList<>();
		for (Table table : tables)
			if (SqlServerTemporalSupport.enabled(table))
				result.add(disableVersioning(table));
		for (Table table : sort(new ArrayList<>(tables)))
			result.addAll(super.createSql(table));
		return result;
	}

	private SqlOperation disableVersioning(Table table) {
		SqlOperation operation = SqlServerTemporalSupport.versioning(table, createSqlBuilder(), false,
				getOptions().isDecorateSchemaName());
		if (!getOptions().isDropIfExists())
			return operation;
		SqlServerSqlBuilder name = createSqlBuilder();
		name.name(table, getOptions().isDecorateSchemaName());
		String literal = name.toString().trim().replace("'", "''");
		return createOperation(
				"IF EXISTS (SELECT 1 FROM sys.tables WHERE object_id = OBJECT_ID(N'" + literal
						+ "') AND temporal_type = 2) " + operation.getSqlText(),
				com.sqlapp.data.db.sql.SqlType.ALTER, table);
	}

	@Override
	protected void addDropObject(final Table table, final SqlServerSqlBuilder builder) {
		builder.drop().table().ifExists(this.getOptions().isDropIfExists());
		builder.name(table, this.getOptions().isDecorateSchemaName());
	}
}
