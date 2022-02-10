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

package com.sqlapp.data.db.metadata;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.sqlapp.data.converter.Converters;
import com.sqlapp.util.CommonUtils;

public class ReaderUtils {

	protected static Boolean toBoolean(String value) {
		return Converters.getDefault().convertObject(value, Boolean.class);
	}

	/**
	 * ResultSetから指定したカラムのInteger値を取得します
	 * 
	 * @param rs
	 * @param name
	 * @throws SQLException
	 */
	protected static Integer getInteger(ResultSet rs, String name)
			throws SQLException {
		Integer val = Converters.getDefault().convertObject(rs.getObject(name),
				Integer.class);
		return val;
	}

	/**
	 * ResultSetから指定したカラムのLong値を取得します
	 * 
	 * @param rs
	 * @param name
	 * @throws SQLException
	 */
	protected static Long getLong(ResultSet rs, String name)
			throws SQLException {
		long ret = rs.getLong(name);
		if (rs.wasNull()) {
			return null;
		}
		return ret;
	}

	/**
	 * ResultSetから指定したカラムのBoolean値を取得します
	 * 
	 * @param rs
	 * @param name
	 * @throws SQLException
	 */
	protected static Boolean getBoolean(ResultSet rs, String name)
			throws SQLException {
		boolean ret = rs.getBoolean(name);
		if (rs.wasNull()) {
			return null;
		}
		return ret;
	}

	private static final Pattern VIEW_PATTERN = Pattern.compile(
			".*create.*\\s+view\\s+.*as\\s+(.*)", Pattern.CASE_INSENSITIVE
					+ Pattern.MULTILINE + Pattern.DOTALL);

	/**
	 * ビュー定義からステートメント部分を抽出します
	 * 
	 * @param definition
	 *            ビュー定義
	 * @return ビュー定義のステートメント
	 */
	public static String getViewStatement(String definition) {
		if(definition==null){
			return definition;
		}
		definition = CommonUtils.rtrim(definition);
		definition = CommonUtils.rtrim(definition, ';');
		Matcher matcher = VIEW_PATTERN.matcher(definition);
		if (matcher.matches()) {
			return matcher.group(1);
		}
		return definition;
	}

}
