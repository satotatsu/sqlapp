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

package com.sqlapp.data.converter;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * EnumType Converterー
 * 
 * @param <T>
 */
public class EnumConverter<T extends Enum<?>> extends AbstractConverter<T> {
	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = -6320671871245038472L;
	private final Class<T> clazz;

	private ConcurrentMap<String, Map<Class<?>, Method>> methodCache = new ConcurrentHashMap<String, Map<Class<?>, Method>>();
	/**
	 * 空文字をnullのenumに変換する
	 */
	private boolean emptyToNull = false;

	/**
	 * コンストラクタ
	 * 
	 * @param clazz
	 */
	public EnumConverter(Class<T> clazz) {
		this.clazz = clazz;
	}

	@Override
	public T convertObject(Object value) {
		if (value == null) {
			return null;
		}
		if ("".equals(value) && this.isEmptyToNull()) {
			return null;
		}
		T result = invoke("parse", value);
		if (result != null) {
			return result;
		} else {
			result = invoke("valueOf", value);
			if (result != null) {
				return result;
			}
		}
		throw new IllegalArgumentException("value type is invalid. value="
				+ value);
	}

	private T invoke(String methodName, Object value) {
		Method method = getStaticMethod(methodName, value.getClass());
		if (method != null) {
			return invoke(method, value);
		}
		method = getStaticMethod(methodName, String.class);
		if (method != null) {
			return invoke(method, value.toString());
		}
		return null;
	}

	@Override
	public String convertString(T value) {
		if (value == null) {
			return null;
		}
		return value.toString();
	}

	@SuppressWarnings("unchecked")
	@Override
	public T copy(Object value) {
		return (T) value;
	}

	private Method getStaticMethod(String methodName, Class<?> arg) {
		Map<Class<?>, Method> map = methodCache.get(methodName);
		if (map == null) {
			map = new HashMap<Class<?>, Method>();
			Map<Class<?>, Method> org = methodCache.put(methodName, map);
			if (org != null) {
				map = org;
			}
		}
		Method method = map.get(arg);
		if (method != null) {
			return method;
		}
		if (map.containsKey(arg)) {
			return null;
		}
		try {
			method = clazz.getMethod(methodName, arg);
			if (method!=null){
				if ((method.getModifiers() & Modifier.STATIC) == 0) {
					method = null;
				}else if ((method.getModifiers() & Modifier.PUBLIC) == 0) {
					method = null;
				}
			}
		} catch (SecurityException e) {
			throw new RuntimeException(e);
		} catch (NoSuchMethodException e) {
			method = null;
		}
		synchronized (map) {
			map.put(arg, method);
		}
		return method;
	}

	/**
	 * メソッドを実行します
	 * 
	 * @param method
	 *            メソッド
	 * @param value
	 *            値
	 * @return 戻り値
	 */
	@SuppressWarnings({ "unchecked", "hiding" })
	private <T> T invoke(Method method, Object value) {
		try {
			return (T) method.invoke(null, value);
		} catch (IllegalArgumentException e) {
			throw new RuntimeException(e);
		} catch (IllegalAccessException e) {
			throw new RuntimeException(e);
		} catch (InvocationTargetException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * @return the emptyToNull
	 */
	public boolean isEmptyToNull() {
		return emptyToNull;
	}

	/**
	 * @param emptyToNull
	 *            the emptyToNull to set
	 */
	public void setEmptyToNull(boolean emptyToNull) {
		this.emptyToNull = emptyToNull;
	}

	/**
	 * Enumのクラスを取得します
	 * 
	 * @return Enumのクラス
	 */
	public Class<T> getEnumClass() {
		return this.clazz;
	}

}
