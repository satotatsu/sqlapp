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
import com.sqlapp.data.db.sql.AbstractCreateSequenceFactory;
import com.sqlapp.data.schemas.Sequence;

public class SqlServer2012CreateSequenceFactory extends AbstractCreateSequenceFactory<SqlServerSqlBuilder> {

	@Override
	protected void addOrder(final Sequence obj, SqlServerSqlBuilder builder) {
	}

	@Override
	protected void addDataType(final Sequence sequence, SqlServerSqlBuilder builder) {
		String name = sequence.getDataTypeName();
		if (sequence.getDataType() == com.sqlapp.data.db.datatype.DataType.DECIMAL
				|| name != null && (name.equalsIgnoreCase("decimal") || name.equalsIgnoreCase("numeric"))) {
			if (sequence.getScale() != null && sequence.getScale() != 0) throw new IllegalArgumentException("SQL Server sequence scale must be zero");
			builder.as().space()._add(name == null ? "DECIMAL" : name)._add("(")
					._add(sequence.getPrecision() == null ? 38 : sequence.getPrecision())._add(",0)");
		} else {
			super.addDataType(sequence, builder);
		}
	}

	@Override
	protected void addOptions(final Sequence sequence, SqlServerSqlBuilder builder) {
		if (!sequence.isCache() && sequence.getCacheSize() == null) builder.space()._add("NO CACHE");
	}
}
