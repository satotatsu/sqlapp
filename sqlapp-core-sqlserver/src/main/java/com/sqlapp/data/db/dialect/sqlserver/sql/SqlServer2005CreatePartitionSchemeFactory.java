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
import com.sqlapp.data.db.sql.AbstractCreatePartitionSchemeFactory;
import com.sqlapp.data.schemas.PartitionScheme;
import com.sqlapp.data.schemas.TableSpace;
import java.util.List;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.dialect.sqlserver.util.SqlServerPartitionOptions;

public class SqlServer2005CreatePartitionSchemeFactory
		extends AbstractCreatePartitionSchemeFactory<SqlServerSqlBuilder> {

	@Override
	protected void addCreateObject(final PartitionScheme obj, final SqlServerSqlBuilder builder) {
		builder.create().partition().scheme();
		builder.name(obj);
		builder.lineBreak();
		builder.as().partition().space().name(obj.getPartitionFunctionName());
		builder.lineBreak();
		builder.all(obj.getTableSpaces().isEmpty() || obj.getTableSpaces().size() == 1).to().space().brackets(() -> {
			builder.space();
			if (obj.getTableSpaces().isEmpty()) {
				builder._add("[PRIMARY]");
			} else {
				boolean first = true;
				for (final TableSpace tableSpace : obj.getTableSpaces()) {
					builder._add(", ", !first);
					if ("PRIMARY".equalsIgnoreCase(tableSpace.getName()))
						builder._add("[PRIMARY]");
					else
						builder.name(tableSpace.getName());
					first = false;
				}
			}
			builder.space();
		});
	}

	@Override
	protected void addOptions(PartitionScheme scheme, List<SqlOperation> result) {
		if (SqlServerPartitionOptions.getNextUsed(scheme) != null)
			result.addAll(getSqlFactoryRegistry().createSql(scheme, SqlType.ALTER));
	}
}
