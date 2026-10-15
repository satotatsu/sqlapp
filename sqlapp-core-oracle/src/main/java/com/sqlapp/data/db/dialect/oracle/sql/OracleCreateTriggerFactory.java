/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-oracle.
 *
 * sqlapp-core-oracle is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-oracle is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-oracle.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.oracle.sql;

import java.util.List;

import com.sqlapp.data.db.dialect.oracle.util.OracleSqlBuilder;
import com.sqlapp.data.db.sql.AbstractCreateTriggerFactory;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Trigger;
import com.sqlapp.util.CommonUtils;

/**
 * Oracleのcreate trigger生成
 * 
 * @author tatsuo satoh
 * 
 */
public class OracleCreateTriggerFactory extends AbstractCreateTriggerFactory<OracleSqlBuilder> {

	@Override
	protected void addCreateObject(final Trigger obj, OracleSqlBuilder builder) {
		if (!CommonUtils.isEmpty(obj.getDefinition())) {
			builder._add(obj.getDefinition());
			return;
		}
		builder.create().or().replace().trigger();
		builder.name(obj, this.getOptions().isDecorateSchemaName());
		addCreateObjectDetail(obj, builder);
	}

	@Override
	protected void addCreateTriggerBody(final Trigger obj, OracleSqlBuilder builder) {
		if (obj.getActionOrientation() == null || "ROW".equalsIgnoreCase(obj.getActionOrientation())) {
			builder.lineBreak().forEach().row();
		}
		if (!CommonUtils.isEmpty(obj.getActionCondition())) {
			builder.lineBreak()._add("WHEN (")._add(obj.getActionCondition())._add(")");
		}
		builder.lineBreak()._add(toString(obj.getStatement()));
	}

	@Override
	protected void addOptions(final Trigger obj, List<SqlOperation> sqlList) {
		super.addOptions(obj, sqlList);
		if (!obj.isEnable()) {
			OracleSqlBuilder builder = createSqlBuilder();
			builder.alter().trigger().name(obj, getOptions().isDecorateSchemaName()).disable();
			addSql(sqlList, builder, SqlType.ALTER, obj);
		}
	}
}
