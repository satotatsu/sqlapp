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

import static com.sqlapp.util.FileUtils.combinePath;
import static com.sqlapp.util.FileUtils.exists;
import static com.sqlapp.util.FileUtils.readText;
import static com.sqlapp.util.FileUtils.writeText;

import java.util.Collections;
import java.util.List;

public class FileSqlRegistry extends AbstractSqlRegistry {
	/**
	 * SQLを格納する基準となるパス
	 */
	private String basePath = null;
	/**
	 * SQLファイルのエンコーディング
	 */
	private String encoding = "UTF-8";
	/**
	 * SQLファイルの拡張子
	 */
	private String extension = ".sql";

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.sql.SqlRegistry#contains(java.lang.String)
	 */
	@Override
	public boolean contains(String sqlId) {
		String path = combinePath(getBasePath(), sqlId + getExtension());
		return exists(path);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.sql.SqlRegistry#contains(java.lang.String,
	 * java.lang.String)
	 */
	@Override
	public boolean contains(String sqlId, String databaseProductName) {
		String path = combinePath(getBasePath(), databaseProductName, sqlId
				+ getExtension());
		return exists(path);
	}

	@Override
	protected List<String> getInternal(String sqlId) {
		String path = combinePath(getBasePath(), sqlId + getExtension());
		if (exists(path)) {
			String sql= readText(path, getEncoding());
			return splitSql(sql);
		}
		return Collections.emptyList();
	}

	private static List<String> splitSql(String sql){
		return ClassPathSqlRegistry.splitSql(sql);
	}
	
	
	@Override
	protected List<String> getInternal(String sqlId, String databaseProductName) {
		String path = combinePath(getBasePath(), databaseProductName, sqlId
				+ getExtension());
		if (exists(path)) {
			String sql= readText(path, getEncoding());
			return splitSql(sql);
		}
		return Collections.emptyList();
	}

	@Override
	protected void putInternal(String sqlId,
			String databaseProductName, String... sql) {
		String path;
		if (databaseProductName==null){
			path = combinePath(getBasePath(), sqlId + getExtension());
		} else{
			path = combinePath(getBasePath(), databaseProductName, sqlId
					+ getExtension());
		}
		if (exists(path)) {
			com.sqlapp.util.FileUtils.remove(path);
		}
		writeText(path, getEncoding(), sql);
	}

	@Override
	protected void removeInternal(String sqlId) {
		String path = combinePath(getBasePath(), sqlId + getExtension());
		if (exists(path)) {
			com.sqlapp.util.FileUtils.remove(path);
		}
	}

	@Override
	protected void removeInternal(String sqlId, String databaseProductName) {
		String path = combinePath(getBasePath(), databaseProductName, sqlId
				+ getExtension());
		if (exists(path)) {
			com.sqlapp.util.FileUtils.remove(path);
			return;
		}
	}

	public String getBasePath() {
		return basePath;
	}

	public void setBasePath(String basePath) {
		this.basePath = basePath;
	}

	public String getEncoding() {
		return encoding;
	}

	public void setEncoding(String encoding) {
		this.encoding = encoding;
	}

	public String getExtension() {
		return extension;
	}

	public void setExtension(String extension) {
		this.extension = extension;
	}
}
