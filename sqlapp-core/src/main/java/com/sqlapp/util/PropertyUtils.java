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

package com.sqlapp.util;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Properties;

public class PropertyUtils {

	/**
	 * 指定したプロパティのキーを取得します
	 * 
	 * @param clazz
	 * @param propertyFileName
	 * @param key
	 * @param locale
	 */
	public static <T extends Enum<T>> String getPropertyValue(Class<?> clazz,
			String propertyFileName, String key, Locale locale) {
		Properties properties;
		try {
			properties = getProperty(clazz, propertyFileName, locale);
			String value = properties.getProperty(key);
			if (value == null) {
				properties = getProperty(clazz, propertyFileName, null);
				value = properties.getProperty(key);
			}
			return value;
		} catch (FileNotFoundException e) {
			throw new RuntimeException(e);
		}
	}

	private static final DoubleKeyMap<Class<?>, Locale, Properties> PROPERTIES_CHACHE = new DoubleKeyMap<Class<?>, Locale, Properties>();

	private static Properties getProperty(Class<?> clazz, String name,
			Locale locale) throws FileNotFoundException {
		Properties properties = PROPERTIES_CHACHE.get(clazz, locale);
		if (properties != null) {
			return properties;
		}
		InputStream in = getInputStream(clazz, name, locale);
		if (in == null) {
			throw new FileNotFoundException(getPropertyPath(clazz, name));
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

	private static InputStream getInputStream(Class<?> clazz, String name,
			Locale locale) {
		InputStream stream = null;
		if (locale != null) {
			stream = FileUtils.getInputStream(clazz,
					name + "_" + locale.toString() + ".properties");
			if (stream != null) {
				return stream;
			}
			stream = FileUtils.getInputStream(clazz,
					name + "_" + locale.getLanguage() + ".properties");
			if (stream != null) {
				return stream;
			}
		}
		return FileUtils.getInputStream(clazz, name + ".properties");
	}

	private static String getPropertyPath(Class<?> clazz, String name) {
		String result = "/" + clazz.getPackage().getName().replace(".", "/")
				+ "/" + name + ".properties";
		return result;
	}
}
