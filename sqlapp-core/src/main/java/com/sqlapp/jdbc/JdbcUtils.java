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

package com.sqlapp.jdbc;

public class JdbcUtils {

	/**
	 * URLからJDBCドライバークラスを取得します
	 * 
	 * @param url
	 */
	public static String getDriverClassNameByUrl(String url) {
		return JdbcDriver.getDriverClassNameByUrl(url);
	}

	public static String getDriverClassNameByUrl(String url, ClassLoader classLoader) {
		return JdbcDriver.getDriverClassNameByUrl(url, classLoader);
	}

	/**
	 * 文字列のTransactionIsolationを数値に変換します
	 * 
	 * @param value TransactionIsolation
	 * @return <code>java.sql.Connection.TRANSACTION_NONE</code> or
	 *         <code>java.sql.Connection.TRANSACTION_READ_COMMITTED</code> or
	 *         <code>java.sql.Connection.TRANSACTION_READ_UNCOMMITTED</code> or
	 *         <code>java.sql.Connection.TRANSACTION_REPEATABLE_READ</code> or
	 *         <code>java.sql.Connection.TRANSACTION_SERIALIZABLE</code> or
	 *         <code>null</code>
	 */
	public static Integer getTransactionIsolation(String value) {
		if ("NONE".equalsIgnoreCase(value) || "TRANSACTION_NONE".equalsIgnoreCase(value)) {
			return java.sql.Connection.TRANSACTION_NONE;
		} else if ("READ_COMMITTED".equalsIgnoreCase(value) || "TRANSACTION_READ_COMMITTED".equalsIgnoreCase(value)) {
			return java.sql.Connection.TRANSACTION_READ_COMMITTED;
		} else if ("READ_UNCOMMITTED".equalsIgnoreCase(value)
				|| "TRANSACTION_READ_UNCOMMITTED".equalsIgnoreCase(value)) {
			return java.sql.Connection.TRANSACTION_READ_UNCOMMITTED;
		} else if ("REPEATABLE_READ".equalsIgnoreCase(value) || "TRANSACTION_REPEATABLE_READ".equalsIgnoreCase(value)) {
			return java.sql.Connection.TRANSACTION_REPEATABLE_READ;
		} else if ("SERIALIZABLE".equalsIgnoreCase(value) || "TRANSACTION_SERIALIZABLE".equalsIgnoreCase(value)) {
			return java.sql.Connection.TRANSACTION_SERIALIZABLE;
		}
		return null;
	}

}
