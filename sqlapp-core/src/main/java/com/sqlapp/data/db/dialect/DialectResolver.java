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

package com.sqlapp.data.db.dialect;

import static com.sqlapp.util.DbUtils.getDatabaseMetaData;

import java.lang.reflect.Modifier;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.sqlapp.data.db.dialect.resolver.ProductNameDialectResolver;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.util.ClassFinder;
import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.DbUtils;
import com.sqlapp.util.SimpleBeanUtils;

/**
 * DB方言のファクトリ
 * 
 * @author satoh
 * 
 */
public class DialectResolver extends AbstractDialectResolver {
	protected static final Logger logger = LogManager.getLogger(DialectResolver.class);

	private final List<ProductNameDialectResolver> resolverList = new ArrayList<ProductNameDialectResolver>();

	private final Map<String, ProductNameDialectResolver> resolverMap = new ConcurrentHashMap<String, ProductNameDialectResolver>();

	private static final DialectResolver instance = new DialectResolver();

	private static final Dialect DEFAULT_DIALECT = new Dialect(null);

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
		List<ProductNameDialectResolver> list = getResolversByServiceLoader();
		if (!list.isEmpty()) {
			resolverList.addAll(list);
		} else {
			list = getResolvers();
			resolverList.addAll(list);
		}
		Collections.sort(resolverList);
		logger.debug("resolverList=" + resolverList);
		logger.debug("resolverList.size()=" + resolverList.size());
	}

	private List<ProductNameDialectResolver> getResolversByServiceLoader() {
		ServiceLoader<ProductNameDialectResolver> loader = ServiceLoader.load(ProductNameDialectResolver.class);
		List<ProductNameDialectResolver> list = CommonUtils.list();
		for (ProductNameDialectResolver resolver : loader) {
			list.add(resolver);
		}
		return list;
	}

	private List<ProductNameDialectResolver> getResolvers() {
		List<Class<? extends ProductNameDialectResolver>> classes = getResolvers(
				Thread.currentThread().getContextClassLoader());
		if (classes.size() == 0) {
			// classes = getResolvers(Thread.class.getClassLoader());
			classes = getResolvers(DialectResolver.class.getClassLoader());
		}
		if (classes.size() == 0) {
			// classes = getResolvers(Thread.class.getClassLoader());
			classes = getResolvers(Dialect.class.getClassLoader());
		}
		if (classes.size() == 0) {
			// classes = getResolvers(Thread.class.getClassLoader());
			classes = getResolvers(ClassLoader.getPlatformClassLoader());
		}
		if (classes.size() == 0) {
			// classes = getResolvers(Thread.class.getClassLoader());
			classes = getResolvers(ClassLoader.getSystemClassLoader());
		}
		List<ProductNameDialectResolver> resolverList = CommonUtils.list();
		for (final Class<? extends ProductNameDialectResolver> clazz : classes) {
			final ProductNameDialectResolver resolver = SimpleBeanUtils.newInstance(clazz);
			resolverList.add(resolver);
		}
		return resolverList;
	}

	private List<Class<? extends ProductNameDialectResolver>> getResolvers(final ClassLoader classLoader) {
		final ClassFinder finder = new ClassFinder(classLoader);
		List<Class<? extends ProductNameDialectResolver>> classes;
		finder.setFilter(new Predicate<Class<?>>() {
			@Override
			public boolean test(final Class<?> obj) {
				if (Modifier.isAbstract(obj.getModifiers())) {
					return false;
				}
				if (!ProductNameDialectResolver.class.isAssignableFrom(obj)) {
					return false;
				}
				return true;
			}
		});
		classes = finder.findRecursive(DialectResolver.class.getPackage().getName());
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
		final ProductVersionInfo productVersionInfo = DbUtils.getProductVersionInfo(databaseMetaData);
		return getDialect(productVersionInfo.getName(), productVersionInfo.getMajorVersion(),
				productVersionInfo.getMinorVersion(), productVersionInfo.getRevision());
	}

	public Dialect getDefaultDialect() {
		return DEFAULT_DIALECT;
	}

	@Override
	public Dialect getDialect(String dbProductName, final int majorVersion, final int minorVersion,
			final Integer revision) {
		ProductNameDialectResolver resolver = null;
		if (dbProductName != null) {
			dbProductName = dbProductName.trim();
		}
		if (dbProductName != null && getResolverMap().containsKey(dbProductName)) {
			resolver = getResolverMap().get(dbProductName);
			return resolver.getDialect(majorVersion, minorVersion, revision);
		}
		final int size = getResolverList().size();
		for (int i = 0; i < size; i++) {
			resolver = getResolverList().get(i);
			final Dialect dialect = resolver.getDialect(dbProductName, majorVersion, minorVersion, revision);
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
