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
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.Trigger;
import com.sqlapp.util.CommonUtils;

/**
 * Create Trigger
 * 
 * @author satoh
 * 
 */
public class Postgres90CreateTriggerFactory extends PostgresCreateTriggerFactory {

	/** Optional vendor attribute: ALWAYS or REPLICA; ordinary enabled triggers omit it. */
	public static final String FIRING_MODE = "TRIGGER_FIRING_MODE";

	@Override
	protected void addOptions(final Trigger obj, final List<SqlOperation> sqlList) {
		super.addOptions(obj, sqlList);
		String mode = obj.getSpecifics().get(FIRING_MODE);
		if (mode != null && !mode.equals("ALWAYS") && !mode.equals("REPLICA")) {
			throw new IllegalArgumentException("TRIGGER_FIRING_MODE must be ALWAYS or REPLICA: " + mode);
		}
		if (obj.isEnable() && mode == null) return;
		if (CommonUtils.isEmpty(obj.getTableName())) {
			throw new IllegalArgumentException("Trigger state restoration requires tableName: " + obj.getName());
		}
		String schemaName = CommonUtils.notEmpty(obj.getTableSchemaName(), obj.getSchemaName());
		Table table = new Table(obj.getTableName()).setSchemaName(schemaName);
		PostgresSqlBuilder builder = createSqlBuilder();
		builder.alter().table().space().name(table, getOptions().isDecorateSchemaName()
				|| !CommonUtils.eq(schemaName, obj.getSchemaName()));
		builder.space()._add(obj.isEnable() ? "ENABLE " + mode + " TRIGGER" : "DISABLE TRIGGER");
		builder.space().name(obj, false);
		addSql(sqlList, builder, SqlType.ALTER, obj);
	}

	@Override
	protected void addWhen(final Trigger obj, PostgresSqlBuilder builder) {
		builder.lineBreak();
		builder.when().space()._add(obj.getWhen());
	}
}
