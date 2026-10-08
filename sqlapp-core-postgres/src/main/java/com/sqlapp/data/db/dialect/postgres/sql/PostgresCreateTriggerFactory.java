/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
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
 * along with sqlapp-core-postgres.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.postgres.sql;

import java.util.List;
import com.sqlapp.data.db.dialect.postgres.util.PostgresSqlBuilder;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Trigger;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.util.CommonUtils;
import com.sqlapp.data.db.sql.AbstractCreateTriggerFactory;

/**
 * Create Trigger
 * 
 * @author satoh
 * 
 */
public class PostgresCreateTriggerFactory extends AbstractCreateTriggerFactory<PostgresSqlBuilder> {

	@Override
	protected void addOtherDefinitions(final Trigger obj, List<SqlOperation> sqlList) {
		super.addOtherDefinitions(obj, sqlList);
		if (obj.getRemarks() == null) return;
		if (CommonUtils.isEmpty(obj.getTableName())) {
			throw new IllegalArgumentException("Trigger comment requires tableName: " + obj.getName());
		}
		String schemaName = CommonUtils.notEmpty(obj.getTableSchemaName(), obj.getSchemaName());
		Table table = new Table(obj.getTableName()).setSchemaName(schemaName);
		PostgresSqlBuilder builder = createSqlBuilder();
		builder.comment().on().trigger().space().name(obj, false).on()
				.name(table, getOptions().isDecorateSchemaName() || !CommonUtils.eq(schemaName, obj.getSchemaName()))
				.is().sqlChar(obj.getRemarks());
		addSql(sqlList, builder, SqlType.SET_COMMENT, obj);
	}
}
