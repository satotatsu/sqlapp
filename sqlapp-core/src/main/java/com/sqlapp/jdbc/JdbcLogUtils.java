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
package com.sqlapp.jdbc;

import com.sqlapp.thread.ThreadContext;

public class JdbcLogUtils {
	/**
	 * SQLのログ出力
	 * 
	 * @param sql
	 */
	public static void logSql(AbstractJdbc<?> jdbc, final String sql, long start, long end) {
		if (jdbc==null){
			return;
		}
		if (jdbc.isSqlLogEnabled()) {
			org.apache.logging.log4j.ThreadContext.put("process_time", "" + (end - start));
			ThreadContext.setSql(sql);
			jdbc.info(sql);
		}
	}
	
	/**
	 * SQLのログ出力
	 * 
	 * @param jdbc
	 * @param value
	 */
	public static void info(AbstractJdbc<?> jdbc, String value) {
		if (jdbc==null){
			return;
		}
		jdbc.info(value);
	}
}
