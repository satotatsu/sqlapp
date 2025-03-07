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

import com.sqlapp.data.schemas.DbObject;
import com.sqlapp.util.AbstractSqlBuilder;

/**
 * スキーマ検索パス設定SQL 生成クラス
 * 
 * @author satoh
 * 
 */
public abstract class AbstractSetSearchPathToSchemaFactory<T extends DbObject<?>, S extends AbstractSqlBuilder<?>>
		extends SimpleSqlFactory<T, S> {

	@Override
	public List<SqlOperation> createSql(final T obj) {
		List<SqlOperation> sqlList = list();
		S builder = createSqlBuilder();
		addSetSearchPath(obj, builder);
		addSql(sqlList, builder, SqlType.SET_SEARCH_PATH_TO_SCHEMA, obj);
		return sqlList;
	}

	protected abstract void addSetSearchPath(final T obj, S builder);

}
