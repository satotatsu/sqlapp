/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core.
 *
 * sqlapp-core is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.sql;

import static com.sqlapp.util.CommonUtils.list;

import java.util.List;

import com.sqlapp.data.schemas.Table;
import com.sqlapp.util.AbstractSqlBuilder;

/**
 * ANALYZE TABLE
 * 
 * @author tatsuo satoh
 * 
 * @param <S>
 */
public abstract class AbstractAnalyzeTableFactory<S extends AbstractSqlBuilder<?>>
		extends AbstractTableFactory<S> {

	@Override
	public List<SqlOperation> createSql(final Table obj) {
		final S builder = createSqlBuilder();
		final List<SqlOperation> sqlList = list();
		addAnalyzeTable(obj, builder);
		addSql(sqlList, builder, SqlType.ANALYZE, obj);
		return sqlList;
	}

	protected void addAnalyzeTable(final Table obj, final S builder) {
		builder.analyze().table();
		builder.name(obj, this.getOptions().isDecorateSchemaName());
		this.addTableComment(obj, builder);
	}
}
