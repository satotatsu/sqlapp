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

import static com.sqlapp.util.CommonUtils.concurrentMap;
import static com.sqlapp.util.CommonUtils.trim;
import static com.sqlapp.util.FileUtils.readText;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.Serializable;
import java.util.concurrent.ConcurrentMap;

import com.sqlapp.jdbc.sql.SqlParser;
import com.sqlapp.jdbc.sql.node.SqlNode;
import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.FileUtils;

public class SqlNodeCache implements Serializable {
	/** serialVersionUID */
	private static final long serialVersionUID = 396963923069427429L;

	private ConcurrentMap<String, SqlNode> sqlMap = CommonUtils
			.concurrentMap();

	private Class<?> baseClass;

	private static ConcurrentMap<Class<?>, SqlNodeCache> instancePool = concurrentMap();

	public static SqlNodeCache getInstance(final Class<?> clazz) {
		SqlNodeCache sqlNodeCache = instancePool.get(clazz);
		if (sqlNodeCache != null) {
			return sqlNodeCache;
		}
		sqlNodeCache = new SqlNodeCache(clazz);
		SqlNodeCache oldValue = instancePool.putIfAbsent(clazz, sqlNodeCache);
		return oldValue != null ? oldValue : sqlNodeCache;
	}

	protected SqlNodeCache(final Class<?> clazz) {
		this.baseClass = clazz;
	}

	public SqlNode getString(String sqlFile) {
		SqlNode node = sqlMap.get(sqlFile);
		if (node != null) {
			return node;
		}
		Class<?> clazz=this.baseClass;
		while(true) {
			node=getStringInternal(clazz, sqlFile);
			if (node!=null) {
				break;
			} else {
				clazz=clazz.getSuperclass();
				if (clazz==null) {
					throw new RuntimeException(new FileNotFoundException("path="
							+ getBasePath(this.baseClass) + sqlFile));
				}
			}
		}
		SqlNode org = sqlMap.putIfAbsent(sqlFile, node);
		return org != null ? org : node;
	}

	private SqlNode getStringInternal(Class<?> clazz, String sqlFile) {
		InputStream inp = FileUtils.getInputStream(clazz, sqlFile);
		String sql = null;
		try {
			String basePath=getBasePath(clazz);
			if (inp == null) {
				inp = FileUtils.getInputStream(basePath + sqlFile);
			}
			if (inp==null) {
				return null;
			}
			sql = readText(inp, "utf8");
		} finally {
			FileUtils.close(inp);
		}
		SqlNode node = SqlParser.getInstance().parse(trim(sql));
		return node;
	}

	private String getBasePath(final Class<?> clazz) {
		return clazz.getPackage().getName().replace('.', '/') + "/";
	}
	
}
