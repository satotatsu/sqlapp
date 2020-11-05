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
				if (!this.contains(entry.getKey(), sqlEntry.getKey())) {
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
		String databaseProductName = getProductName(dialect);
		ConcurrentMap<String, List<SqlNode>> sqlIdMap = this.sqlIdMap;
		if (databaseProductName == null) {
			List<SqlNode> nodes = sqlIdMap.get(sqlId);
			if (CommonUtils.isEmpty(nodes)) {
				nodes = this.getSqlRegistry().getAll(sqlId);
				if (CommonUtils.isEmpty(nodes)) {
					List<SqlNode> org = sqlIdMap.putIfAbsent(sqlId, nodes);
					return org != null ? org : nodes;
				} else {
					return null;
				}
			}
			return nodes;
		} else {
			sqlIdMap = dialectSqlIdMap.get(databaseProductName);
			if (sqlIdMap == null) {
				sqlIdMap = CommonUtils.concurrentMap();
				ConcurrentMap<String, List<SqlNode>> org = dialectSqlIdMap.putIfAbsent(
						databaseProductName, sqlIdMap);
				if (org != null) {
					sqlIdMap = org;
				}
			}
			List<SqlNode> nodes = sqlIdMap.get(sqlId);
			if (CommonUtils.isEmpty(nodes)) {
				nodes = this.getSqlRegistry().getAll(sqlId, dialect);
				if (CommonUtils.isEmpty(nodes)) {
					List<SqlNode> org = sqlIdMap.putIfAbsent(sqlId, nodes);
					return org != null ? org : nodes;
				} else {
					return null;
				}
			} else {
				return nodes;
			}
		}
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
		this.getSqlRegistry().put(sqlId, dialect, sql);
		String databaseProductName = getProductName(dialect);
		ConcurrentMap<String, List<SqlNode>> sqlIdMap = this.sqlIdMap;
		if (databaseProductName == null) {
			sqlIdMap.remove(sqlId);
		} else {
			sqlIdMap = dialectSqlIdMap.get(databaseProductName);
			if (sqlIdMap == null) {
				sqlIdMap = this.sqlIdMap;
			}
			if (sqlIdMap != null) {
				sqlIdMap.remove(sqlId);
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
		this.getSqlRegistry().put(sqlId, sql, null);
		ConcurrentMap<String, List<SqlNode>> sqlIdMap = this.sqlIdMap;
		sqlIdMap.remove(sqlId);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.sql.SqlRegistry#remove(java.lang.String,
	 * com.sqlapp.data.db.dialect.Dialect)
	 */
	@Override
	public void remove(String sqlId, Dialect dialect) {
		String databaseProductName = getProductName(dialect);
		if (databaseProductName != null) {
			this.getSqlRegistry().remove(sqlId, dialect);
			ConcurrentMap<String, List<SqlNode>> sqlIdMap = dialectSqlIdMap
					.get(databaseProductName);
			if (sqlIdMap != null) {
				sqlIdMap.remove(sqlId);
			}
		} else {
			this.remove(sqlId);
		}
	}

	@Override
	public void remove(String sqlId) {
		this.getSqlRegistry().remove(sqlId);
		this.sqlIdMap.remove(sqlId);
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
		return sqlIdMap.containsKey(sqlId);
	}

	/**
	 * @param sqlId
	 * @param databaseProductName
	 */
	@Override
	public boolean contains(String sqlId, String databaseProductName) {
		ConcurrentMap<String, List<SqlNode>> sqlIdMap = this.sqlIdMap;
		if (databaseProductName == null) {
			return sqlIdMap.containsKey(sqlId);
		} else {
			sqlIdMap = dialectSqlIdMap.get(databaseProductName);
			if (sqlIdMap == null) {
				sqlIdMap = this.sqlIdMap;
			}
			if (sqlIdMap != null) {
				return sqlIdMap.containsKey(sqlId);
			}
			return false;
		}
	}



}
