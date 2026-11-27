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
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.sqlapp.data.db.dialect.resolver.DefaultDialectResolver;
import com.sqlapp.data.db.dialect.resolver.ProductNameDialectResolver;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.jdbc.AbstractConnection;
import com.sqlapp.jdbc.AbstractDatabaseMetaData;
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

	/**
	 * Resolvers per class loader used to discover dialect implementations.
	 */
	private static final Map<ClassLoader, DialectResolver> INSTANCES = new ConcurrentHashMap<ClassLoader, DialectResolver>();

	private static final Dialect DEFAULT_DIALECT = new Dialect(null);

	private final ClassLoader classLoader;

	/**
	 * Returns the resolver for the current thread's context class loader.
	 * 
	 * @return the resolver whose dialects are discovered through the context
	 *         class loader
	 */
	public static DialectResolver getInstance() {
		return getInstance(Thread.currentThread().getContextClassLoader());
	}

	/**
	 * Returns the resolver whose dialects are discovered through the specified
	 * class loader. The resolver is cached until {@link #release(ClassLoader)}
	 * is called for the class loader.
	 * 
	 * @param classLoader class loader used to discover dialects. {@code null}
	 *                    means the class loader of this class.
	 * @return the resolver
	 */
	public static DialectResolver getInstance(final ClassLoader classLoader) {
		final ClassLoader key = toKey(classLoader);
		DialectResolver resolver = INSTANCES.get(key);
		if (resolver != null) {
			return resolver;
		}
		synchronized (INSTANCES) {
			resolver = INSTANCES.get(key);
			if (resolver == null) {
				// Not computeIfAbsent: a resolver may call getInstance while it is
				// being constructed.
				resolver = new DialectResolver(key);
				final DialectResolver existing = INSTANCES.putIfAbsent(key, resolver);
				if (existing != null) {
					resolver = existing;
				}
			}
			return resolver;
		}
	}

	/**
	 * Discards the resolver cached for the specified class loader. Call this
	 * before closing a class loader that was used to resolve dialects, so that
	 * its classes are no longer referenced and a later call discovers dialects
	 * again.
	 * 
	 * @param classLoader the class loader
	 */
	public static void release(final ClassLoader classLoader) {
		if (classLoader != null) {
			INSTANCES.remove(classLoader);
		}
	}

	private static ClassLoader toKey(final ClassLoader classLoader) {
		if (classLoader != null) {
			return classLoader;
		}
		final ClassLoader own = DialectResolver.class.getClassLoader();
		return own != null ? own : ClassLoader.getSystemClassLoader();
	}

	private DialectResolver(final ClassLoader classLoader) {
		this.classLoader = classLoader;
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
			list.add(new DefaultDialectResolver());
			resolverList.addAll(list);
		} else {
			// 単一JAR化された場合はこっちが動くはず
			list = getResolvers();
			if (list.isEmpty()) {
				list.add(new DefaultDialectResolver());
			}
			resolverList.addAll(list);
		}
		Collections.sort(resolverList);
		logger.debug("resolverList=" + resolverList);
		logger.debug("resolverList.size()=" + resolverList.size());
	}

	private List<ProductNameDialectResolver> getResolversByServiceLoader() {
		final ServiceLoader<ProductNameDialectResolver> loader = ServiceLoader.load(ProductNameDialectResolver.class,
				classLoader);
		final List<ProductNameDialectResolver> list = CommonUtils.list();
		for (ProductNameDialectResolver resolver : loader) {
			list.add(resolver);
			final ProductNameDialectResolver[] related = resolver.getRelatedProducts();
			for (ProductNameDialectResolver rel : related) {
				list.add(rel);
			}
		}
		return list;
	}

	private List<ProductNameDialectResolver> getResolvers() {
		List<Class<? extends ProductNameDialectResolver>> classes = getResolvers(classLoader);
		if (classes.size() == 0) {
			// classes = getResolvers(Thread.class.getClassLoader());
			classes = getResolvers(DialectResolver.class.getClassLoader());
		}
		if (classes.size() == 0) {
			// classes = getResolvers(Thread.class.getClassLoader());
			classes = getResolvers(ClassLoader.getSystemClassLoader());
		}
		List<ProductNameDialectResolver> resolverList = CommonUtils.list();
		for (final Class<? extends ProductNameDialectResolver> clazz : classes) {
			final ProductNameDialectResolver resolver = SimpleBeanUtils.newInstance(clazz);
			resolverList.add(resolver);
			final ProductNameDialectResolver[] related = resolver.getRelatedProducts();
			for (ProductNameDialectResolver rel : related) {
				resolverList.add(rel);
			}
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
		return getDialect(getDatabaseMetaData(connection), getDialectOptions(connection));
	}

	/**
	 * Dialectの取得
	 * 
	 * @param rs ResultSet
	 */
	public Dialect getDialect(final ResultSet rs) {
		try {
			return getDialect(getDatabaseMetaData(rs.getStatement().getConnection()));
		} catch (SQLException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Dialectの取得
	 * 
	 * @param databaseMetaData
	 */
	public Dialect getDialect(final DatabaseMetaData databaseMetaData) {
		Map<String, String> dialectOptions = Map.of();
		if (databaseMetaData instanceof AbstractDatabaseMetaData) {
			// sqlappでラップした接続のメタデータはDialectオプションを持つ接続を返す
			try {
				dialectOptions = getDialectOptions(databaseMetaData.getConnection());
			} catch (final SQLException e) {
				throw new IllegalStateException("Cannot read the connection of the database metadata", e);
			}
		}
		return getDialect(databaseMetaData, dialectOptions);
	}

	/**
	 * Dialectの取得
	 * 
	 * @param databaseMetaData DatabaseMetaData
	 * @param dialectOptions   データソースで指定したDialectオプション(例: oracle.maxStringSize)
	 * @throws IllegalArgumentException 接続先のDBで使用できないオプションや不正な値の場合
	 */
	public Dialect getDialect(final DatabaseMetaData databaseMetaData, final Map<String, String> dialectOptions) {
		final Map<String, String> options = dialectOptions == null ? Map.of() : dialectOptions;
		for (ProductNameDialectResolver resolver : getResolverList()) {
			final Dialect dialect = resolver.resolveDatabaseMetaData(databaseMetaData, options);
			if (dialect != null) {
				return options.isEmpty() ? dialect : resolver.applyDialectOptions(dialect, options);
			}
		}
		final ProductVersionInfo productVersionInfo = DbUtils.getProductVersionInfo(databaseMetaData);
		return getDialect(productVersionInfo.getName(), productVersionInfo.getMajorVersion(),
				productVersionInfo.getMinorVersion(), productVersionInfo.getRevision(), options);
	}

	/**
	 * Dialectの取得
	 * 
	 * @param dbProductName  DB製品名
	 * @param majorVersion   メジャーバージョン
	 * @param minorVersion   マイナーバージョン
	 * @param revision       リビジョン
	 * @param dialectOptions Dialectオプション(例: oracle.maxStringSize)
	 * @throws IllegalArgumentException 対象のDBで使用できないオプションや不正な値の場合
	 */
	public Dialect getDialect(final String dbProductName, final int majorVersion, final int minorVersion,
			final Integer revision, final Map<String, String> dialectOptions) {
		final Dialect dialect = getDialect(dbProductName, majorVersion, minorVersion, revision);
		if (dialectOptions == null || dialectOptions.isEmpty()) {
			return dialect;
		}
		final String name = dbProductName == null ? null : dbProductName.trim();
		final ProductNameDialectResolver resolver = name == null ? null : getResolverMap().get(name);
		if (resolver == null) {
			throw new IllegalArgumentException(
					"Dialect options " + dialectOptions.keySet() + " cannot be applied to " + dbProductName);
		}
		return resolver.applyDialectOptions(dialect, dialectOptions);
	}

	/**
	 * 解決済みのDialectにDialectオプションを適用します。Schema XMLの製品情報から求めたDialectなど、接続のない場合に使用します。
	 * 
	 * @param dialect        Dialect
	 * @param dialectOptions Dialectオプション(例: oracle.maxStringSize)
	 * @return オプションを適用したDialect
	 * @throws IllegalArgumentException 対象のDBで使用できないオプションや不正な値の場合
	 */
	public Dialect applyDialectOptions(final Dialect dialect, final Map<String, String> dialectOptions) {
		if (dialectOptions == null || dialectOptions.isEmpty() || dialect == null) {
			if (dialect == null && dialectOptions != null && !dialectOptions.isEmpty()) {
				throw new IllegalArgumentException(
						"Dialect options " + dialectOptions.keySet() + " require a database product.");
			}
			return dialect;
		}
		for (final ProductNameDialectResolver resolver : getResolverList()) {
			if (resolver.matchesProductName(dialect.getProductName())) {
				return resolver.applyDialectOptions(dialect, dialectOptions);
			}
		}
		throw new IllegalArgumentException(
				"Dialect options " + dialectOptions.keySet() + " cannot be applied to " + dialect.getProductName());
	}

	private static Map<String, String> getDialectOptions(final Connection connection) {
		if (connection instanceof AbstractConnection) {
			return ((AbstractConnection) connection).getDialectOptions();
		}
		return Map.of();
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

	private Map<String, ProductNameDialectResolver> getResolverMap() {
		return resolverMap;
	}
}
