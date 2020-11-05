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
package com.sqlapp.data.db.dialect;

import static com.sqlapp.util.DbUtils.getDatabaseMetaData;

import java.io.Serializable;
import java.lang.reflect.Modifier;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.sqlapp.data.db.dialect.resolver.ProductNameDialectResolver;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.util.ClassFinder;
import com.sqlapp.util.DbUtils;
import com.sqlapp.util.SimpleBeanUtils;

/**
 * DB方言のファクトリ
 * 
 * @author satoh
 * 
 */
public class DialectResolver implements Serializable {
	protected static final Logger logger = LogManager
			.getLogger(DialectResolver.class);
	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = 1L;

	private List<ProductNameDialectResolver> resolverList = new ArrayList<ProductNameDialectResolver>();

	private Map<String, ProductNameDialectResolver> resolverMap = new ConcurrentHashMap<String, ProductNameDialectResolver>();

	private static final DialectResolver instance = new DialectResolver();

	private static final Dialect DEFAULT_DIALECT=new Dialect(null);
	
	public static DialectResolver getInstance() {
		return instance;
	}

	private DialectResolver() {
		initializeResolverList();
	}

	protected List<ProductNameDialectResolver> getResolverList() {
		return resolverList;
	}

	protected synchronized void initializeResolverList() {
		if (resolverList.size() > 0) {
			return;
		}
		List<Class<? extends ProductNameDialectResolver>> classes = getResolvers(Thread
				.currentThread().getContextClassLoader());
		if (classes.size() == 0) {
			classes = getResolvers(DialectResolver.class.getClassLoader());
		}
		for (Class<? extends ProductNameDialectResolver> clazz : classes) {
			ProductNameDialectResolver resolver = SimpleBeanUtils
					.newInstance(clazz);
			resolverList.add(resolver);
		}
		Collections.sort(resolverList);
		logger.debug("resolverList=" + resolverList);
		logger.debug("resolverList.size()=" + resolverList.size());
	}

	private List<Class<? extends ProductNameDialectResolver>> getResolvers(
			ClassLoader classLoader) {
		ClassFinder finder = new ClassFinder(classLoader);
		finder.setFilter(new Predicate<Class<?>>() {
			@Override
			public boolean test(Class<?> obj) {
				if (Modifier.isAbstract(obj.getModifiers())) {
					return false;
				}
				if (!ProductNameDialectResolver.class.isAssignableFrom(obj)) {
					return false;
				}
				return true;
			}
		});
		List<Class<? extends ProductNameDialectResolver>> classes = finder
				.find(ProductNameDialectResolver.class.getPackage().getName());
		return classes;
	}

	/**
	 * Dialectの取得
	 * 
	 * @param connection
	 */
	public Dialect getDialect(final Connection connection) {
		return getDialect(getDatabaseMetaData(connection));
	}

	/**
	 * Dialectの取得
	 * 
	 * @param databaseMetaData
	 */
	public Dialect getDialect(final DatabaseMetaData databaseMetaData) {
		ProductVersionInfo productVersionInfo = DbUtils
				.getProductVersionInfo(databaseMetaData);
		return getDialect(productVersionInfo.getName(),
				productVersionInfo.getMajorVersion(),
				productVersionInfo.getMinorVersion(),
				productVersionInfo.getRevision());
	}

	/**
	 * Dialectの取得
	 * 
	 * @param dbProductName
	 * @param majorVersion
	 * @param minorVersion
	 */
	public Dialect getDialect(String dbProductName, int majorVersion,
			int minorVersion) {
		return getDialect(dbProductName, majorVersion, minorVersion, null);
	}

	public Dialect getDefaultDialect() {
		return DEFAULT_DIALECT;
	}

	/**
	 * Dialectを取得します
	 * 
	 * @param dbProductName
	 * @param majorVersion
	 * @param minorVersion
	 * @param revision
	 */
	public Dialect getDialect(String dbProductName, int majorVersion,
			int minorVersion, Integer revision) {
		ProductNameDialectResolver resolver = null;
		if (dbProductName!=null){
			dbProductName = dbProductName.trim();
		}
		if (dbProductName!=null&&getResolverMap().containsKey(dbProductName)) {
			resolver = getResolverMap().get(dbProductName);
			return resolver.getDialect(majorVersion, minorVersion,
					revision);
		}
		int size = getResolverList().size();
		for (int i = 0; i < size; i++) {
			resolver = getResolverList().get(i);
			Dialect dialect = resolver.getDialect(dbProductName, majorVersion,
					minorVersion, revision);
			if (dialect != null) {
				getResolverMap().put(dbProductName, resolver);
				return dialect;
			}
		}
		return getDefaultDialect();
	}

	public Map<String, ProductNameDialectResolver> getResolverMap() {
		return resolverMap;
	}
}
