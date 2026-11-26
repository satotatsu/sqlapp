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

package com.sqlapp.jdbc.sql;

import static com.sqlapp.util.CommonUtils.concurrentMap;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.jdbc.sql.node.SqlNode;
import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.DoubleKeyMap;

/**
 * SQLを管理するクラス
 * 
 * @author SATOH
 * 
 */
public class CachedSqlRegistry implements SqlRegistry {

	/**
	 * DBプロダクト毎のSQLのキャッシュ
	 */
	private ConcurrentMap<String, ConcurrentMap<String, List<SqlNode>>> dialectSqlIdMap = concurrentMap();

	/**
	 * デフォルトのSQLのキャッシュ
	 */
	private ConcurrentMap<String, List<SqlNode>> sqlIdMap = concurrentMap();
	/**
	 * SQLレジストリ
	 */
	private SqlRegistry sqlRegistry = null;

	public CachedSqlRegistry(SqlRegistry sqlRegistry) {
		this.sqlRegistry = sqlRegistry;
	}

	/**
	 * @return the sqlRegistry
	 */
	public SqlRegistry getSqlRegistry() {
		return sqlRegistry;
	}

	/**
	 * @param sqlRegistry
	 *            the sqlRegistry to set
	 */
	public void setSqlRegistry(SqlRegistry sqlRegistry) {
		this.sqlRegistry = sqlRegistry;
		sqlIdMap.clear();
		dialectSqlIdMap.clear();
	}

	/**
	 * 不要なキャッシュを削除します。
	 */
	public void gcCache() {
		Set<String> removeTarget = CommonUtils.set();
		for (Map.Entry<String, List<SqlNode>> entry : sqlIdMap.entrySet()) {
			if (!this.contains(entry.getKey())) {
				removeTarget.add(entry.getKey());
			}
		}
		for (String key : removeTarget) {
			sqlIdMap.remove(key);
		}
		DoubleKeyMap<String, String, List<SqlNode>> doubleKeyMap = new DoubleKeyMap<String, String, List<SqlNode>>();
		for (Map.Entry<String, ConcurrentMap<String, List<SqlNode>>> entry : dialectSqlIdMap
				.entrySet()) {
			for (Map.Entry<String, List<SqlNode>> sqlEntry : entry.getValue()
					.entrySet()) {
				if (!this.contains(sqlEntry.getKey(), entry.getKey())) {
					doubleKeyMap.put(entry.getKey(), sqlEntry.getKey(),
							sqlEntry.getValue());
				}
			}
		}
		for (Map.Entry<String, Map<String, List<SqlNode>>> entry : doubleKeyMap
				.entrySet()) {
			for (Map.Entry<String, List<SqlNode>> sqlEntry : entry.getValue()
					.entrySet()) {
				ConcurrentMap<String, List<SqlNode>> map=dialectSqlIdMap.get(entry.getKey());
				if (map!=null){
					map.remove(sqlEntry.getKey());
				}
			}
		}
		removeTarget.clear();
		for (Map.Entry<String, ConcurrentMap<String, List<SqlNode>>> entry : dialectSqlIdMap
				.entrySet()) {
			if (entry.getValue().isEmpty()) {
				removeTarget.add(entry.getKey());
			}
		}
		for (String key : removeTarget) {
			dialectSqlIdMap.remove(key);
		}
	}

	/**
	 * IDを指定してSQLを取得します
	 * 
	 * @param sqlId
	 *            SQLのID
	 * @param dialect
	 *            DB dialect
	 * @return SQL
	 */
	@Override
	public List<SqlNode> getAll(String sqlId, Dialect dialect) {
		String product = getProductName(dialect);
		ConcurrentMap<String, List<SqlNode>> cache = product == null ? sqlIdMap
				: dialectSqlIdMap.computeIfAbsent(product, key -> concurrentMap());
		return cache.computeIfAbsent(sqlId, key -> {
			List<SqlNode> nodes = getSqlRegistry().getAll(key, dialect);
			return nodes == null ? java.util.Collections.emptyList() : nodes;
		});
	}


	@Override
	public List<SqlNode> getAll(String sqlId) {
		return getAll(sqlId, null);
	}
	
	@Override
	public SqlNode get(String sqlId, Dialect dialect) {
		return CommonUtils.first(getAll(sqlId, dialect));
	}

	@Override
	public SqlNode get(String sqlId) {
		return CommonUtils.first(getAll(sqlId));
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.sql.SqlRegistry#put(java.lang.String,
	 * java.lang.String, com.sqlapp.data.db.dialect.Dialect)
	 */
	@Override
	public void put(String sqlId, Dialect dialect, String... sql) {
		getSqlRegistry().put(sqlId, dialect, sql);
		invalidate(sqlId, dialect);
	}

	private void invalidate(String sqlId, Dialect dialect) {
		String product = getProductName(dialect);
		if (product == null) {
			sqlIdMap.remove(sqlId);
			// Product caches may contain a fallback to the default SQL.
			dialectSqlIdMap.values().forEach(cache -> cache.remove(sqlId));
		} else {
			ConcurrentMap<String, List<SqlNode>> cache = dialectSqlIdMap.get(product);
			if (cache != null) {
				cache.remove(sqlId);
			}
		}
	}



	@Override
	public void put(String sqlId, Dialect dialect, String sql) {
		put(sqlId, dialect, new String[]{sql});
	}

	@Override
	public void put(String sqlId, String... sql) {
		put(sqlId, null, sql);
	}
	
	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.sql.SqlRegistry#put(java.lang.String,
	 * java.lang.String)
	 */
	@Override
	public void put(String sqlId, String sql) {
		put(sqlId, (Dialect) null, sql);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.sql.SqlRegistry#remove(java.lang.String,
	 * com.sqlapp.data.db.dialect.Dialect)
	 */
	@Override
	public void remove(String sqlId, Dialect dialect) {
		getSqlRegistry().remove(sqlId, dialect);
		invalidate(sqlId, dialect);
	}

	@Override
	public void remove(String sqlId) {
		remove(sqlId, null);
	}

	protected String getProductName(Dialect dialect) {
		String databaseProductName;
		if (dialect == null) {
			return null;
		} else {
			databaseProductName = dialect.getProductName();
		}
		return databaseProductName.toLowerCase();
	}

	/**
	 * @param sqlId
	 */
	@Override
	public boolean contains(String sqlId) {
		return getSqlRegistry().contains(sqlId);
	}

	@Override
	public boolean contains(String sqlId, String databaseProductName) {
		return getSqlRegistry().contains(sqlId, databaseProductName);
	}
}
