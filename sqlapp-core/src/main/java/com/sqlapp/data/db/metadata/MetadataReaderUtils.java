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

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.schemas.Catalog;
import com.sqlapp.data.schemas.Mview;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.SchemaUtils;
import com.sqlapp.data.schemas.View;
import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.SimpleBeanUtils;

/**
 * メタデータ読み込みのユーティリティクラス
 * 
 * @author tatsuo satoh
 * 
 */
public class MetadataReaderUtils {
	/**
	 * コンストラクタ
	 */
	private MetadataReaderUtils() {
	}

	/**
	 * 指定した名称のMetaDataReaderを取得します
	 * 
	 * @param dialect
	 * @param name
	 */
	public static <T extends MetadataReader<?, ?>> T getMetadataReader(
			final Dialect dialect, final String name) {
		final CatalogReader catalogReader = dialect.getCatalogReader();
		T reader = catalogReader.getMetadataReader(name);
		if (reader != null) {
			return reader;
		}
		final SchemaReader schemaReader = catalogReader.getSchemaReader();
		reader = schemaReader.getMetadataReader(name);
		if (reader != null) {
			return reader;
		}
		final TableReader tableReader = schemaReader.getTableReader();
		reader = tableReader.getMetadataReader(name);
		if (reader != null) {
			return reader;
		}
		return null;
	}

	/**
	 * 指定した名称のMetaDataReaderを取得します
	 * 
	 * @param name
	 */
	protected static <T extends MetadataReader<?, ?>> T getMetadataReader(
			final MetadataReader<?, ?> reader, final String name) {
		final T obj = SimpleBeanUtils.getInstance(reader.getClass()).getValueCI(
				reader, SchemaUtils.getSingularName(name) + "Reader");
		return obj;
	}

	private final static Map<Class<?>, Set<Class<?>>> SUPPORTED_TYPE_CACHE = CommonUtils
			.map();

	/**
	 * 指定のカタログ読み込みクラスでサポートされた型のセットを返します
	 * 
	 * @param reader
	 */
	public static Set<Class<?>> supportedSchemaTypes(final CatalogReader reader) {
		Set<Class<?>> types = SUPPORTED_TYPE_CACHE.get(reader.getClass());
		if (types == null) {
			types = CommonUtils.set();
			types.add(Catalog.class);
			types.add(Schema.class);
			final List<MetadataReader<?, ?>> metadataReaders = getRecursiveMetadataReaders(reader);
			for (final MetadataReader<?, ?> metadataReader : metadataReaders) {
				types.add(getMetaClass(metadataReader));
			}
			types = Collections.unmodifiableSet(types);
			SUPPORTED_TYPE_CACHE.put(reader.getClass(), types);
		}
		return types;
	}

	private static final Pattern READER_PATTERN = Pattern.compile(
			"get.*Reader", Pattern.CASE_INSENSITIVE);

	private static List<MetadataReader<?, ?>> getRecursiveMetadataReaders(
			final MetadataReader<?, ?> reader) {
		final List<MetadataReader<?, ?>> result = CommonUtils.list();
		result.add(reader);
		final List<MetadataReader<?, ?>> metadataReaders = getChildMetadataReaders(reader);
		for (final MetadataReader<?, ?> metadataReader : metadataReaders) {
			final List<MetadataReader<?, ?>> childMetadataReaders = getRecursiveMetadataReaders(metadataReader);
			result.addAll(childMetadataReaders);
		}
		return result;
	}

	private static List<MetadataReader<?, ?>> getChildMetadataReaders(
			final MetadataReader<?, ?> reader) {
		final List<MetadataReader<?, ?>> readers = CommonUtils.list();
		if (reader == null) {
			return readers;
		}
		final Method[] methods = reader.getClass().getMethods();
		for (final Method method : methods) {
			final String name = method.getName();
			final Matcher matcher = READER_PATTERN.matcher(name);
			if (!matcher.matches()) {
				continue;
			}
			if (name.contains("Parent")) {
				continue;
			}
			if (!CommonUtils.isEmpty(method.getParameterTypes())) {
				continue;
			}
			MetadataReader<?, ?> obj;
			try {
				obj = (MetadataReader<?, ?>) method.invoke(reader);
				if (obj != null) {
					readers.add(obj);
				}
			} catch (final Exception e) {
				throw new RuntimeException(e);
			}
		}
		return readers;
	}

	/**
	 * MetadataReaderから読み込み対象の型を取得します
	 * 
	 * @param reader
	 */
	public static Class<?> getMetaClass(final MetadataReader<?, ?> reader) {
		if (reader instanceof ViewReader) {
			return View.class;
		}
		if (reader instanceof MviewReader) {
			return Mview.class;
		}
		final Class<?> clazz = reader.getClass();
		final Class<?> ret = getTypeParameterClass(clazz);
		if (ret != null) {
			return ret;
		}
		return getTypeGenericSuperclass(clazz);
	}

	/**
	 * MetadataReaderクラスから読み込み対象の型を取得します
	 * 
	 * @param clazz
	 */
	public static Class<?> getMetaClass(
			final Class<? extends MetadataReader<?, ?>> clazz) {
		if (ViewReader.class.isAssignableFrom(clazz)) {
			return View.class;
		}
		if (MviewReader.class.isAssignableFrom(clazz)) {
			return Mview.class;
		}
		final Class<?> ret = getTypeParameterClass(clazz);
		if (ret != null) {
			return ret;
		}
		return getTypeGenericSuperclass(clazz);
	}

	protected static Class<?> getTypeGenericSuperclass(final Class<?> clazz) {
		if (clazz == null) {
			return null;
		}
		final Type type = clazz.getGenericSuperclass();
		if (CommonUtils.isEmpty(type)) {
			return getTypeGenericSuperclass(clazz.getSuperclass());
		} else {
			if (type instanceof ParameterizedType) {
				final ParameterizedType pt = (ParameterizedType) type;
				final Type retType = pt.getActualTypeArguments()[0];
				return (Class<?>) retType;
			} else {
				if (MetadataReader.class.isAssignableFrom((Class<?>) type)) {
					final Class<?> ret = getTypeParameterClass((Class<?>) type);
					if (ret != null) {
						return ret;
					} else {
						return getTypeGenericSuperclass(clazz.getSuperclass());
					}
				}
			}
		}
		return null;
	}

	public static Class<?> getTypeParameterClass(final Class<?> clazz) {
		if (clazz == null) {
			return null;
		}
		final TypeVariable<?>[] typvs = clazz.getTypeParameters();
		if (CommonUtils.isEmpty(typvs)) {
			final Class<?> ret = getTypeParameterClass(clazz.getSuperclass());
			if (ret != null) {
				return ret;
			}
		} else {
			for (final TypeVariable<?> typv : typvs) {
				final Type[] types = typv.getBounds();
				for (final Type type : types) {
					if (type instanceof ParameterizedType) {
						final ParameterizedType ptype = (ParameterizedType) type;
						final Type rawType = ptype.getRawType();
						if (MetadataReader.class
								.isAssignableFrom((Class<?>) rawType)) {
							final Class<?> ret = getTypeParameterClass((Class<?>) rawType);
							if (ret != null) {
								return ret;
							}
						}
					}
				}
			}
		}
		return null;
	}

}
