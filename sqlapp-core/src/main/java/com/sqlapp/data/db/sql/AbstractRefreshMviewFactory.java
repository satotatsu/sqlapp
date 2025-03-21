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

import com.sqlapp.data.schemas.Mview;
import com.sqlapp.util.AbstractSqlBuilder;

/**
 * Mveiw Refresh SQL 生成クラス
 * 
 * @author satoh
 * 
 */
public abstract class AbstractRefreshMviewFactory<S extends AbstractSqlBuilder<?>>
		extends SimpleSqlFactory<Mview, S> {

	@Override
	public List<SqlOperation> createSql(final Mview obj) {
		final List<SqlOperation> sqlList = list();
		final S builder = createSqlBuilder();
		addRefreshObject(obj, builder);
		addSql(sqlList, builder, SqlType.REFRESH, obj);
		return sqlList;
	}

	protected void addRefreshObject(final Mview obj, final S builder) {
		builder.alter().materialized().view();
		builder.name(obj);
		this.addTableComment(obj, builder);
		builder.refresh();
	}

}
