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

import java.util.Collections;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.jdbc.sql.node.SqlNode;
import com.sqlapp.util.CommonUtils;

/**
 * SQLを管理するクラス
 * 
 * @author SATOH
 * 
 */
public abstract class AbstractSqlRegistry implements SqlRegistry {
	protected static final Logger loggger = LogManager
			.getLogger(AbstractSqlRegistry.class);

	private SqlParser sqlParser = SqlParser.getInstance();

	public AbstractSqlRegistry() {
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.sql.SqlRegistry#get(java.lang.String,
	 * com.sqlapp.data.db.dialect.Dialect)
	 */
	@Override
	public SqlNode get(String sqlId, Dialect dialect) {
		return CommonUtils.first(getAll(sqlId, dialect));
	}

	/* (non-Javadoc)
	 * @see com.sqlapp.data.sql.SqlRegistry#getAll(java.lang.String, com.sqlapp.data.db.dialect.Dialect)
	 */
	@Override
	public List<SqlNode> getAll(String sqlId, Dialect dialect) {
		String databaseProductName = getProductName(dialect);
		List<String> sqls=null;
		if (databaseProductName == null) {
			sqls = getInternal(sqlId);
			if (CommonUtils.isEmpty(sqls)) {
				return Collections.emptyList();
			}
		} else {
			sqls = getInternal(sqlId, databaseProductName);
			if (CommonUtils.isEmpty(sqls)) {
				sqls = getInternal(sqlId);
			}
			if (CommonUtils.isEmpty(sqls)) {
				return Collections.emptyList();
			}
		}
		List<SqlNode> result=CommonUtils.list(sqls.size());
		for(String sql:sqls){
			if (!CommonUtils.isEmpty(sql)){
				SqlNode node = this.getSqlParser().parse(sql);
				result.add(node);
			}
		}
		return result;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.sql.SqlRegistry#get(java.lang.String)
	 */
	@Override
	public SqlNode get(String sqlId) {
		return CommonUtils.first(getAll(sqlId));
	}


	@Override
	public List<SqlNode> getAll(String sqlId) {
		return getAll(sqlId, null);
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

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.sql.SqlRegistry#put(java.lang.String,
	 * java.lang.String, com.sqlapp.data.db.dialect.Dialect)
	 */
	@Override
	public void put(String sqlId, Dialect dialect, String sql) {
		String databaseProductName = getProductName(dialect);
		if (databaseProductName == null) {
			if (contains(sqlId)) {
				removeInternal(sqlId);
			}
			putInternal(sqlId, sql);
		} else {
			if (contains(sqlId, databaseProductName)) {
				removeInternal(sqlId, databaseProductName);
			}
			putInternal(sqlId, sql, databaseProductName);
		}
	}

	
	@Override
	public void put(String sqlId, Dialect dialect, String... sql) {
		String databaseProductName = getProductName(dialect);
		if (databaseProductName == null) {
			if (contains(sqlId)) {
				removeInternal(sqlId);
			}
			putInternal(sqlId,null, sql);
		} else {
			if (contains(sqlId, databaseProductName)) {
				removeInternal(sqlId, databaseProductName);
			}
			putInternal(sqlId, databaseProductName, sql);
		}
		
	}

	
	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.sql.SqlRegistry#put(java.lang.String,
	 * java.lang.String)
	 */
	@Override
	public void put(String sqlId, String sql) {
		this.put(sqlId, sql, null);
	}

	@Override
	public void put(String sqlId, String... sql) {
		this.putInternal(sqlId, null, sql);
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
		if (databaseProductName == null) {
			if (contains(sqlId)) {
				removeInternal(sqlId);
			}
		} else {
			if (contains(sqlId, databaseProductName)) {
				removeInternal(sqlId, databaseProductName);
			}
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.sql.SqlRegistry#remove(java.lang.String)
	 */
	@Override
	public void remove(String sqlId) {
		this.remove(sqlId, null);
	}

	/**
	 * DB共通のSQLを取得するメソッド
	 * 
	 * @param sqlId
	 *            SQLのID
	 */
	protected abstract List<String> getInternal(String sqlId);

	/**
	 * DB毎のSQLを取得するメソッド
	 * 
	 * @param sqlId
	 *            SQLのID
	 * @param databaseProductName
	 */
	protected abstract List<String> getInternal(String sqlId,
			String databaseProductName);

	/**
	 * DB共通のSQLが存在するかの判定
	 * 
	 * @param sqlId
	 *            SQLのID
	 */
	public abstract boolean contains(String sqlId);

	/**
	 * DB毎のSQLが存在するかの判定
	 * 
	 * @param sqlId
	 *            SQLのID
	 * @param databaseProductName
	 */
	public abstract boolean contains(String sqlId, String databaseProductName);

	/**
	 * DB共通のSQLを削除するメソッド
	 * 
	 * @param sqlId
	 *            SQLのID
	 */
	protected abstract void removeInternal(String sqlId);

	/**
	 * DB毎のSQLを削除するメソッド
	 * 
	 * @param sqlId
	 *            SQLのID
	 * @param databaseProductName
	 */
	protected abstract void removeInternal(String sqlId,
			String databaseProductName);


	/**
	 * DB共通のSQLを格納するメソッド
	 * 
	 * @param sqlId
	 *            SQLのID
	 * @param databaseProductName
	 *            DB製品名
	 * @param sql
	 *            SQLの文字列
	 */
	protected abstract void putInternal(String sqlId, String databaseProductName, String... sql);

	/**
	 * @return the sqlParser
	 */
	public SqlParser getSqlParser() {
		return sqlParser;
	}

	/**
	 * @param sqlParser
	 *            the sqlParser to set
	 */
	public void setSqlParser(SqlParser sqlParser) {
		this.sqlParser = sqlParser;
	}






}
