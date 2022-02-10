/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
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
 * along with sqlapp-core.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.sqlapp.jdbc.sql;

import static com.sqlapp.util.CommonUtils.concurrentMap;

import java.util.List;
import java.util.concurrent.ConcurrentMap;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.util.CommonUtils;

public class MapSqlRegistry extends AbstractSqlRegistry {
	/**
	 * DBプロダクト毎のSQLのキャッシュ
	 */
	private ConcurrentMap<String, ConcurrentMap<String, List<String>>> dialectSqlIdMap = concurrentMap();

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.sql.SqlRegistry#contains(java.lang.String)
	 */
	@Override
	public boolean contains(String sqlId) {
		ConcurrentMap<String, List<String>> map = getDialectMap(Dialect.DefaultDbType);
		return map.containsKey(sqlId);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.sql.SqlRegistry#contains(java.lang.String,
	 * java.lang.String)
	 */
	@Override
	public boolean contains(String sqlId, String databaseProductName) {
		ConcurrentMap<String, List<String>> map = getDialectMap(databaseProductName);
		return map.containsKey(sqlId);
	}

	@Override
	protected List<String> getInternal(String sqlId) {
		ConcurrentMap<String, List<String>> map = getDialectMap(Dialect.DefaultDbType);
		return map.get(sqlId);
	}

	@Override
	protected List<String> getInternal(String sqlId, String databaseProductName) {
		ConcurrentMap<String, List<String>> map = getDialectMap(databaseProductName);
		return map.get(sqlId);
	}

	@Override
	protected void putInternal(String sqlId, String databaseProductName, String... sql) {
		ConcurrentMap<String, List<String>> map = getDialectMap(databaseProductName);
		map.put(sqlId, CommonUtils.list(sql));
	}

	private ConcurrentMap<String, List<String>> getDialectMap(
			String databaseProductName) {
		if (databaseProductName==null){
			databaseProductName=Dialect.DefaultDbType;
		}
		ConcurrentMap<String, List<String>> map = dialectSqlIdMap
				.get(databaseProductName);
		if (map == null) {
			map = concurrentMap();
			ConcurrentMap<String, List<String>> org = dialectSqlIdMap.putIfAbsent(
					databaseProductName, map);
			return org != null ? org : map;
		}
		return map;
	}

	@Override
	protected void removeInternal(String sqlId) {
		ConcurrentMap<String, List<String>> map = getDialectMap(Dialect.DefaultDbType);
		map.remove(sqlId);
	}

	@Override
	protected void removeInternal(String sqlId, String databaseProductName) {
		ConcurrentMap<String, List<String>> map = getDialectMap(databaseProductName);
		map.remove(sqlId);
	}

}
