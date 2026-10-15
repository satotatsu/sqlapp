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


import java.util.Collection;
import java.util.List;
import com.sqlapp.data.db.dialect.sqlserver.util.SqlServerSqlBuilder;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.schemas.Table;

/**
 * SQL Server 2016+ temporal table creation
 * 
 * @author tatsuo satoh
 * 
 */
public class SqlServer2016CreateTableFactory extends SqlServer2014CreateTableFactory {
	@Override
	public List<SqlOperation> createSql(Table table) {
		SqlServerTemporalSupport.period(table);
		if ("SYSTEM_VERSIONED_TEMPORAL_TABLE".equals(table.getSpecifics().get("temporal_type"))
				&& table.getSystemVersioning() == null)
			throw new IllegalArgumentException("temporal_type requires the shared systemVersioning and temporalPeriods model");
		List<SqlOperation> result = super.createSql(table);
		if (SqlServerTemporalSupport.enabled(table))
			result.add(SqlServerTemporalSupport.versioning(table, createSqlBuilder(), true, getOptions().isDecorateSchemaName()));
		return result;
	}

	@Override
	public List<SqlOperation> createSql(Collection<Table> tables) {
		return SqlServerTemporalSupport.enableLast(super.createSql(tables));
	}

	@Override
	protected void addConstraintDefinitions(Table table, SqlServerSqlBuilder builder) {
		super.addConstraintDefinitions(table, builder);
		var period = SqlServerTemporalSupport.period(table);
		if (period != null)
			builder.lineBreak().comma()._add("PERIOD FOR SYSTEM_TIME (").name(period.getStartColumnName())
					.comma().space().name(period.getEndColumnName())._add(")");
	}

}
