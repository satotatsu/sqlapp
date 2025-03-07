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

package com.sqlapp.util;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import com.sqlapp.data.converter.EnumConvertable;

/**
 * Enum用のユーティリティ
 * 
 * 
 */
public class EnumUtils {
	private EnumUtils() {
	}

	/**
	 * 指定したenumの表示名を取得します
	 * 
	 * @param enm
	 *            enum
	 * @param locale
	 *            ロケール
	 * @return 表示名
	 */
	public static <T extends Enum<T>> String getDisplayName(T enm, Locale locale) {
		Properties properties = getProperty(enm.getClass(), locale);
		return properties.getProperty(enm.toString() + ".displayName");
	}

	private static final DoubleKeyMap<Class<?>, Locale, Properties> PROPERTIES_CHACHE = new DoubleKeyMap<Class<?>, Locale, Properties>();

	private static Properties getProperty(Class<?> clazz, Locale locale) {
		Properties properties = PROPERTIES_CHACHE.get(clazz, locale);
		if (properties != null) {
			return properties;
		}
		InputStream in = null;
		if (locale != null) {
			in = clazz.getResourceAsStream(getPropertyPath(clazz,
					locale.toString()));
			if (in == null) {
				in = clazz.getResourceAsStream(getPropertyPath(clazz,
						locale.getLanguage()));
			}
		}
		if (in == null) {
			in = clazz.getResourceAsStream(getPropertyPath(clazz));
		}
		if (in == null) {
			throw new RuntimeException(new FileNotFoundException(
					getPropertyPath(clazz)));
		}
		try {
			properties = new Properties();
			properties.load(in);
			PROPERTIES_CHACHE.put(clazz, locale, properties);
			return properties;
		} catch (IOException e) {
			return null;
		} finally {
			FileUtils.close(in);
		}
	}

	private static String getPropertyPath(Class<?> clazz, String lang) {
		String result = "/" + clazz.getName().replace(".", "/") + "_" + lang
				+ ".properties";
		return result;
	}

	private static String getPropertyPath(Class<?> clazz) {
		String result = "/" + clazz.getName().replace(".", "/") + ".properties";
		return result;
	}

	/**
	 * 指定したEnumクラスの値のセットを取得します
	 * 
	 * @param clazz
	 *            クラス
	 * @return Enumクラスの値のセット
	 */
	public static <T extends Enum<?>> Set<T> getValues(Class<T> clazz) {
		T[] vals = clazz.getEnumConstants();
		Set<T> result = new LinkedHashSet<T>(vals.length);
		for (T t : clazz.getEnumConstants()) {
			result.add(t);
		}
		return result;
	}

	private static final ConcurrentMap<Class<?>, Map<Object, Enum<?>>> ENUM_CACHE = new ConcurrentHashMap<Class<?>, Map<Object, Enum<?>>>();

	/**
	 * 指定した値に対応したenum型を返します
	 * 
	 * @param clazz
	 *            enumの型
	 * @param value
	 *            指定した値
	 * @return enum型
	 */
	public static <T extends Enum<?>> T parse(Class<T> clazz, Object value) {
		if (value == null) {
			return null;
		}
		String lowerKey = value.toString().toLowerCase();
		Map<Object, Enum<?>> map = ENUM_CACHE.get(clazz);
		if (map == null) {
			map = new HashMap<Object, Enum<?>>();
			Map<Object, Enum<?>> orgMap = ENUM_CACHE.putIfAbsent(clazz, map);
			if (orgMap != null) {
				map = orgMap;
			}
		}
		@SuppressWarnings("unchecked")
		T result = (T) map.get(lowerKey);
		if (result != null) {
			return result;
		}
		for (T type : clazz.getEnumConstants()) {
			String typeText = type.toString();
			if (type == value) {
				map.put(lowerKey, type);
				return type;
			}
			if (typeText.equalsIgnoreCase(lowerKey)) {
				map.put(lowerKey, type);
				return type;
			}
		}
		return null;
	}

	/**
	 * EnumConvertableと値がマッチしているかを返します
	 * 
	 * @param enumConvertable
	 * @param value
	 * @return <code>true</code>:EnumConvertableと値がマッチ
	 */
	public static boolean match(EnumConvertable<?> enumConvertable, Object value) {
		if (CommonUtils.eq(enumConvertable.getValue(), value)) {
			return true;
		}
		if (enumConvertable.getValue() instanceof String
				&& value instanceof String) {
			if (enumConvertable.getValue().toString()
					.equalsIgnoreCase((String) value)) {
				return true;
			}
		}
		return false;
	}

}
