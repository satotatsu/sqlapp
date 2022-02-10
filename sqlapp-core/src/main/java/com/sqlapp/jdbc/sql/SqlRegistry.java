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

import java.util.List;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.jdbc.sql.node.SqlNode;

public interface SqlRegistry {

	/**
	 * IDを指定してSQLを取得します
	 * 
	 * @param sqlId
	 *            SQLのID
	 * @param dialect
	 *            DB dialect
	 * @return SQL
	 */
	SqlNode	get(String sqlId, Dialect dialect);

	/**
	 * IDを指定してSQLを取得します
	 * 
	 * @param sqlId
	 *            SQLのID
	 * @param dialect
	 *            DB dialect
	 * @return SQL
	 */
	List<SqlNode> getAll(String sqlId, Dialect dialect);

	/**
	 * IDを指定してSQLを取得します
	 * 
	 * @param sqlId
	 *            SQLのID
	 * @return SQL
	 */
	SqlNode get(String sqlId);

	/**
	 * IDを指定してSQLを取得します
	 * 
	 * @param sqlId
	 *            SQLのID
	 * @return SQL
	 */
	List<SqlNode> getAll(String sqlId);
	
	/**
	 * IDを指定してSQLを保存する
	 * 
	 * @param sqlId
	 *            SQLのID
	 * @param dialect
	 *            dialect
	 * @param sql
	 *            SQL
	 */
	void put(String sqlId, Dialect dialect, String sql);

	/**
	 * IDを指定してSQLを保存する
	 * 
	 * @param sqlId
	 *            SQLのID
	 * @param dialect
	 *            dialect
	 * @param sql
	 *            SQL
	 */
	void put(String sqlId, Dialect dialect, String... sql);

	/**
	 * IDを指定してSQLを保存する
	 * 
	 * @param sqlId
	 *            SQLのID
	 * @param sql
	 *            SQL
	 */
	void put(String sqlId, String sql);

	/**
	 * IDを指定してSQLを保存する
	 * 
	 * @param sqlId
	 *            SQLのID
	 * @param sql
	 *            SQL
	 */
	void put(String sqlId, String... sql);

	/**
	 * IDを指定してSQLを削除します
	 * 
	 * @param sqlId
	 *            SQLのID
	 * @param dialect
	 *            DB dialect
	 */
	void remove(String sqlId, Dialect dialect);

	/**
	 * IDを指定してSQLを削除します
	 * 
	 * @param sqlId
	 *            SQLのID
	 */
	void remove(String sqlId);

	/**
	 * DB共通のSQLが存在するかの判定
	 * 
	 * @param sqlId
	 *            SQLのID
	 */
	boolean contains(String sqlId);

	/**
	 * DB毎のSQLが存在するかの判定
	 * 
	 * @param sqlId
	 *            SQLのID
	 * @param databaseProductName
	 */
	boolean contains(String sqlId, String databaseProductName);

}