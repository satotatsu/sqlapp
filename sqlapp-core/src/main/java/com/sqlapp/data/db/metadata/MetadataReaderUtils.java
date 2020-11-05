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
			Dialect dialect, String name) {
		CatalogReader catalogReader = dialect.getCatalogReader();
		T reader = catalogReader.getMetadataReader(name);
		if (reader != null) {
			return reader;
		}
		SchemaReader schemaReader = catalogReader.getSchemaReader();
		reader = schemaReader.getMetadataReader(name);
		if (reader != null) {
			return reader;
		}
		TableReader tableReader = schemaReader.getTableReader();
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
			MetadataReader<?, ?> reader, String name) {
		T obj = SimpleBeanUtils.getInstance(reader.getClass()).getValueCI(
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
	public static Set<Class<?>> supportedSchemaTypes(CatalogReader reader) {
		Set<Class<?>> types = SUPPORTED_TYPE_CACHE.get(reader.getClass());
		if (types == null) {
			types = CommonUtils.set();
			types.add(Catalog.class);
			types.add(Schema.class);
			List<MetadataReader<?, ?>> metadataReaders = getRecursiveMetadataReaders(reader);
			for (MetadataReader<?, ?> metadataReader : metadataReaders) {
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
			MetadataReader<?, ?> reader) {
		List<MetadataReader<?, ?>> result = CommonUtils.list();
		result.add(reader);
		List<MetadataReader<?, ?>> metadataReaders = getChildMetadataReaders(reader);
		for (MetadataReader<?, ?> metadataReader : metadataReaders) {
			List<MetadataReader<?, ?>> childMetadataReaders = getRecursiveMetadataReaders(metadataReader);
			result.addAll(childMetadataReaders);
		}
		return result;
	}

	private static List<MetadataReader<?, ?>> getChildMetadataReaders(
			MetadataReader<?, ?> reader) {
		List<MetadataReader<?, ?>> readers = CommonUtils.list();
		if (reader == null) {
			return readers;
		}
		Method[] methods = reader.getClass().getMethods();
		for (Method method : methods) {
			String name = method.getName();
			Matcher matcher = READER_PATTERN.matcher(name);
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
			} catch (Exception e) {
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
	public static Class<?> getMetaClass(MetadataReader<?, ?> reader) {
		if (reader instanceof ViewReader) {
			return View.class;
		}
		if (reader instanceof MviewReader) {
			return Mview.class;
		}
		Class<?> clazz = reader.getClass();
		Class<?> ret = getTypeParameterClass(clazz);
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
			Class<? extends MetadataReader<?, ?>> clazz) {
		if (ViewReader.class.isAssignableFrom(clazz)) {
			return View.class;
		}
		if (MviewReader.class.isAssignableFrom(clazz)) {
			return Mview.class;
		}
		Class<?> ret = getTypeParameterClass(clazz);
		if (ret != null) {
			return ret;
		}
		return getTypeGenericSuperclass(clazz);
	}

	protected static Class<?> getTypeGenericSuperclass(Class<?> clazz) {
		if (clazz == null) {
			return null;
		}
		Type type = clazz.getGenericSuperclass();
		if (CommonUtils.isEmpty(type)) {
			return getTypeGenericSuperclass(clazz.getSuperclass());
		} else {
			if (type instanceof ParameterizedType) {
				ParameterizedType pt = (ParameterizedType) type;
				Type retType = pt.getActualTypeArguments()[0];
				return (Class<?>) retType;
			} else {
				if (MetadataReader.class.isAssignableFrom((Class<?>) type)) {
					Class<?> ret = getTypeParameterClass((Class<?>) type);
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

	protected static Class<?> getTypeParameterClass(Class<?> clazz) {
		if (clazz == null) {
			return null;
		}
		TypeVariable<?>[] typvs = clazz.getTypeParameters();
		if (CommonUtils.isEmpty(typvs)) {
			Class<?> ret = getTypeParameterClass(clazz.getSuperclass());
			if (ret != null) {
				return ret;
			}
		} else {
			for (TypeVariable<?> typv : typvs) {
				Type[] types = typv.getBounds();
				for (Type type : types) {
					if (type instanceof ParameterizedType) {
						ParameterizedType ptype = (ParameterizedType) type;
						Type rawType = ptype.getRawType();
						if (MetadataReader.class
								.isAssignableFrom((Class<?>) rawType)) {
							Class<?> ret = getTypeParameterClass((Class<?>) rawType);
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
