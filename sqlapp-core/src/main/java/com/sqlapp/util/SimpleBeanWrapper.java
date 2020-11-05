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

import java.lang.annotation.Annotation;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.sqlapp.data.converter.Converters;
import com.sqlapp.exceptions.InvalidPropertyException;

/**
 * 単純なリフレクション用のユーティリティ
 * 
 * @author satoh
 * 
 */
public class SimpleBeanWrapper {

	private final Class<?> clazz;

	private final Map<String, Method> getterMap = CommonUtils.map();

	private final Map<String, Method> setterMap = CommonUtils.map();

	private final Set<String> propertyNameSet = CommonUtils.set();
	/**
	 * 大文字、アンダースコアを無視した名称と正式な名称とのマッピング
	 */
	private final Map<String, String> propertyNameMapping = new LowerUnderScoreISMap<String>();

	private final Map<String, Map<Class<?>, Method>> setterOverloadMap = CommonUtils.map();

	private final Map<String, Field> fieldMap = CommonUtils.map();

	private final Map<String, Field> protectedFieldMap = CommonUtils.map();

	private transient Constructor<?> constructor = null;

	private Class<?>[] constructorParameterTypes = null;

	private boolean initialized = false;

	/**
	 * コンストラクタ
	 * 
	 * @param className
	 */
	protected SimpleBeanWrapper(final String className) {
		this.clazz = CommonUtils.classForName(className);
		initialize(clazz);
	}

	/**
	 * コンストラクタ
	 * 
	 * @param clazz
	 */
	protected SimpleBeanWrapper(final Class<?> clazz) {
		this.clazz = clazz;
		initialize(clazz);
	}

	private void initialize(final Class<?> clazz) {
		synchronized (clazz) {
			if (initialized) {
				return;
			}
			initializeGetterSetter(clazz);
			final Field[] fields = clazz.getFields();
			for (final Field field : fields) {
				final String propertyName = field.getName();
				if ("serialversionuid".equalsIgnoreCase(propertyName) && field.getType() == long.class) {
					continue;
				}
				if ("class".equalsIgnoreCase(propertyName)) {
					continue;
				}
				if (Modifier.isStatic(field.getModifiers())) {
					continue;
				}
				if (Modifier.isPublic(field.getModifiers())) {
					fieldMap.put(propertyName, field);
					propertyNameSet.add(propertyName);
					propertyNameMapping.put(propertyName, propertyName);
				}
			}
			setProtectedFields(clazz);
			initialized = true;
		}
	}

	private void setProtectedFields(final Class<?> clazz) {
		if (clazz == Object.class) {
			return;
		}
		if (clazz == null) {
			return;
		}
		final Field[] fields = clazz.getDeclaredFields();
		for (final Field field : fields) {
			final String propertyName = field.getName();
			if ("serialversionuid".equalsIgnoreCase(propertyName) && field.getType() == long.class) {
				continue;
			}
			if (!Modifier.isPublic(field.getModifiers())) {
				if (protectedFieldMap.containsKey(propertyName)) {
					continue;
				}
				field.setAccessible(true);
				protectedFieldMap.put(propertyName, field);
			}
		}
		setProtectedFields(clazz.getSuperclass());
	}

	private void initializeGetterSetter(final Class<?> clazz) {
		final Method[] methods = clazz.getMethods();
		for (final Method method : methods) {
			final Class<?> returnType = method.getReturnType();
			final Class<?>[] parameterTypes = method.getParameterTypes();
			final String methodName = method.getName();
			if (isSetter(clazz, method)) {
				final String propertyName = getPropertyName(methodName);
				Map<Class<?>, Method> classMap = setterOverloadMap.get(propertyName);
				if (classMap == null) {
					classMap = CommonUtils.concurrentMap();
					setterOverloadMap.put(propertyName, classMap);
					propertyNameMapping.put(propertyName, propertyName);
				}
				final Class<?> argClass = method.getParameterTypes()[0];
				if (argClass.isPrimitive()) {
					final Class<?> wrapperClass = CommonUtils.getWrapperClass(argClass);
					classMap.put(wrapperClass, method);
				}
				classMap.put(argClass, method);
				setterMap.put(propertyName, method);
				propertyNameMapping.put(propertyName, propertyName);
				continue;
			} else {
				if (parameterTypes.length == 0) {
					if (methodName.startsWith("getClass")) {
						continue;
					}
					if (methodName.startsWith("getSerialversionuid") && Modifier.isStatic(method.getModifiers())
							&& returnType == long.class) {
						continue;
					}
					String propertyName = null;
					final Matcher getMatcher=GETTER_GET_PATTERN.matcher(methodName);
					if (getMatcher.matches()) {
						propertyName = getPropertyName(methodName);
						getterMap.put(propertyName, method);
						propertyNameSet.add(propertyName);
						propertyNameMapping.put(propertyName, propertyName);
						continue;
					}
					final Matcher isMatcher=GETTER_IS_PATTERN.matcher(methodName);
					if (isMatcher.matches() && (boolean.class.equals(returnType) || Boolean.class.equals(returnType))) {
						propertyName = getIsPropertyName(methodName);
						getterMap.put(propertyName, method);
						propertyNameSet.add(propertyName);
						propertyNameMapping.put(propertyName, propertyName);
						continue;
					}
				}
			}
		}
	}


	private static final Pattern GETTER_GET_PATTERN=Pattern.compile("^get[_A-Z]+.*");
	
	private static final Pattern GETTER_IS_PATTERN=Pattern.compile("^is[_A-Z]+.*");

	private static final Pattern SETTER_PATTERN=Pattern.compile("^set[_A-Z]+.*");
	
	private boolean isSetter(final Class<?> clazz, final Method method) {
		final Class<?> returnType = method.getReturnType();
		final Class<?>[] parameterTypes = method.getParameterTypes();
		final String methodName = method.getName();
		if (returnType == void.class || (Object.class != clazz && returnType.isAssignableFrom(clazz))) {
			if (parameterTypes.length == 1) {
				final Matcher matcher=SETTER_PATTERN.matcher(methodName);
				if (matcher.matches()) {
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * メソッド名からプロパティ名を取得します
	 * 
	 * @param methodName
	 */
	private String getPropertyName(final String methodName) {
		String propertyName = methodName.substring(3);
		propertyName = propertyName.substring(0, 1).toLowerCase() + propertyName.substring(1);
		return propertyName;
	}

	/**
	 * メソッド名からプロパティ名を取得します
	 * 
	 * @param methodName
	 */
	private String getIsPropertyName(final String methodName) {
		String propertyName = methodName.substring(2);
		propertyName = propertyName.substring(0, 1).toLowerCase() + propertyName.substring(1);
		return propertyName;
	}

	/**
	 * コンストラクタを設定します
	 * 
	 * @param parameterTypes
	 * @return this
	 */
	public SimpleBeanWrapper setConstructor(final Class<?>... parameterTypes) {
		synchronized (clazz) {
			constructorParameterTypes = parameterTypes;
			try {
				constructor = clazz.getConstructor(parameterTypes);
			} catch (final NoSuchMethodException e) {
				throw new RuntimeException(e);
			} catch (final SecurityException e) {
				throw new RuntimeException(e);
			}
		}
		return this;
	}

	protected Constructor<?> getConstructor() {
		if (constructor != null) {
			return constructor;
		}
		synchronized (clazz) {
			try {
				constructor = clazz.getConstructor(constructorParameterTypes);
				return constructor;
			} catch (final NoSuchMethodException e) {
				throw new RuntimeException(e);
			} catch (final SecurityException e) {
				throw new RuntimeException(e);
			}
		}
	}

	/**
	 * 新しいインスタンスを生成します
	 * 
	 * @param initargs コンストラクタの引数
	 * @return 新しいインスタンス
	 */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public <T> T newInstance(final Object... initargs) {
		try {
			if (this.clazz.equals(Map.class)) {
				return (T) new LinkedHashMap();
			} else if (this.clazz.equals(List.class)) {
				return (T) new ArrayList();
			} else if (this.clazz.equals(Set.class)) {
				return (T) new LinkedHashSet();
			} else if (this.clazz.isInterface()) {
				return null;
			}
			if (initargs == null || initargs.length == 0) {
				if (clazz.isArray()) {
					return (T) Array.newInstance(clazz.getComponentType(), 0);
				}
			} else if (getConstructor().getParameterTypes().length == initargs.length) {
				return (T) getConstructor().newInstance(initargs);
			}
			return (T) clazz.getDeclaredConstructor().newInstance();
		} catch (final InstantiationException e) {
			throw new RuntimeException("Class=" + clazz, e);
		} catch (final IllegalAccessException e) {
			throw new RuntimeException("Class=" + clazz, e);
		} catch (final InvocationTargetException e) {
			throw SimpleBeanUtils.throwInvocationTargetException(e);
		} catch (final IllegalArgumentException e) {
			throw new RuntimeException("Class=" + clazz, e);
		} catch (final NoSuchMethodException e) {
			throw new RuntimeException("Class=" + clazz, e);
		} catch (final SecurityException e) {
			throw new RuntimeException("Class=" + clazz, e);
		}
	}

	/**
	 * プロパティ値を取得します
	 * 
	 * @param caseInsensitive プロパティの大文字小文字を無視
	 * @param obj             対象のオブジェクト
	 * @param propertyName    プロパティ名
	 * @return プロパティ値
	 */
	public <T> T getValue(final boolean caseInsensitive, final Object obj, final String propertyName) {
		if (caseInsensitive) {
			return this.getValueCI(obj, propertyName);
		} else {
			return this.getValue(obj, propertyName);
		}
	}

	/**
	 * プロパティ値を取得します
	 * 
	 * @param obj          対象のオブジェクト
	 * @param propertyName プロパティ名
	 * @return プロパティ値
	 */
	@SuppressWarnings("unchecked")
	public <T> T getValue(final Object obj, final String propertyName) {
		try {
			final Method method = getterMap.get(propertyName);
			if (method != null) {
				return (T) method.invoke(obj);
			}
			final Field field = fieldMap.get(propertyName);
			if (field != null) {
				return (T) field.get(obj);
			} else {
				return null;
			}
		} catch (final InvocationTargetException e) {
			throw SimpleBeanUtils.throwInvocationTargetException(e);
		} catch (final IllegalAccessException e) {
			throw new RuntimeException(e);
		} catch (final IllegalArgumentException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * プロパティ名を指定してプロパティのジェネリック型を取得します
	 * 
	 * @param propertyName プロパティ名
	 * @return プロパティのジェネリック型
	 */
	public Type getPropertyGenericType(final String propertyName) {
		if (propertyName == null) {
			return null;
		}
		final Method method = getterMap.get(propertyName);
		if (method != null) {
			return method.getGenericReturnType();
		}
		return fieldMap.get(propertyName).getGenericType();
	}

	/**
	 * プロパティ名(大文字、小文字、アンダースコア無視)を指定してプロパティのジェネリック型を取得します
	 * 
	 * @param propertyName プロパティ名
	 * @return プロパティのジェネリック型
	 */
	public Type getPropertyGenericTypeCI(final String propertyName) {
		return getPropertyGenericType(getPropertyNameCI(propertyName));
	}

	/**
	 * プロパティ名(大文字、小文字、アンダースコア無視)を指定してプロパティのジェネリック型を取得します
	 * 
	 * @param caseInsensitive プロパティの大文字小文字を無視
	 * @param propertyName    プロパティ名
	 * @return プロパティのジェネリック型
	 */
	protected Type getPropertyGenericType(final boolean caseInsensitive, final String propertyName) {
		if (caseInsensitive) {
			return getPropertyGenericTypeCI(propertyName);
		} else {
			return getPropertyGenericType(propertyName);
		}
	}

	/**
	 * プロパティ名を指定してプロパティの型を取得します
	 * 
	 * @param propertyName プロパティ名
	 * @return プロパティの型
	 */
	public Class<?> getPropertyClass(final String propertyName) {
		if (propertyName == null) {
			return null;
		}
		final Method method = getterMap.get(propertyName);
		if (method != null) {
			return method.getReturnType();
		}
		final Field field = fieldMap.get(propertyName);
		if (field != null) {
			return field.getType();
		}
		return null;
	}

	/**
	 * プロパティ名(大文字、小文字、アンダースコア無視)を指定してプロパティの型を取得します
	 * 
	 * @param propertyName プロパティ名
	 * @return プロパティの型
	 */
	public Class<?> getPropertyClassCI(final String propertyName) {
		return getPropertyClass(getPropertyNameCI(propertyName));
	}

	/**
	 * プロパティ名(大文字、小文字、アンダースコア無視)を指定してプロパティの型を取得します
	 * 
	 * @param caseInsensitive プロパティの大文字小文字を無視
	 * @param propertyName    プロパティ名
	 * @return プロパティの型
	 */
	public Class<?> getPropertyClass(final boolean caseInsensitive, final String propertyName) {
		if (caseInsensitive) {
			return getPropertyClass(getPropertyNameCI(propertyName));
		} else {
			return getPropertyClass(propertyName);
		}
	}

	/**
	 * プロパティ名(大文字、小文字、アンダースコア無視)を指定して正式なプロパティ名を取得します
	 * 
	 * @param propertyName
	 * @return 正式なプロパティ名
	 */
	public String getPropertyNameCI(final String propertyName) {
		return propertyNameMapping.get(propertyName);
	}

	/**
	 * プロパティ値(大文字、小文字、アンダースコア無視)を取得します
	 * 
	 * @param obj
	 * @param propertyName
	 * @return プロパティ値
	 */
	public <T> T getValueCI(final Object obj, final String propertyName) {
		return this.getValue(obj, this.propertyNameMapping.get(propertyName));
	}

	/**
	 * プロパティ(大文字、小文字、アンダースコア無視)へ値を設定します
	 * 
	 * @param obj
	 * @param propertyName
	 * @param value
	 * @return <code>true</code>：値を設定成功、<code>false</code>：値を設定失敗
	 */
	public boolean setValueCI(final Object obj, final String propertyName, final Object value) {
		return setValue(obj, this.propertyNameMapping.get(propertyName), value);
	}

	/**
	 * プロパティ(大文字、小文字、アンダースコア無視)へ値を設定します
	 * 
	 * @param obj
	 * @param propertyName
	 * @param value
	 * @param force        <code>true</code>privateでも強制的に値を設定する
	 * @return <code>true</code>：値を設定成功、<code>false</code>：値を設定失敗
	 */
	public boolean setValueCI(final Object obj, final String propertyName, final Object value, final boolean force) {
		return setValue(obj, this.propertyNameMapping.get(propertyName), value, force);
	}

	/**
	 * プロパティへ値を設定します
	 * 
	 * @param caseInsensitive プロパティの大文字小文字を無視
	 * @param obj
	 * @param propertyName
	 * @param value
	 * @return <code>true</code>：値を設定成功、<code>false</code>：値を設定失敗
	 */
	protected boolean setValue(final boolean caseInsensitive, final Object obj, final String propertyName, final Object value) {
		if (caseInsensitive) {
			return setValueCI(obj, propertyName, value);
		} else {
			return setValue(obj, propertyName, value);
		}
	}

	/**
	 * オブジェクトのコレクションのプロパティ(大文字、小文字、アンダースコア無視)へ値を一括設定します
	 * 
	 * @param list         対象のオブジェクトリスト
	 * @param propertyName プロパティ名
	 * @param value        設定する値
	 */
	public void setValuesCI(final Collection<?> list, final String propertyName, final Object value) {
		for (final Object obj : list) {
			setValueCI(obj, propertyName, value);
		}
	}

	/**
	 * オブジェクトのコレクションのプロパティへ値を一括設定します
	 * 
	 * @param list         対象のオブジェクトリスト
	 * @param propertyName プロパティ名
	 * @param value        設定する値
	 */
	public void setValues(final Collection<?> list, final String propertyName, final Object value) {
		for (final Object obj : list) {
			setValue(obj, propertyName, value);
		}
	}

	/**
	 * 指定した名称、型のセッターが存在するかを返します
	 * 
	 * @param caseInsensitive プロパティの大文字小文字を無視
	 * @param propertyName    プロパティ名
	 * @param argClass        セッターの引数の型
	 * @return <code>true</code>:セッターが存在
	 */
	public boolean hasSetter(final boolean caseInsensitive, String propertyName, final Class<?> argClass) {
		if (caseInsensitive) {
			propertyName = getPropertyNameCI(propertyName);
		}
		final Method method = getSetterMethod(propertyName, argClass);
		return method != null;
	}

	/**
	 * プロパティへ値を設定します
	 * 
	 * @param obj          対象のオブジェクト
	 * @param propertyName プロパティ名
	 * @param value        設定する値
	 * @return <code>true</code>：値を設定成功、<code>false</code>：値を設定失敗
	 */
	public boolean setValue(final Object obj, final String propertyName, final Object value) {
		return this.setValue(obj, propertyName, value, false);
	}

	/**
	 * Fieldに値を設定します
	 * 
	 * @param obj       対象のオブジェクト
	 * @param fieldName プロパティ名
	 * @param value     設定する値
	 * @return <code>true</code>：値を設定成功、<code>false</code>：値を設定失敗
	 */
	public boolean setField(final Object obj, final String fieldName, final Object value) {
		Class<?> parameterType = null;
		try {
			final Field field = this.protectedFieldMap.get(fieldName);
			final Converters converters = Converters.getDefault();
			if (field != null) {
				parameterType = field.getType();
				if (converters.isConvertable(parameterType)) {
					if (parameterType.isPrimitive() && value == null) {
						return false;
					} else {
						try {
							field.set(obj, converters.convertObject(value, parameterType));
							return true;
						} catch (final Exception e) {
							return false;
						}
					}
				} else {
					if (value != null && parameterType.isInstance(value)) {
						field.set(obj, value);
						return true;
					}
				}
			}
			return false;
		} catch (final Exception e) {
			throw new InvalidPropertyException(fieldName, value, parameterType, e);
		}
	}

	/**
	 * Fieldから値を取得します
	 * 
	 * @param obj       対象のオブジェクト
	 * @param fieldName プロパティ名
	 */
	@SuppressWarnings("unchecked")
	public <T> T getField(final Object obj, final String fieldName) {
		final Class<?> parameterType = null;
		try {
			final Field field = this.protectedFieldMap.get(fieldName);
			if (field != null) {
				return (T) field.get(obj);
			}
			return null;
		} catch (final Exception e) {
			throw new InvalidPropertyException(fieldName, null, parameterType, e);
		}
	}

	/**
	 * Fieldから値を取得します
	 * 
	 * @param obj          対象のオブジェクト
	 * @param propertyName プロパティ名
	 */
	public <T> T getFieldCI(final Object obj, final String propertyName) {
		return getField(obj, this.propertyNameMapping.get(propertyName));
	}

	/**
	 * Field(大文字、小文字、アンダースコア無視)へ値を設定します
	 * 
	 * @param obj
	 * @param propertyName
	 * @param value
	 * @return <code>true</code>：値を設定成功、<code>false</code>：値を設定失敗
	 */
	public boolean setFieldCI(final Object obj, final String propertyName, final Object value) {
		return setField(obj, this.propertyNameMapping.get(propertyName), value);
	}

	/**
	 * プロパティへ値を設定します
	 * 
	 * @param obj          対象のオブジェクト
	 * @param propertyName プロパティ名
	 * @param value        設定する値
	 * @param force        <code>true</code>privateでも強制的に値を設定する
	 * @return <code>true</code>：値を設定成功、<code>false</code>：値を設定失敗
	 */
	public boolean setValue(final Object obj, final String propertyName, final Object value, final boolean force) {
		Class<?> parameterType = null;
		Class<?> valueClass = null;
		try {
			if (value != null) {
				final Map<Class<?>, Method> classMap = setterOverloadMap.get(propertyName);
				valueClass = value.getClass();
				if (classMap != null) {
					Method method = classMap.get(valueClass);
					if (method != null) {
						method.invoke(obj, value);
						return true;
					} else {
						for (final Map.Entry<Class<?>, Method> entry : classMap.entrySet()) {
							if (entry.getKey().isAssignableFrom(valueClass)) {
								method = entry.getValue();
								classMap.put(valueClass, method);
								method.invoke(obj, value);
								return true;
							}
						}
					}
				}
			}
			final Converters converters = Converters.getDefault();
			final Method method = setterMap.get(propertyName);
			if (method != null) {
				parameterType = method.getParameterTypes()[0];
				if (converters.isConvertable(parameterType)) {
					if (parameterType.isPrimitive() && value == null) {
						return false;
					} else {
						try {
							method.invoke(obj, converters.convertObject(value, parameterType));
							return true;
						} catch (final Exception e) {
							return false;
						}
					}
				} else {
					if (value == null) {
						method.invoke(obj, value);
					} else if (CommonUtils.isAssignableFrom(parameterType, value.getClass())) {
						method.invoke(obj, value);
					} else {
						final Object toObj = SimpleBeanUtils.convert(value, parameterType);
						method.invoke(obj, toObj);
					}
				}
				return true;
			}
			Field field = getField(propertyName);
			if (force) {
				field = getProtectedField(propertyName);
			}
			if (field != null) {
				if (!force) {
					if (Modifier.isFinal(field.getModifiers())) {
						return false;
					}
				}
				parameterType = field.getType();
				if (converters.isConvertable(parameterType)) {
					if (parameterType.isPrimitive() && value == null) {
						return false;
					} else {
						try {
							field.set(obj, converters.convertObject(value, parameterType));
							return true;
						} catch (final Exception e) {
							return false;
						}
					}
				} else {
					if (value == null) {
						field.set(obj, value);
					} else if (CommonUtils.isAssignableFrom(parameterType, value.getClass())) {
						field.set(obj, value);
					} else {
						final Object toObj = SimpleBeanUtils.convert(value, parameterType);
						field.set(obj, toObj);
					}
				}
				return true;
			}
			return false;
		} catch (final Exception e) {
			throw new InvalidPropertyException(propertyName, value, parameterType, e);
		}
	}

	private Method getSetterMethod(final String propertyName, final Class<?> argClass) {
		final Map<Class<?>, Method> classMap = setterOverloadMap.get(propertyName);
		if (classMap != null) {
			Method method = classMap.get(argClass);
			if (method != null) {
				return method;
			}
			for (final Map.Entry<Class<?>, Method> entry : classMap.entrySet()) {
				if (entry.getKey().isAssignableFrom(argClass)) {
					method = entry.getValue();
					classMap.put(argClass, entry.getValue());
					return method;
				}
			}
		}
		return null;
	}

	private Field getField(final String propertyName) {
		final Field field = fieldMap.get(propertyName);
		return field;
	}

	private Field getProtectedField(final String propertyName) {
		final Field field = this.protectedFieldMap.get(propertyName);
		return field;
	}

	/**
	 * オブジェクトのコレクションを指定したプロパティをキーにしたマップに変換します(大文字、小文字、アンダースコア無視)
	 * 
	 * @param fromCllection 変換元のオブジェクトのリスト
	 * @param propertyName  キーになるプロパティ名
	 * @return 指定したプロパティをキーにしたマップ
	 */
	@SuppressWarnings("unchecked")
	public <S, T> Map<S, T> convertMap(final Collection<T> fromCllection, final String propertyName) {
		if (fromCllection == null) {
			return null;
		}
		final Map<S, T> map = new LinkedHashMap<S, T>();
		if (fromCllection.size() == 0) {
			return map;
		}
		for (final Object obj : fromCllection) {
			final Object key = getValue(obj, propertyName);
			map.put((S) key, (T) obj);
		}
		return map;
	}

	/**
	 * オブジェクトのコレクションを指定したプロパティをキーにしたマップに変換します
	 * 
	 * @param fromCllection 変換元のオブジェクトのリスト
	 * @param propertyName  キーになるプロパティ名
	 * @return 指定したプロパティをキーにしたマップ
	 */
	@SuppressWarnings("unchecked")
	public <S, T> Map<S, T> convertMapCI(final Collection<T> fromCllection, final String propertyName) {
		if (fromCllection == null) {
			return null;
		}
		final Map<S, T> map = new LinkedHashMap<S, T>();
		if (fromCllection.size() == 0) {
			return map;
		}
		for (final Object obj : fromCllection) {
			final Object key = getValueCI(obj, propertyName);
			map.put((S) key, (T) obj);
		}
		return map;
	}

	/**
	 * プロパティをコピーします
	 * 
	 * @param fromObj コピー元のプロパティ
	 * @param toObj   コピー先のプロパティ
	 */
	public void copyProperties(final Object fromObj, final Object toObj) {
		if (fromObj == null) {
			return;
		}
		if (toObj == null) {
			return;
		}
		final SimpleBeanWrapper wrapper = SimpleBeanUtils.getInstance(toObj.getClass());
		final Map<String, Object> map = toMap(fromObj);
		for (final Map.Entry<String, Object> entry : map.entrySet()) {
			wrapper.setValue(toObj, entry.getKey(), entry.getValue());
		}
	}

	/**
	 * プロパティ(大文字、小文字、アンダースコア無視)をコピーします
	 * 
	 * @param fromObj コピー元のプロパティ
	 * @param toObj   コピー先のプロパティ
	 */
	public void copyPropertiesCI(final Object fromObj, final Object toObj) {
		if (fromObj == null) {
			return;
		}
		if (toObj == null) {
			return;
		}
		final SimpleBeanWrapper wrapper = SimpleBeanUtils.getInstance(toObj.getClass());
		final Map<String, Object> map = toMap(fromObj);
		for (final Map.Entry<String, Object> entry : map.entrySet()) {
			wrapper.setValueCI(toObj, entry.getKey(), entry.getValue());
		}
	}

	/**
	 * オブジェクトが該当のクラスか判定します
	 * 
	 * @param value 判定対象のオブジェクト
	 * @return true:対象のクラス
	 */
	public boolean is(final Object value) {
		return value.getClass() == clazz;
	}

	/**
	 * プロパティを持っているか?
	 * 
	 * @param name プロパティ名
	 * @return trueの場合、プロパティが存在
	 */
	public boolean hasProperty(final String name) {
		return propertyNameMapping.containsKey(name);
	}

	/**
	 * プロパティ(大文字、小文字、アンダースコア無視)を持っているか?
	 * 
	 * @param name プロパティ名
	 * @return trueの場合、プロパティが存在
	 */
	public boolean hasPropertyCI(final String name) {
		return this.propertyNameMapping.containsKey(name);
	}

	/**
	 * プロパティ名のセットを取得します
	 * 
	 * @return プロパティ名のセット
	 */
	public Set<String> getPropertyNames() {
		return this.propertyNameSet;
	}

	/**
	 * オブジェクトをマップに変換します
	 * 
	 * @param val マップに変換するオブジェクト
	 * @return 変換後のマップ
	 */
	public Map<String, Object> toMap(final Object val) {
		if (val == null) {
			return null;
		}
		final Map<String, Object> result = new LinkedHashMap<String, Object>();
		if (val instanceof Map) {
			final Map<?, ?> map = (Map<?, ?>) val;
			for (final Map.Entry<?, ?> entry : map.entrySet()) {
				if (entry.getKey() instanceof String) {
					result.put((String) entry.getKey(), entry.getValue());
				} else if (entry.getKey() == null) {
					result.put(null, entry.getValue());
				} else {
					result.put(entry.getKey().toString(), entry.getValue());
				}
			}
		} else {
			for (final String name : this.propertyNameSet) {
				result.put(name, getValue(val, name));
			}
		}
		return result;
	}

	/**
	 * オブジェクトをマップに変換します
	 * 
	 * @param val               マップに変換するオブジェクト
	 * @param simpleBeanWrapper マップに変換対象のプロパティを判定するSimpleBeanWrapper
	 * @param caseInsensitive
	 * @return 変換後のマップ
	 */
	protected Map<String, Object> toMap(final Object val, final SimpleBeanWrapper simpleBeanWrapper, final boolean caseInsensitive) {
		if (val == null) {
			return null;
		}
		final Map<String, Object> result = new LinkedHashMap<String, Object>();
		if (val instanceof Map) {
			final Map<?, ?> map = (Map<?, ?>) val;
			for (final Map.Entry<?, ?> entry : map.entrySet()) {
				String key = null;
				if (entry.getKey() instanceof String) {
					key = (String) entry.getKey();
				} else if (entry.getKey() == null) {
					key = (String) entry.getKey();
				} else {
					key = entry.getKey().toString();
				}
				if (caseInsensitive) {
					if (simpleBeanWrapper.hasPropertyCI(key)) {
						result.put(key, entry.getValue());
					}
				} else {
					if (simpleBeanWrapper.hasProperty(key)) {
						result.put(key, entry.getValue());
					}
				}
			}
		} else {
			for (final String name : this.propertyNameSet) {
				if (caseInsensitive) {
					if (simpleBeanWrapper.hasPropertyCI(name)) {
						result.put(name, getValue(val, name));
					}
				} else {
					if (simpleBeanWrapper.hasProperty(name)) {
						result.put(name, getValue(val, name));
					}
				}
			}
		}
		return result;
	}

	/**
	 * オブジェクトをツリーマップに変換します
	 * 
	 * @param val ツリーマップに変換するオブジェクト
	 * @return 変換後のマップ
	 */
	public Map<String, Object> toTreeMap(final Object val) {
		if (val == null) {
			return null;
		}
		final Map<String, Object> result = new TreeMap<String, Object>();
		if (val instanceof Map) {
			final Map<?, ?> map = (Map<?, ?>) val;
			for (final Map.Entry<?, ?> entry : map.entrySet()) {
				if (entry.getKey() instanceof String) {
					result.put((String) entry.getKey(), entry.getValue());
				} else if (entry.getKey() == null) {
					result.put(null, entry.getValue());
				} else {
					result.put(entry.getKey().toString(), entry.getValue());
				}
			}
		} else {
			for (final String name : this.propertyNameSet) {
				result.put(name, getValue(val, name));
			}
		}
		return result;
	}

	/**
	 * Beanの全Getterのアノテーションを返します
	 * 
	 * @return プロパティ名、Getterのアノテーションのマップ
	 */
	public Map<String, Annotation[]> getGetterAnnotationMap() {
		final Map<String, Annotation[]> map = CommonUtils.map();
		for (final String name : this.getPropertyNames()) {
			final Method method = getterMap.get(name);
			if (method != null) {
				final Annotation[] annotations = method.getAnnotations();
				if (!CommonUtils.isEmpty(annotations)) {
					map.put(name, annotations);
				}
			}
		}
		return map;
	}

	/**
	 * Beanの全Setterのアノテーションを返します
	 * 
	 * @return プロパティ名、Setterのアノテーションのマップ
	 */
	public Map<String, Annotation[]> getSetterAnnotationMap() {
		final Map<String, Annotation[]> map = CommonUtils.map();
		for (final String name : this.getPropertyNames()) {
			final Method method = setterMap.get(name);
			if (method != null) {
				final Annotation[] annotations = method.getAnnotations();
				if (!CommonUtils.isEmpty(annotations)) {
					map.put(name, annotations);
				}
			}
		}
		return map;
	}

	/**
	 * Beanの全Fieldのアノテーションを返します
	 * 
	 * @return プロパティ名、Fieldのアノテーションのマップ
	 */
	public Map<String, Annotation[]> getFieldAnnotationMap() {
		final Map<String, Annotation[]> map = CommonUtils.map();
		for (final String name : this.getPropertyNames()) {
			Field field = fieldMap.get(name);
			if (field != null) {
				final Annotation[] annotations = field.getAnnotations();
				if (!CommonUtils.isEmpty(annotations)) {
					map.put(name, annotations);
				}
			}
			field = protectedFieldMap.get(name);
			if (field != null) {
				final Annotation[] annotations = field.getAnnotations();
				if (!CommonUtils.isEmpty(annotations)) {
					map.put(name, annotations);
				}
			}
		}
		return map;
	}

	/**
	 * Beanの全Fieldのアノテーションを返します
	 * 
	 * @return プロパティ名、Fieldのアノテーションのマップ
	 */
	public Map<String, Annotation[]> getPropertyAnnotationMap() {
		final Map<String, Annotation[]> getterAnnotationMap = getGetterAnnotationMap();
		final Map<String, Annotation[]> setterAnnotationMap = getSetterAnnotationMap();
		final Map<String, Annotation[]> fieldAnnotationMap = getFieldAnnotationMap();
		final Map<String, Annotation[]> map = CommonUtils.map();
		for (final String name : this.getPropertyNames()) {
			final List<Annotation> list = CommonUtils.list();
			Annotation[] annotations = getterAnnotationMap.get(name);
			if (annotations != null) {
				for (final Annotation annotation : annotations) {
					list.add(annotation);
				}
			}
			annotations = setterAnnotationMap.get(name);
			if (annotations != null) {
				for (final Annotation annotation : annotations) {
					list.add(annotation);
				}
			}
			annotations = fieldAnnotationMap.get(name);
			if (annotations != null) {
				for (final Annotation annotation : annotations) {
					list.add(annotation);
				}
			}
			if (!list.isEmpty()) {
				map.put(name, list.toArray(new Annotation[0]));
			}
		}
		return map;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		final ToStringBuilder builder = new ToStringBuilder(this.getClass());
		toString(builder);
		return builder.toString();
	}

	protected void toString(final ToStringBuilder builder) {
		builder.add("clazz", clazz);
	}

	private final DoubleKeyMap<String, Integer, Method[]> METHOD_CACHE = CommonUtils.doubleKeyMap();

	/**
	 * オブジェクトの指定した名称のメソッドを呼び出します
	 * 
	 * @param obj
	 * @param name
	 * @param args
	 */
	@SuppressWarnings("unchecked")
	public <T> T invoke(final Object obj, final String name, final Object... args) {
		final MethodArgs methodArgs = getMethod(name, args);
		if (methodArgs == null) {
			throw new RuntimeException(new NoSuchMethodException(name));
		} else {
			try {
				return (T) methodArgs.method.invoke(obj, methodArgs.args);
			} catch (final IllegalAccessException e) {
				throw new RuntimeException(e);
			} catch (final IllegalArgumentException e) {
				throw new RuntimeException(e);
			} catch (final InvocationTargetException e) {
				throw SimpleBeanUtils.throwInvocationTargetException(e);
			}
		}
	}

	/**
	 * オブジェクトの指定した名称のメソッドが存在するかを判定します
	 * 
	 * @param name
	 * @param args
	 */
	public boolean hasMethod(final String name, final Object... args) {
		final MethodArgs methodArgs = getMethod(name, args);
		if (methodArgs == null) {
			return false;
		} else {
			return true;
		}
	}

	protected MethodArgs getMethod(final String name, final Object... args) {
		Method[] methods = METHOD_CACHE.get(name, args.length);
		if (methods == null) {
			synchronized (METHOD_CACHE) {
				final List<Method> list = CommonUtils.list();
				for (final Method method : clazz.getMethods()) {
					if (!CommonUtils.eq(method.getName(), name)) {
						continue;
					}
					final Class<?>[] parameterTypes = method.getParameterTypes();
					if (parameterTypes.length != args.length) {
						continue;
					}
					list.add(method);
				}
				methods = list.toArray(new Method[0]);
				METHOD_CACHE.put(name, args.length, methods);
			}
		}
		final MethodArgs methodArgs = new MethodArgs();
		methodArgs.args = new Object[args.length];
		System.arraycopy(args, 0, methodArgs.args, 0, args.length);
		for (final Method method : methods) {
			final Class<?>[] parameterTypes = method.getParameterTypes();
			boolean match = true;
			final Converters converters = Converters.getDefault();
			for (int i = 0; i < args.length; i++) {
				final Object arg = args[i];
				final Class<?> paramClass = parameterTypes[i];
				if (arg == null) {
					if (paramClass.isPrimitive()) {
						match = false;
						break;
					}
				} else {
					final Class<?> argClass = arg.getClass();
					if (paramClass == argClass) {
						continue;
					} else if (paramClass.isAssignableFrom(argClass)) {
						continue;
					} else {
						if (converters.isConvertable(paramClass) && converters.isConvertable(argClass)) {
							methodArgs.args[i] = converters.convertObject(args[i], paramClass);
							continue;
						}
						match = false;
						break;
					}
				}
			}
			if (match) {
				methodArgs.method = method;
				return methodArgs;
			}
		}
		return null;
	}

	private static class MethodArgs {
		Method method;
		Object[] args;
	}

}
