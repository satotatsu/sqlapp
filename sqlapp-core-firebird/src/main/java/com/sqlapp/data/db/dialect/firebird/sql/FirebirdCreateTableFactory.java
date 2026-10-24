/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-firebird.
 *
 * sqlapp-core-firebird is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-firebird is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-firebird.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.firebird.sql;

import com.sqlapp.data.db.dialect.firebird.util.FirebirdSqlBuilder;
import com.sqlapp.data.db.sql.AbstractCreateTableFactory;

/**
 * Firebird create table Operation factory
 * 
 * @author tatsuo satoh
 * 
 */
public class FirebirdCreateTableFactory extends AbstractCreateTableFactory<FirebirdSqlBuilder> {
	@Override
	public java.util.List<com.sqlapp.data.db.sql.SqlOperation> createSql(com.sqlapp.data.schemas.Table table) {
		var result = super.createSql(table);
		if (table.getTableType() == com.sqlapp.data.schemas.Table.TableType.Temporary
				&& table.getSpecifics().containsKey("firebird.sqlSecurity")) {
			var builder = createSqlBuilder()._add("ALTER TABLE ").name(table, getOptions().isDecorateSchemaName())
					._add(" ALTER");
			FirebirdRoutineSql.addSecurity(table.getSpecifics(), builder);
			addSql(result, builder, com.sqlapp.data.db.sql.SqlType.ALTER, table);
		}
		return result;
	}

	@Override
	protected void addCreateObject(com.sqlapp.data.schemas.Table table, FirebirdSqlBuilder builder) {
		if (table.getTableType() == com.sqlapp.data.schemas.Table.TableType.Temporary) {
			FirebirdTemporaryTableSql.requireSupportedDialect(builder);
			builder.create().global().temporary().table().name(table, getOptions().isDecorateSchemaName());
		} else {
			if (table.getSpecifics().containsKey("firebird.onCommit"))
				throw new IllegalArgumentException("firebird.onCommit requires a temporary table");
			super.addCreateObject(table, builder);
		}
	}

	@Override
	protected void addOption(com.sqlapp.data.schemas.Table table, FirebirdSqlBuilder builder) {
		if (table.getTableType() == com.sqlapp.data.schemas.Table.TableType.Temporary)
			FirebirdTemporaryTableSql.addOnCommit(table,
					getTableOptions().getTempTableOnCommitPreserveRows().test(table), builder);
		else
			FirebirdRoutineSql.addSecurity(table.getSpecifics(), builder);
	}

}
