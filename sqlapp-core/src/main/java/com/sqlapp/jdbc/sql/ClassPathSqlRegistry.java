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

import static com.sqlapp.util.FileUtils.combinePath;
import static com.sqlapp.util.FileUtils.readText;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;

import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.FileUtils;
import com.sqlapp.util.ModuleHelper;

public class ClassPathSqlRegistry extends AbstractSqlRegistry {
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
	/**
	 * SQLファイルの拡張子
	 */
	private final Class<?> clazz;

	/**
	 * 
	 * @param clazz
	 */
	public ClassPathSqlRegistry(Class<?> clazz) {
		this.clazz = clazz;
		setBasePath(clazz.getPackage().getName().replace('.', '/') + "/");
	}

	/**
	 * 
	 * @param basePath
	 */
	public ClassPathSqlRegistry(String basePath) {
		this.clazz = null;
		setBasePath(basePath);
	}

	/**
	 * 
	 */
	public ClassPathSqlRegistry() {
		this.clazz = null;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.sql.SqlRegistry#contains(java.lang.String)
	 */
	@Override
	public boolean contains(String sqlId) {
		String path = combinePath(getBasePath(), sqlId + getExtension());
		InputStream inp = null;
		try {
			String val = getByPath(path);
			if (val == null) {
				return false;
			}
			return true;
		} finally {
			FileUtils.close(inp);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.sql.SqlRegistry#contains(java.lang.String,
	 * java.lang.String)
	 */
	@Override
	public boolean contains(String sqlId, String databaseProductName) {
		String val = getByPath(combinePath(getBasePath(), databaseProductName, sqlId + getExtension()));
		if (val == null) {
			return false;
		}
		return true;
	}

	@Override
	protected List<String> getInternal(String sqlId) {
		String path = combinePath(getBasePath(), sqlId + getExtension());
		String sql = getByPath(path);
		return splitSql(sql);
	}

	protected static List<String> splitSql(String sql) {
		if (sql == null) {
			return Collections.emptyList();
		}
		String[] args = sql.split(";");
		return CommonUtils.list(args);
	}

	@Override
	protected List<String> getInternal(String sqlId, String databaseProductName) {
		String path = combinePath(getBasePath(), databaseProductName, sqlId + getExtension());
		String sql = getByPath(path);
		return splitSql(sql);
	}

	private InputStream getStream(String path) throws IOException {
		InputStream inp = null;
		if (clazz != null) {
			inp = clazz.getResourceAsStream(path);
		}
		if (inp == null) {
			String val = path.replace(clazz.getPackageName().replace(".", "/"), "");
			if (val.startsWith("/")) {
				val = val.substring(1);
			}
			inp = clazz.getResourceAsStream(val);
		}
		if (inp == null) {
			inp = Thread.currentThread().getContextClassLoader().getResourceAsStream(path);
		}
		if (inp == null) {
			inp = ClassLoader.getSystemResourceAsStream(path);
		}
		if (inp == null) {
			inp = ModuleHelper.getInstance().getResourceAsStream(path);
		}
		return inp;
	}

	private String getByPath(String path) {
		InputStream inp = null;
		try {
			inp = getStream(path);
			if (inp == null) {
				return null;
			}
			String sql = readText(inp, "utf8");
			return sql;
		} catch (IOException e) {
			throw new RuntimeException(e);
		} finally {
			FileUtils.close(inp);
		}
	}

	@Override
	protected void putInternal(String sqlId, String databaseProductName, String... sql) {
	}

	@Override
	protected void removeInternal(String sqlId) {
	}

	@Override
	protected void removeInternal(String sqlId, String databaseProductName) {
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
