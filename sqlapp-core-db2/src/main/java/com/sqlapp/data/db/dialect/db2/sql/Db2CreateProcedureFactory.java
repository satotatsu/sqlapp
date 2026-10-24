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
import com.sqlapp.data.db.sql.AbstractCreateProcedureFactory;
import com.sqlapp.data.schemas.Procedure;

public class Db2CreateProcedureFactory extends AbstractCreateProcedureFactory<Db2SqlBuilder> {

	@Override
	protected void addCreateObject(final Procedure obj, Db2SqlBuilder builder) {
		if (!com.sqlapp.util.CommonUtils.isEmpty(obj.getDefinition())) {
			builder._add(obj.getDefinition());
			return;
		}
		if (com.sqlapp.util.CommonUtils.isEmpty(obj.getStatement())) {
			throw new IllegalArgumentException("Db2 procedure requires a definition or statement: " + obj.getName());
		}
		// Retain the existing PL/SQL path for compatibility-mode callers.
		if (obj.getLanguage() == null || "SQL".equalsIgnoreCase(obj.getLanguage())) {
			builder.create().procedure().name(obj, getOptions().isDecorateSchemaName());
			builder.space().arguments(obj.getArguments());
			if (obj.getSpecificName() != null) {
				builder.lineBreak().specific().space().name(obj.getSpecificName());
			}
			builder.lineBreak().language().space()._add("SQL");
			builder.lineBreak()._add(obj.getStatement());
			return;
		}
		if (!"PLSQL".equalsIgnoreCase(obj.getLanguage())) {
			throw new IllegalArgumentException("External Db2 procedure requires complete definition: " + obj.getName());
		}
		builder.create().or().replace().procedure();
		builder.name(obj, this.getOptions().isDecorateSchemaName());
		builder.space()._add("(");
		int count = 0;
		for (com.sqlapp.data.schemas.NamedArgument argument : obj.getArguments()) {
			builder.comma(count++ > 0);
			builder._add(argument.getName()).space();
			if (argument.getDirection() != null
					&& argument.getDirection() != com.sqlapp.jdbc.sql.ParameterDirection.Input) {
				builder._add(argument.getDirection()).space();
			}
			builder.typeDefinition(argument.getDataType(), argument.getDataTypeName(), argument.getLength(),
					argument.getScale());
		}
		builder._add(")");
		builder.lineBreak().as();
		builder.lineBreak().begin();
		builder.appendIndent(1);
		builder.lineBreak()._add(obj.getStatement());
		builder.appendIndent(-1);
		builder.lineBreak().end();
	}
}
