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
import com.sqlapp.data.db.sql.AbstractMergeFactory;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Table;

public class OracleMergeFactory extends AbstractMergeFactory<OracleSqlBuilder> {

	@Override
	protected void addUsingColumnNameAlias(Column column, final OracleSqlBuilder builder) {
		if (!this.getDialect().supportsValues()) {
			builder.as().name(column);
		}
	}

	@Override
	protected void addUsingSourceColumns(final Table obj, List<Column> columns, final OracleSqlBuilder builder) {
		if (this.getDialect().supportsValues()) {
			super.addUsingSourceColumns(obj, columns, builder);
		}
	}

}
