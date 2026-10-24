/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
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
 * along with sqlapp-core-db2.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.db2.sql;

import com.sqlapp.data.db.dialect.db2.util.Db2SqlBuilder;
import com.sqlapp.data.db.dialect.db2.util.Db2CreationContext;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Table;
import java.util.List;
import java.util.ArrayList;
import com.sqlapp.data.db.sql.AbstractCreateMaskFactory;
import com.sqlapp.data.schemas.Mask;
import com.sqlapp.util.CommonUtils;

public class Db2_1010CreateMaskFactory extends AbstractCreateMaskFactory<Db2SqlBuilder> {
	@Override
	public List<SqlOperation> createSql(Mask mask) {
		if ("N".equals(mask.getSpecifics().get("SOURCE_VALID")))
			throw new IllegalArgumentException("Cannot recreate invalid Db2 column mask: " + mask.getName());
		var result = new ArrayList<SqlOperation>();
		for (String sql : Db2CreationContext.sql(mask.getSpecifics().get("QUALIFIER"),
				mask.getSpecifics().get("FUNC_PATH"))) {
			addSql(result, createSqlBuilder()._add(sql), SqlType.SET_SEARCH_PATH_TO_SCHEMA, mask);
		}
		result.addAll(super.createSql(mask));
		return result;
	}

	@Override
	protected void addCreateObject(final Mask obj, Db2SqlBuilder builder) {
		if (!CommonUtils.isEmpty(obj.getDefinition())) {
			builder._add(obj.getDefinition());
			return;
		}
		if (CommonUtils.isEmpty(obj.getStatement()))
			throw new IllegalArgumentException("Db2 column mask requires an expression or definition");
		builder.create().or().replace().mask();
		builder.name(obj, this.getOptions().isDecorateSchemaName());
		var table = new Table(obj.getTableName());
		table.setSchemaName(obj.getTableSchemaName() == null ? obj.getSchemaName() : obj.getTableSchemaName());
		builder.on().name(table,
				getOptions().isDecorateSchemaName() || !CommonUtils.eq(table.getSchemaName(), obj.getSchemaName()));
		String correlation = obj.getSpecifics().get("TABCORRELATION");
		if (correlation != null && !correlation.isBlank())
			builder.as().space().name(correlation);
		builder.for_().column().name(obj.getColumn()).return_();
		builder.appendIndent(1);
		builder.lineBreak()._add(obj.getStatement());
		builder.appendIndent(-1);
		builder.lineBreak().enable(obj.isEnable()).disable(!obj.isEnable());
	}
}
