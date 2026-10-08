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
import com.sqlapp.data.db.sql.AbstractCreateViewFactory;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.View;

/**
 * Postgres Create View
 * 
 * @author satoh
 * 
 */
public class PostgresCreateViewFactory extends AbstractCreateViewFactory<PostgresSqlBuilder> {

	public static final String SECURITY_BARRIER = "security_barrier";
	public static final String CHECK_OPTION = "check_option";
	public static final String SECURITY_INVOKER = "security_invoker";

	protected boolean supportsViewOption(String name) { return false; }

	@Override
	protected void addCreateObject(View obj, PostgresSqlBuilder builder) {
		if (!com.sqlapp.util.CommonUtils.isEmpty(obj.getDefinition())
				|| com.sqlapp.util.CommonUtils.isEmpty(obj.getStatement())) {
			super.addCreateObject(obj, builder);
			return;
		}
		var options = new java.util.ArrayList<String>();
		for (String name : List.of(SECURITY_BARRIER, CHECK_OPTION, SECURITY_INVOKER)) {
			String value = obj.getSpecifics().get(name);
			if (value == null) continue;
			if (!supportsViewOption(name)) {
				throw new IllegalArgumentException("View option " + name + " is unsupported by this PostgreSQL version.");
			}
			value = value.trim().toLowerCase(java.util.Locale.ROOT);
			if (CHECK_OPTION.equals(name)) {
				if (!List.of("local", "cascaded").contains(value)) {
					throw new IllegalArgumentException("View check_option must be local or cascaded.");
				}
				value = "'" + value + "'";
			} else if (!List.of("true", "false").contains(value)) {
				throw new IllegalArgumentException("View " + name + " must be true or false.");
			}
			options.add(name + "=" + value);
		}
		if (options.isEmpty()) {
			super.addCreateObject(obj, builder);
			return;
		}
		createObject(obj, builder);
		builder.name(obj, getOptions().isDecorateSchemaName()).space()
				._add("WITH (")._add(String.join(", ", options))._add(")")
				.lineBreak().as().lineBreak()._add(obj.getStatement());
	}

	@Override
	protected void addOtherDefinitions(View table, List<SqlOperation> result) {
		if (table.getRemarks() != null) {
			PostgresSqlBuilder builder = this.createSqlBuilder();
			builder.comment().on().view().space().name(table, this.getOptions().isDecorateSchemaName()).is()
					.sqlChar(table.getRemarks());
			addSql(result, builder, SqlType.SET_COMMENT, table);
		}
		table.getColumns().stream().filter(c -> c.getRemarks() != null).forEach(column -> {
			PostgresSqlBuilder builder = createSqlBuilder();
			builder.comment().on().column().space().name(table, getOptions().isDecorateSchemaName())
					._add(".").name(column).is().sqlChar(column.getRemarks());
			addSql(result, builder, SqlType.SET_COMMENT, column);
		});
	}

}
