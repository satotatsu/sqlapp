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

import static com.sqlapp.util.CommonUtils.list;

import java.util.List;

import com.sqlapp.data.db.dialect.sqlserver.util.SqlServerSqlBuilder;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.util.CommonUtils;

/**
 * SQLServer2012 create table
 * 
 * @author tatsuo satoh
 * 
 */
public class SqlServer2012CreateTableFactory extends SqlServer2008CreateTableFactory {

	@Override
	public List<SqlOperation> createSql(final Table table) {
		final Boolean bool = table.getSpecifics().get("is_filetable", Boolean.class);
		if (bool == null || !bool.booleanValue()) {
			return super.createSql(table);
		}
		return createFileTable(table);
	}

	private List<SqlOperation> createFileTable(final Table table) {
		final List<SqlOperation> sqlList = list();
		final SqlServerSqlBuilder builder = createSqlBuilder();
		addCreateObject(table, builder);
		builder.as().filetable();
		String directory = table.getSpecifics().get("directory_name");
		String collation = table.getSpecifics().get("filename_collation_name");
		if (!CommonUtils.isEmpty(directory) || !CommonUtils.isEmpty(collation)) {
			builder.with().space().brackets(() -> {
				if (!CommonUtils.isEmpty(directory))
					builder.filetableDirectory().eq()._add(SqlServerDdlSupport.nchar(directory));
				if (!CommonUtils.isEmpty(collation)) {
					builder.comma(!CommonUtils.isEmpty(directory)).filetableCollateFilename().eq();
					if ("database_default".equalsIgnoreCase(collation))
						builder._add("database_default");
					else
						builder.name(collation);
				}
			});
		}
		addSql(sqlList, builder, com.sqlapp.data.db.sql.SqlType.CREATE, table);
		return sqlList;
	}
}
