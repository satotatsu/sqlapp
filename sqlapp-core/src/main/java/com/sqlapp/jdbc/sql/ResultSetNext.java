package com.sqlapp.jdbc.sql;

import java.sql.SQLException;

import com.sqlapp.jdbc.ExResultSet;

/**
 * ExResultSet用のハンドラー
 */
@FunctionalInterface
public interface ResultSetNext {
	/**
	 * ResultSet のnextのたびに呼ばれます。
	 * 
	 * @param rs ExResultSet
	 * @throws SQLException
	 */
	void handleResultSetNext(ExResultSet rs) throws SQLException;
}
