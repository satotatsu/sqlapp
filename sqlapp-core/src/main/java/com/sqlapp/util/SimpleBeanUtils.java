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

import static com.sqlapp.util.CommonUtils.classForName;
import static com.sqlapp.util.CommonUtils.concurrentMap;

import java.lang.annotation.Annotation;
import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.sqlapp.data.converter.Converters;

/**
 * 単純なリフレクション用のユーティリティ
 * 
 * @author satoh
 * 
 */
public class SimpleBeanUtils {

	private static final Map<Class<?>, SimpleBeanWrapper> INSTANCE_CACHE = concurrentMap();
	/**
	 * 既定のコンバーター
	 */
	private static Converters converters = Converters.getDefault();

	private SimpleBeanUtils() {
	}

	/**
	 * @return the converters
	 */
	public static Converters getConverters() {
		return converters;
	}

	/**
	 * キャッシュからインスタンスを取得します
	 * 
	 * @param clazz
	 *            クラス
	 * @return 指定したクラスに対応したラッパークラス
	 */
	public static SimpleBeanWrapper getInstance(Class<?> clazz) {
		SimpleBeanWrapper instance = INSTANCE_CACHE.get(clazz);
		if (instance != null) {
			return instance;
		}
		instance = new SimpleBeanWrapper(clazz);
		SimpleBeanWrapper org = INSTANCE_CACHE.putIfAbsent(clazz, instance);
		return org != null ? org : instance;
	}

	/**
	 * キャッシュからインスタンスを取得します
	 * 
	 * @param className
	 *            クラス名
	 * @return 指定したクラスに対応したラッパークラス
	 */
	public static SimpleBeanWrapper getInstance(String className) {
		Class<?> clazz = classForName(className);
		return getInstance(clazz);
	}

	/**
	 * プロパティ値を取得します
	 * 
	 * @param obj
	 *            対象のオブジェクト
	 * @param propertyName
	 *            プロパティ名
	 * @return プロパティ値
	 */
	@SuppressWarnings("unchecked")
	public static <T> T getValue(Object obj, String propertyName) {
		if (obj instanceof Map) {
			Map<?,?> map=Map.class.cast(obj);
			if (map.containsKey(propertyName)) {
				return (T)map.get(propertyName);
			}
		}
		SimpleBeanWrapper wrapper = SimpleBeanUtils.getInstance(obj.getClass());
		return wrapper.getValue(obj, propertyName);
	}

	/**
	 * プロパティ値(大文字、小文字、アンダースコア無視)を取得します
	 * 
	 * @param obj
	 * @param propertyName
	 * @return プロパティ値
	 */
	@SuppressWarnings("unchecked")
	public static <T> T getValueCI(Object obj, String propertyName) {
		if (obj instanceof Map) {
			Map<?,?> map=Map.class.cast(obj);
			if (map.containsKey(propertyName)) {
				return (T)map.get(propertyName);
			}else if (map.containsKey(propertyName.toLowerCase())) {
				return (T)map.get(propertyName.toLowerCase());
			}else if (map.containsKey(propertyName.toUpperCase())) {
				return (T)map.get(propertyName.toUpperCase());
			}
			for(Map.Entry<?,?> entry:map.entrySet()) {
				String key=entry.getKey().toString();
				if (key.equalsIgnoreCase(propertyName)) {
					return (T)entry.getValue();
				}
			}
		}
		SimpleBeanWrapper wrapper = SimpleBeanUtils.getInstance(obj.getClass());
		return wrapper.getValueCI(obj, propertyName);
	}

	/**
	 * プロパティ(大文字、小文字、アンダースコア無視)へ値を設定します
	 * 
	 * @param obj
	 * @param propertyName
	 * @param value
	 * @return <code>true</code>：値を設定成功、<code>false</code>：値を設定失敗
	 */
	public static boolean setValueCI(Object obj, String propertyName,
			Object value) {
		return setValueCI(obj, propertyName, value, false);
	}

	/**
	 * プロパティ(大文字、小文字、アンダースコア無視)へ値を設定します
	 * 
	 * @param obj
	 * @param propertyName
	 * @param value
	 * @param force
	 *            <code>true</code>privateでも強制的に値を設定する
	 * @return <code>true</code>：値を設定成功、<code>false</code>：値を設定失敗
	 */
	@SuppressWarnings("unchecked")
	public static boolean setValueCI(Object obj, String propertyName,
			Object value, boolean force) {
		SimpleBeanWrapper wrapper = SimpleBeanUtils.getInstance(obj.getClass());
		boolean bool= wrapper.setValueCI(obj, propertyName, value, force);
		if (bool) {
			return bool;
		}
		if (obj instanceof Map) {
			Map<String,Object> map=Map.class.cast(obj);
			for(Map.Entry<String,?> entry:map.entrySet()) {
				String key=entry.getKey().toString();
				if (key.equalsIgnoreCase(propertyName)) {
					map.put(key, value);
					return true;
				}
			}
			map.put(propertyName, value);
			return true;
		}
		return false;
	}

	/**
	 * オブジェクトのコレクションのプロパティ(大文字、小文字、アンダースコア無視)へ値を一括設定します
	 * 
	 * @param list
	 *            対象のオブジェクトリスト
	 * @param propertyName
	 *            プロパティ名
	 * @param value
	 *            設定する値
	 */
	public static void setValuesCI(Collection<?> list, String propertyName,
			Object value) {
		for (Object obj : list) {
			setValueCI(obj, propertyName, value);
		}
	}

	/**
	 * オブジェクトのコレクションのプロパティへ値を一括設定します
	 * 
	 * @param list
	 *            対象のオブジェクトリスト
	 * @param propertyName
	 *            プロパティ名
	 * @param value
	 *            設定する値
	 */
	public static void setValues(Collection<?> list, String propertyName,
			Object value) {
		for (Object obj : list) {
			setValue(obj, propertyName, value);
		}
	}

	/**
	 * プロパティへ値を設定します
	 * 
	 * @param obj
	 *            対象のオブジェクト
	 * @param propertyName
	 *            プロパティ名
	 * @param value
	 *            設定する値
	 * @return <code>true</code>：値を設定成功、<code>false</code>：値を設定失敗
	 */
	public static boolean setValue(Object obj, String propertyName, Object value) {
		return setValue(obj, propertyName, value, false);
	}

	/**
	 * プロパティへ値を設定します
	 * 
	 * @param obj
	 *            対象のオブジェクト
	 * @param propertyName
	 *            プロパティ名
	 * @param value
	 *            設定する値
	 * @param force
	 *            <code>true</code>privateでも強制的に値を設定する
	 * @return <code>true</code>：値を設定成功、<code>false</code>：値を設定失敗
	 */
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public static boolean setValue(Object obj, String propertyName,
			Object value, boolean force) {
		SimpleBeanWrapper wrapper = SimpleBeanUtils.getInstance(obj.getClass());
		boolean bool= wrapper.setValue(obj, propertyName, value, force);
		if (bool) {
			return bool;
		}
		if (obj instanceof Map) {
			Map map=Map.class.cast(obj);
			map.put(propertyName, value);
		}
		return false;
	}
	
	/**
	 * Fieldから値を取得します
	 * 
	 * @param obj
	 *            対象のオブジェクト
	 * @param fieldName
	 *            Field Name
	 * @return Field Value
	 */
	public static <T> T getField(Object obj, String fieldName) {
		SimpleBeanWrapper wrapper = SimpleBeanUtils.getInstance(obj.getClass());
		return wrapper.getField(obj, fieldName);
	}
	
	/**
	 * Fieldから値を取得します
	 * 
	 * @param obj
	 *            対象のオブジェクト
	 * @param fieldName
	 *            Field Name
	 * @return Field Value
	 */
	public static <T> T getFieldCI(Object obj, String fieldName) {
		SimpleBeanWrapper wrapper = SimpleBeanUtils.getInstance(obj.getClass());
		return wrapper.getFieldCI(obj, fieldName);
	}
	
	/**
	 * Fieldへ値を設定します
	 * 
	 * @param obj
	 *            対象のオブジェクト
	 * @param fieldName
	 *            プロパティ名
	 * @param value
	 *            設定する値
	 * @return <code>true</code>：値を設定成功、<code>false</code>：値を設定失敗
	 */
	public static boolean setField(Object obj, String fieldName,
			Object value) {
		SimpleBeanWrapper wrapper = SimpleBeanUtils.getInstance(obj.getClass());
		return wrapper.setField(obj, fieldName, value);
	}
	
	/**
	 * Field(大文字、小文字、アンダースコア無視)へ値を設定します
	 * 
	 * @param obj
	 * @param fieldName
	 * @param value
	 * @return <code>true</code>：値を設定成功、<code>false</code>：値を設定失敗
	 */
	public static boolean setFieldCI(Object obj, String fieldName,
			Object value) {
		SimpleBeanWrapper wrapper = SimpleBeanUtils.getInstance(obj.getClass());
		return wrapper.setFieldCI(obj, fieldName, value);
	}

	/**
	 * オブジェクトを指定したクラスに変換します
	 * 
	 * @param fromObj
	 *            変換元のオブジェクト
	 * @param toClazz
	 *            変換先のクラス
	 * @return 指定したクラス
	 */
	public static <T> T convert(Object fromObj, Class<T> toClazz) {
		return convert(false, fromObj, toClazz);
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	private static Map toMap(Object fromObj, Class toClazz) {
		SimpleBeanWrapper toUtils = SimpleBeanUtils.getInstance(toClazz);
		Map toObj = toUtils.newInstance();
		toObj.putAll(SimpleBeanUtils.toMap(fromObj));
		return toObj;
	}

	/**
	 * オブジェクトを指定したクラスに変換します(大文字、小文字、アンダースコア無視)
	 * 
	 * @param fromObj
	 *            変換元のオブジェクト
	 * @param toClazz
	 *            変換先のクラス
	 * @return 指定したクラス
	 */
	public static <T> T convertCI(Object fromObj, Class<T> toClazz) {
		return convert(true, fromObj, toClazz);
	}

	/**
	 * オブジェクトを指定したクラスに変換します(大文字、小文字、アンダースコア無視)
	 * 
	 * @param caseInsensitive
	 *            プロパティの大文字小文字を無視
	 * @param fromObj
	 *            変換元のオブジェクト
	 * @param toClazz
	 *            変換先のクラス
	 * @return 指定したクラス
	 */
	@SuppressWarnings("unchecked")
	protected static <T> T convert(boolean caseInsensitive, Object fromObj,
			Class<T> toClazz) {
		if (fromObj == null) {
			return null;
		}
		if (Map.class.isAssignableFrom(toClazz)) {
			@SuppressWarnings("rawtypes")
			Map toObj = toMap(fromObj, toClazz);
			return (T) toObj;
		} else {
			SimpleBeanWrapper toUtils = SimpleBeanUtils.getInstance(toClazz);
			Object toObj = toUtils.newInstance();
			copyProperties(caseInsensitive, fromObj, toObj);
			return (T) toObj;
		}
	}

	/**
	 * オブジェクトのセットを指定したクラスのリストに変換します(大文字、小文字、アンダースコア無視)
	 * 
	 * @param fromSet
	 *            変換元のオブジェクトのセット
	 * @param toClazz
	 *            変換先のクラス
	 * @return 指定したクラスのリスト
	 */
	public static <T> Set<T> convertSetCI(Collection<?> fromSet,
			Class<T> toClazz) {
		return convertSet(true, fromSet, toClazz);
	}

	/**
	 * オブジェクトのセットを指定したクラスのリストに変換します
	 * 
	 * @param fromSet
	 *            変換元のオブジェクトのセット
	 * @param toClazz
	 *            変換先のクラス
	 * @return 指定したクラスのリスト
	 */
	public static <T> Set<T> convertSet(Collection<?> fromSet, Class<T> toClazz) {
		return convertSet(false, fromSet, toClazz);
	}

	/**
	 * オブジェクトのセットを指定したクラスのセットに変換します
	 * 
	 * @param caseInsensitive
	 *            プロパティの大文字小文字を無視
	 * @param fromSet
	 *            変換元のオブジェクトのセット
	 * @param toClazz
	 *            変換先のクラス
	 * @return 指定したクラスのセット
	 */
	protected static <T> Set<T> convertSet(boolean caseInsensitive,
			Collection<?> fromSet, Class<T> toClazz) {
		if (fromSet == null) {
			return null;
		}
		if (fromSet.size() == 0) {
			return new LinkedHashSet<T>();
		}
		LinkedHashSet<T> result = new LinkedHashSet<T>(fromSet.size());
		SimpleBeanWrapper toUtils = SimpleBeanUtils.getInstance(toClazz);
		for (Object obj : fromSet) {
			T toObj = toUtils.newInstance();
			copyProperties(caseInsensitive, obj, toObj);
			result.add(toObj);
		}
		return result;
	}

	/**
	 * オブジェクトのコレクションを指定したプロパティのセットに変換します
	 * 
	 * @param caseInsensitive
	 *            プロパティの大文字小文字を無視
	 * @param fromCllection
	 *            変換元のオブジェクトのリスト
	 * @param propertyName
	 *            キーになるプロパティ名
	 * @return 指定したプロパティのセット
	 */
	protected static <T> Set<T> convertSet(boolean caseInsensitive,
			Collection<?> fromCllection, String propertyName) {
		if (fromCllection == null) {
			return null;
		}
		Set<T> set = CommonUtils.linkedSet(fromCllection.size());
		if (fromCllection.size() == 0) {
			return set;
		}
		for (Object obj : fromCllection) {
			T value = getValueRecursive(caseInsensitive, obj, propertyName);
			set.add(value);
		}
		return set;
	}

	/**
	 * オブジェクトのコレクションを指定したプロパティのセットに変換します
	 * 
	 * @param fromCllection
	 *            変換元のオブジェクトのリスト
	 * @param propertyName
	 *            キーになるプロパティ名
	 * @return 指定したプロパティのセット
	 */
	public static <T> Set<T> convertSet(Collection<?> fromCllection,
			String propertyName) {
		return convertSet(false, fromCllection, propertyName);
	}

	/**
	 * オブジェクトのコレクションを指定したプロパティのセットに変換します(大文字、小文字、アンダースコア無視)
	 * 
	 * @param fromCllection
	 *            変換元のオブジェクトのリスト
	 * @param propertyName
	 *            キーになるプロパティ名
	 * @return 指定したプロパティのセット
	 */
	public static <T> Set<T> convertSetCI(Collection<?> fromCllection,
			String propertyName) {
		return convertSet(true, fromCllection, propertyName);
	}

	/**
	 * オブジェクトのリストを指定したクラスのリストに変換します(大文字、小文字、アンダースコア無視)
	 * 
	 * @param fromList
	 *            変換元のオブジェクトのリスト
	 * @param toClazz
	 *            変換先のクラス
	 * @return 指定したクラスのリスト
	 */
	public static <T> List<T> convertListCI(List<?> fromList, Class<T> toClazz) {
		return convertList(true, fromList, toClazz);
	}

	/**
	 * オブジェクトのリストを指定したクラスのリストに変換します
	 * 
	 * @param fromList
	 *            変換元のオブジェクトのリスト
	 * @param toClazz
	 *            変換先のクラス
	 * @return 指定したクラスのリスト
	 */
	public static <T> List<T> convertList(List<?> fromList, Class<T> toClazz) {
		return convertList(false, fromList, toClazz);
	}

	/**
	 * オブジェクトのリストを指定したクラスのリストに変換します
	 * 
	 * @param caseInsensitive
	 *            プロパティの大文字小文字を無視
	 * @param fromList
	 *            変換元のオブジェクトのリスト
	 * @param toClazz
	 *            変換先のクラス
	 * @return 指定したクラスのリスト
	 */
	protected static <T> List<T> convertList(boolean caseInsensitive,
			List<?> fromList, Class<T> toClazz) {
		if (fromList == null) {
			return null;
		}
		if (fromList.size() == 0) {
			return new ArrayList<T>();
		}
		List<T> result = new ArrayList<T>(fromList.size());
		SimpleBeanWrapper toUtils = SimpleBeanUtils.getInstance(toClazz);
		for (int i = 0; i < fromList.size(); i++) {
			T toObj = toUtils.newInstance();
			copyProperties(caseInsensitive, fromList.get(i), toObj);
			result.add(toObj);
		}
		return result;
	}

	/**
	 * オブジェクトのコレクションを指定したプロパティのリストに変換します
	 * 
	 * @param caseInsensitive
	 *            プロパティの大文字小文字を無視
	 * @param fromCllection
	 *            変換元のオブジェクトのリスト
	 * @param propertyName
	 *            キーになるプロパティ名
	 * @return 指定したプロパティのリスト
	 */
	protected static <T> List<T> convertList(boolean caseInsensitive,
			Collection<?> fromCllection, String propertyName) {
		if (fromCllection == null) {
			return null;
		}
		List<T> list = CommonUtils.list(fromCllection.size());
		if (fromCllection.size() == 0) {
			return list;
		}
		for (Object obj : fromCllection) {
			T value = getValueRecursive(caseInsensitive, obj, propertyName);
			list.add(value);
		}
		return list;
	}

	/**
	 * オブジェクトのコレクションを指定したプロパティのリストに変換します
	 * 
	 * @param fromCllection
	 *            変換元のオブジェクトのリスト
	 * @param propertyName
	 *            キーになるプロパティ名
	 * @return 指定したプロパティのリスト
	 */
	public static <T> List<T> convertList(Collection<?> fromCllection,
			String propertyName) {
		return convertList(false, fromCllection, propertyName);
	}

	/**
	 * オブジェクトのコレクションを指定したプロパティのリストに変換します
	 * 
	 * @param fromCllection
	 *            変換元のオブジェクトのリスト
	 * @param propertyName
	 *            キーになるプロパティ名
	 * @return 指定したプロパティのリスト
	 */
	public static <T> List<T> convertListCI(Collection<?> fromCllection,
			String propertyName) {
		return convertList(true, fromCllection, propertyName);
	}

	/**
	 * オブジェクトのコレクションを指定したプロパティをキーにしたマップに変換します(大文字、小文字、アンダースコア無視)
	 * 
	 * @param caseInsensitive
	 *            プロパティの大文字小文字を無視
	 * @param fromCllection
	 *            変換元のオブジェクトのリスト
	 * @param propertyName
	 *            キーになるプロパティ名
	 * @return プロパティをキーにしたマップ
	 */
	@SuppressWarnings("unchecked")
	protected static <S, T> Map<S, T> convertMap(boolean caseInsensitive,
			Collection<T> fromCllection, String propertyName) {
		if (fromCllection == null) {
			return null;
		}
		Map<S, T> map = new LinkedHashMap<S, T>();
		if (fromCllection.size() == 0) {
			return map;
		}
		for (Object obj : fromCllection) {
			Object key = getValueRecursive(caseInsensitive, obj, propertyName);
			map.put((S) key, (T) obj);
		}
		return map;
	}

	private static <T> T getValueRecursive(boolean caseInsensitive, Object obj,
			String propertyName) {
		if (obj == null) {
			return null;
		}
		SimpleBeanWrapper utils = SimpleBeanUtils.getInstance(obj.getClass());
		String[] args = propertyName.split("\\.");
		if (args.length == 1) {
			return utils.getValue(caseInsensitive, obj, propertyName);
		}
		Object value = utils.getValue(caseInsensitive, obj, args[0]);
		return getValueRecursive(caseInsensitive, value,
				propertyName.replaceFirst(args[0] + "\\.", ""));
	}

	/**
	 * オブジェクトのコレクションを指定したプロパティをキーにしたマップに変換します
	 * 
	 * @param fromCllection
	 *            変換元のオブジェクトのリスト
	 * @param propertyName
	 *            キーになるプロパティ名
	 * @return プロパティをキーにしたマップ
	 */
	public static <S, T> Map<S, T> convertMap(Collection<T> fromCllection,
			String propertyName) {
		return convertMap(false, fromCllection, propertyName);
	}

	/**
	 * オブジェクトのコレクションを指定したプロパティをキーにしたマップに変換します
	 * 
	 * @param fromCllection
	 *            変換元のオブジェクトのリスト
	 * @param propertyName
	 *            キーになるプロパティ名
	 * @return プロパティをキーにしたマップ
	 */
	public static <S, T> Map<S, T> convertMapCI(Collection<T> fromCllection,
			String propertyName) {
		return convertMap(true, fromCllection, propertyName);
	}

	/**
	 * プロパティをコピーします
	 * 
	 * @param fromObj
	 *            コピー元のプロパティ
	 * @param toObj
	 *            コピー先のプロパティ
	 */
	public static void copyProperties(Object fromObj, Object toObj) {
		copyProperties(false, fromObj, toObj);
	}

	/**
	 * プロパティ(大文字、小文字、アンダースコア無視)をコピーします
	 * 
	 * @param fromObj
	 *            コピー元のプロパティ
	 * @param toObj
	 *            コピー先のプロパティ
	 */
	public static void copyPropertiesCI(Object fromObj, Object toObj) {
		copyProperties(true, fromObj, toObj);
	}

	/**
	 * プロパティをコピーします
	 * 
	 * @param caseInsensitive
	 *            プロパティの大文字小文字を無視
	 * @param fromObj
	 *            コピー元のプロパティ
	 * @param toObj
	 *            コピー先のプロパティ
	 */
	@SuppressWarnings("unchecked")
	protected static void copyProperties(boolean caseInsensitive,
			Object fromObj, Object toObj) {
		if (fromObj == null) {
			return;
		}
		if (toObj == null) {
			return;
		}
		SimpleBeanWrapper fromWrapper = SimpleBeanUtils.getInstance(fromObj
				.getClass());
		SimpleBeanWrapper toWrapper = SimpleBeanUtils.getInstance(toObj
				.getClass());
		Map<String, Object> map;
		if (fromObj instanceof Map) {
			map = (Map<String, Object>) fromObj;
		} else {
			map = fromWrapper.toMap(fromObj, toWrapper, caseInsensitive);
		}
		for (Map.Entry<String, Object> entry : map.entrySet()) {
			String propertyName = entry.getKey();
			Object child = entry.getValue();
			if (child == null) {
				toWrapper.setValue(caseInsensitive, toObj, propertyName, null);
			} else if (child instanceof Iterable<?>) {
				Class<?> toChildClass = toWrapper.getPropertyClass(
						caseInsensitive, propertyName);
				if (toChildClass == null) {
					// 設定先のプロパティがない場合は無視
					continue;
				} else if (toChildClass == Object.class) {
					// 設定先がObject型の場合はそのまま設定
					toWrapper.setValue(caseInsensitive, toObj, propertyName,
							cloneValue(child));
					continue;
				} else if (!(Collection.class.isAssignableFrom(toChildClass))) {
					continue;
				}
				// 設定先のコレクションのジェネリック型を取得する
				Type toType = toWrapper.getPropertyGenericType(caseInsensitive,
						propertyName);
				if ((toType == String.class)
						&& (toWrapper.hasSetter(caseInsensitive, propertyName,
								child.getClass()))) {
					toWrapper.setValue(caseInsensitive, toObj, propertyName,
							cloneValue(child));
					continue;
				}
				Class<?> toGenericClass = getGenericClass(toType, 0);
				Collection<?> c = convertCollection(caseInsensitive,
						toChildClass, toGenericClass, entry);
				toWrapper.setValue(caseInsensitive, toObj, propertyName, c);
			} else if (toWrapper.hasSetter(caseInsensitive, propertyName,
					child.getClass())) {
				toWrapper.setValue(caseInsensitive, toObj, propertyName,
						cloneValue(child));
			} else {
				toWrapper.setValue(caseInsensitive, toObj, propertyName,
						cloneValue(child));
			}
		}
	}

	private static Object cloneValue(Object obj) {
		if (obj == null) {
			return null;
		}
		Class<?> clazz = obj.getClass();
		if (obj instanceof String) {
			return obj;
		} else if (obj instanceof Number) {
			return obj;
		} else if (clazz.isEnum()) {
			return obj;
		} else if (obj instanceof ArrayList) {
			return ((ArrayList<?>) obj).clone();
		} else if (obj instanceof Map) {
			return CommonUtils.cloneMap((Map<?, ?>) obj);
		} else if (obj instanceof HashSet) {
			return ((HashSet<?>) obj).clone();
		} else if (getConverters().isConvertable(clazz)) {
			return getConverters().copy(obj);
		} else if (clazz.isArray()) {
			int length = Array.getLength(obj);
			Object clone = Array.newInstance(clazz.getComponentType(), length);
			for (int i = 0; i < length; i++) {
				Object element = Array.get(obj, i);
				Array.set(clone, i, cloneValue(element));
			}
			return clone;
		} else if (doesNotCloneClasses.contains(clazz)) {
			return obj;
		} else if (obj instanceof Cloneable) {
			try {
				Method method = clazz.getMethod("clone");
				return method.invoke(obj);
			} catch (NoSuchMethodException e) {
				doesNotCloneClasses.add(clazz);
				return obj;
			} catch (SecurityException e) {
				throw new RuntimeException(e);
			} catch (IllegalAccessException e) {
				throw new RuntimeException(e);
			} catch (IllegalArgumentException e) {
				throw new RuntimeException(e);
			} catch (InvocationTargetException e) {
				throw throwInvocationTargetException(e);
			}
		} else {
			return obj;
		}
	}

	private static Set<Class<?>> doesNotCloneClasses=CommonUtils.set();
	
	protected static RuntimeException throwInvocationTargetException(
			InvocationTargetException e) {
		if (e.getCause() instanceof RuntimeException) {
			throw (RuntimeException) e.getCause();
		}
		throw new RuntimeException(e);
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	private static Collection<?> convertCollection(boolean caseInsensitive,
			Class<?> toChildClass, Class<?> toGenericClass,
			Map.Entry<String, Object> entry) {
		Collection c = getNewCollection(toChildClass);
		for (Object o : (Iterable<?>) entry.getValue()) {
			if (toGenericClass != null) {
				if (getConverters().isConvertable(toGenericClass)) {
					c.add(getConverters().convertObject(o, toGenericClass));
				} else {
					c.add(SimpleBeanUtils.convert(caseInsensitive, o,
							toGenericClass));
				}
			} else {
				Object newObj = SimpleBeanUtils.newInstance(o.getClass());
				SimpleBeanUtils.copyProperties(caseInsensitive, o, newObj);
				c.add(newObj);
			}
		}
		return c;
	}

	/**
	 * クラスからコレクションを生成します
	 * 
	 * @param clazz
	 */
	@SuppressWarnings("rawtypes")
	private static Collection getNewCollection(Class<?> clazz) {
		return (Collection) newInstance(clazz);
	}

	/**
	 * クラスからマップを生成します
	 * 
	 * @param clazz
	 */
	@SuppressWarnings({ "rawtypes", "unused" })
	private static Map getNewMap(Class<?> clazz) {
		return (Map) newInstance(clazz);
	}

	private static Class<?> getGenericClass(Type type, int no) {
		if (type instanceof ParameterizedType) {
			ParameterizedType pt = (ParameterizedType) type;
			Type aType = pt.getActualTypeArguments()[no];
			Class<?> componentType = (Class<?>) aType;
			return componentType;
		}
		return null;
	}

	/**
	 * オブジェクトをマップに変換します
	 * 
	 * @param val
	 *            マップに変換するオブジェクト
	 * @return 変換後のマップ
	 */
	public static Map<String, Object> toMap(Object val) {
		if (val == null) {
			return null;
		}
		SimpleBeanWrapper utils = SimpleBeanUtils.getInstance(val.getClass());
		return utils.toMap(val);
	}

	/**
	 * 指定したクラスのインスタンスを生成します
	 * 
	 * @param clazz
	 *            クラス
	 * @param initargs
	 *            コンストラクタ引数
	 * @return 指定したクラスのインスタンス
	 */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public static <T> T newInstance(Class clazz, Object... initargs) {
		if (clazz.equals(Map.class)) {
			return (T) new LinkedHashMap();
		} else if (clazz.equals(Set.class)) {
			return (T) new LinkedHashSet();
		} else if (clazz.equals(List.class)) {
			return (T) new ArrayList();
		}
		return getInstance(clazz).newInstance(initargs);
	}

	/**
	 * Beanのプロパティの型を返します
	 * 
	 * @param clazz
	 *            Bean
	 * @param propertyName
	 *            プロパティ名
	 * @return プロパティの型
	 */
	public static Class<?> getPropertyClass(Class<?> clazz, String propertyName) {
		SimpleBeanWrapper wrapper = SimpleBeanUtils.getInstance(clazz);
		return wrapper.getPropertyClass(propertyName);
	}

	/**
	 * プロパティ名を指定してプロパティのジェネリック型を取得します
	 * 
	 * @param clazz
	 *            Bean
	 * @param propertyName
	 *            プロパティ名
	 * @return プロパティのジェネリック型
	 */
	public static Type getPropertyGenericType(Class<?> clazz,
			String propertyName) {
		SimpleBeanWrapper wrapper = SimpleBeanUtils.getInstance(clazz);
		return wrapper.getPropertyGenericType(propertyName);
	}

	/**
	 * Beanの全プロパティの型を返します
	 * 
	 * @param clazz
	 *            Bean
	 * @return プロパティ名、プロパティの型のマップ
	 */
	public static Map<String, Class<?>> getPropertyClassMap(Class<?> clazz) {
		Map<String, Class<?>> map = new LinkedHashMap<String, Class<?>>();
		SimpleBeanWrapper wrapper = SimpleBeanUtils.getInstance(clazz);
		for (String name : wrapper.getPropertyNames()) {
			map.put(name, wrapper.getPropertyClass(name));
		}
		return map;
	}

	/**
	 * Beanの全プロパティのアノテーションを返します
	 * 
	 * @param clazz
	 *            Bean
	 * @return プロパティ名、プロパティのアノテーションのマップ
	 */
	public static Map<String, Annotation[]> getPropertyAnnotationMap(Class<?> clazz) {
		SimpleBeanWrapper wrapper = SimpleBeanUtils.getInstance(clazz);
		return wrapper.getPropertyAnnotationMap();
	}

	/**
	 * Beanの全プロパティの指定したアノテーションを返します
	 * 
	 * @param clazz
	 *            Bean
	 * @param annotationClass
	 *            対象のannotation
	 * @return プロパティ名、プロパティのアノテーションのマップ
	 */
	@SuppressWarnings("unchecked")
	public static <T extends Annotation> Map<String, T> getPropertyAnnotation(Class<?> clazz, Class<T> annotationClass) {
		Map<String, Annotation[]> annotationMap=getPropertyAnnotationMap(clazz);
		Map<String, T> result=CommonUtils.map();
		for(Map.Entry<String, Annotation[]> entry:annotationMap.entrySet()){
			for(Annotation annotation:entry.getValue()){
				if (annotationClass.equals(annotation.annotationType())){
					result.put(entry.getKey(), (T)annotation);
					break;
				}
			}
		}
		return result;
	}

	/**
	 * Beanの全プロパティの指定した全アノテーションを返します
	 * 
	 * @param clazz
	 *            Bean
	 * @param annotationClass
	 *            対象のannotation
	 * @return プロパティ名、プロパティのアノテーションのマップ
	 */
	@SuppressWarnings("unchecked")
	public static <T extends Annotation> Map<String, List<T>> getPropertyAnnotations(Class<?> clazz, Class<T> annotationClass) {
		Map<String, Annotation[]> annotationMap=getPropertyAnnotationMap(clazz);
		Map<String, List<T>> result=CommonUtils.map();
		for(Map.Entry<String, Annotation[]> entry:annotationMap.entrySet()){
			List<T> annotations=CommonUtils.list();
			for(Annotation annotation:entry.getValue()){
				if (annotationClass.equals(annotation.annotationType())){
					annotations.add((T)annotation);
					break;
				}
			}
			if (!annotations.isEmpty()){
				result.put(entry.getKey(), annotations);
			}
		}
		return result;
	}

	/**
	 * 指定したアノテーションのマップを取得します。
	 * @param prop
	 * @param annotationClass
	 */
	@SuppressWarnings("unchecked")
	public static <T extends Annotation> Map<String, T> filterAnnotation(Map<String, Annotation[]> prop, Class<T> annotationClass){
		Map<String, T> map=CommonUtils.map();
		for(Map.Entry<String, Annotation[]> entry:prop.entrySet()){
			for(Annotation annotation:entry.getValue()){
				if (annotationClass.equals(annotation.annotationType())){
					map.put(entry.getKey(), (T)annotation);
					break;
				}
			}
		}
		return map;
	}
	
	/**
	 * Beanの全Getterのアノテーションを返します
	 * 
	 * @param clazz
	 *            Bean
	 * @return プロパティ名、Getterのアノテーションのマップ
	 */
	public static Map<String, Annotation[]> getGetterAnnotationMap(Class<?> clazz) {
		SimpleBeanWrapper wrapper = SimpleBeanUtils.getInstance(clazz);
		return wrapper.getGetterAnnotationMap();
	}

	/**
	 * Beanの全Setterのアノテーションを返します
	 * 
	 * @param clazz
	 *            Bean
	 * @return プロパティ名、Setterのアノテーションのマップ
	 */
	public static Map<String, Annotation[]> getSetterAnnotationMap(Class<?> clazz) {
		SimpleBeanWrapper wrapper = SimpleBeanUtils.getInstance(clazz);
		return wrapper.getSetterAnnotationMap();
	}

	/**
	 * Beanの全Fieldのアノテーションを返します
	 * 
	 * @param clazz
	 *            Bean
	 * @return プロパティ名、Fieldのアノテーションのマップ
	 */
	public static Map<String, Annotation[]> getFieldAnnotationMap(Class<?> clazz) {
		SimpleBeanWrapper wrapper = SimpleBeanUtils.getInstance(clazz);
		return wrapper.getFieldAnnotationMap();
	}

	
	/**
	 * Beanの全プロパティのジェネリック型を返します
	 * 
	 * @param clazz
	 *            Bean
	 * @return プロパティ名、プロパティのジェネリック型のマップ
	 */
	public static Map<String, Type> getPropertyGenericTypeMap(Class<?> clazz) {
		Map<String, Type> map = new LinkedHashMap<String, Type>();
		SimpleBeanWrapper wrapper = SimpleBeanUtils.getInstance(clazz);
		for (String name : wrapper.getPropertyNames()) {
			map.put(name, wrapper.getPropertyGenericType(name));
		}
		return map;
	}

}
