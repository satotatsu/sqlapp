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

package com.sqlapp.data.db.dialect.hsql.sql;

import com.sqlapp.data.db.dialect.hsql.util.HsqlSqlBuilder;
import com.sqlapp.data.db.sql.AbstractMergeRowsFactory;
import com.sqlapp.data.db.sql.SqlSignature;
import com.sqlapp.data.schemas.Table;

public class Hsql2MergeRowsFactory extends AbstractMergeRowsFactory<HsqlSqlBuilder> {

	@Override
	protected void addMergeTable(final Table obj, final SqlSignature sqlSignature, final HsqlSqlBuilder builder) {
		builder.select();
		builder.indent(() -> {
			for (int i = 0; i < obj.getColumns().size(); i++) {
				builder.lineBreak().comma(i > 0);
				builder.name(obj.getColumns().get(i));
			}
		});
		builder.lineBreak();
		builder.from().final_().table();
		builder.lineBreak();
		builder.brackets(true, () -> {
			super.addMergeTable(obj, sqlSignature, builder);
		});
	}
}
