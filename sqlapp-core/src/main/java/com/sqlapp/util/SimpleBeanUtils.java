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
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

import com.sqlapp.data.converter.Converters;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Index;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.UniqueConstraint;

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
	public static SimpleBeanWrapper getInstance(final Class<?> clazz) {
		SimpleBeanWrapper instance = INSTANCE_CACHE.get(clazz);
		if (instance != null) {
			return instance;
		}
		instance = new SimpleBeanWrapper(clazz);
		final SimpleBeanWrapper org = INSTANCE_CACHE.putIfAbsent(clazz, instance);
		return org != null ? org : instance;
	}

	/**
	 * キャッシュからインスタンスを取得します
	 * 
	 * @param className
	 *            クラス名
	 * @return 指定したクラスに対応したラッパークラス
	 */
	public static SimpleBeanWrapper getInstance(final String className) {
		final Class<?> clazz = classForName(className);
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
	public static <T> T getValue(final Object obj, final String propertyName) {
		if (obj instanceof Map) {
			final Map<?,?> map=Map.class.cast(obj);
			if (map.containsKey(propertyName)) {
				return (T)map.get(propertyName);
			}
		}
		final SimpleBeanWrapper wrapper = SimpleBeanUtils.getInstance(obj.getClass());
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
	public static <T> T getValueCI(final Object obj, final String propertyName) {
		if (obj instanceof Map) {
			final Map<?,?> map=Map.class.cast(obj);
			if (map.containsKey(propertyName)) {
				return (T)map.get(propertyName);
			}else if (map.containsKey(propertyName.toLowerCase())) {
				return (T)map.get(propertyName.toLowerCase());
			}else if (map.containsKey(propertyName.toUpperCase())) {
				return (T)map.get(propertyName.toUpperCase());
			}
			for(final Map.Entry<?,?> entry:map.entrySet()) {
				final String key=entry.getKey().toString();
				if (key.equalsIgnoreCase(propertyName)) {
					return (T)entry.getValue();
				}
			}
		}
		final SimpleBeanWrapper wrapper = SimpleBeanUtils.getInstance(obj.getClass());
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
	public static boolean setValueCI(final Object obj, final String propertyName,
			final Object value) {
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
	public static boolean setValueCI(final Object obj, final String propertyName,
			final Object value, final boolean force) {
		final SimpleBeanWrapper wrapper = SimpleBeanUtils.getInstance(obj.getClass());
		final boolean bool= wrapper.setValueCI(obj, propertyName, value, force);
		if (bool) {
			return bool;
		}
		if (obj instanceof Map) {
			final Map<String,Object> map=Map.class.cast(obj);
			for(final Map.Entry<String,?> entry:map.entrySet()) {
				final String key=entry.getKey().toString();
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
	public static void setValuesCI(final Collection<?> list, final String propertyName,
			final Object value) {
		for (final Object obj : list) {
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
	public static void setValues(final Collection<?> list, final String propertyName,
			final Object value) {
		for (final Object obj : list) {
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
	public static boolean setValue(final Object obj, final String propertyName, final Object value) {
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
	public static boolean setValue(final Object obj, final String propertyName,
			final Object value, final boolean force) {
		final SimpleBeanWrapper wrapper = SimpleBeanUtils.getInstance(obj.getClass());
		final boolean bool= wrapper.setValue(obj, propertyName, value, force);
		if (bool) {
			return bool;
		}
		if (obj instanceof Map) {
			final Map map=Map.class.cast(obj);
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
	public static <T> T getField(final Object obj, final String fieldName) {
		final SimpleBeanWrapper wrapper = SimpleBeanUtils.getInstance(obj.getClass());
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
	public static <T> T getFieldCI(final Object obj, final String fieldName) {
		final SimpleBeanWrapper wrapper = SimpleBeanUtils.getInstance(obj.getClass());
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
	public static boolean setField(final Object obj, final String fieldName,
			final Object value) {
		final SimpleBeanWrapper wrapper = SimpleBeanUtils.getInstance(obj.getClass());
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
	public static boolean setFieldCI(final Object obj, final String fieldName,
			final Object value) {
		final SimpleBeanWrapper wrapper = SimpleBeanUtils.getInstance(obj.getClass());
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
	public static <T> T convert(final Object fromObj, final Class<T> toClazz) {
		return convert(false, fromObj, toClazz);
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	private static Map toMap(final Object fromObj, final Class toClazz) {
		final SimpleBeanWrapper toUtils = SimpleBeanUtils.getInstance(toClazz);
		final Map toObj = toUtils.newInstance();
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
	public static <T> T convertCI(final Object fromObj, final Class<T> toClazz) {
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
	protected static <T> T convert(final boolean caseInsensitive, final Object fromObj,
			final Class<T> toClazz) {
		if (fromObj == null) {
			return null;
		}
		if (Map.class.isAssignableFrom(toClazz)) {
			@SuppressWarnings("rawtypes")
			final
			Map toObj = toMap(fromObj, toClazz);
			return (T) toObj;
		} else {
			final SimpleBeanWrapper toUtils = SimpleBeanUtils.getInstance(toClazz);
			final Object toObj = toUtils.newInstance();
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
	public static <T> Set<T> convertSetCI(final Collection<?> fromSet,
			final Class<T> toClazz) {
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
	public static <T> Set<T> convertSet(final Collection<?> fromSet, final Class<T> toClazz) {
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
	protected static <T> Set<T> convertSet(final boolean caseInsensitive,
			final Collection<?> fromSet, final Class<T> toClazz) {
		if (fromSet == null) {
			return null;
		}
		if (fromSet.size() == 0) {
			return new LinkedHashSet<T>();
		}
		final LinkedHashSet<T> result = new LinkedHashSet<T>(fromSet.size());
		final SimpleBeanWrapper toUtils = SimpleBeanUtils.getInstance(toClazz);
		for (final Object obj : fromSet) {
			final T toObj = toUtils.newInstance();
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
	protected static <T> Set<T> convertSet(final boolean caseInsensitive,
			final Collection<?> fromCllection, final String propertyName) {
		if (fromCllection == null) {
			return null;
		}
		final Set<T> set = CommonUtils.linkedSet(fromCllection.size());
		if (fromCllection.size() == 0) {
			return set;
		}
		for (final Object obj : fromCllection) {
			final T value = getValueRecursive(caseInsensitive, obj, propertyName);
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
	public static <T> Set<T> convertSet(final Collection<?> fromCllection,
			final String propertyName) {
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
	public static <T> Set<T> convertSetCI(final Collection<?> fromCllection,
			final String propertyName) {
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
	public static <T> List<T> convertListCI(final List<?> fromList, final Class<T> toClazz) {
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
	public static <T> List<T> convertList(final List<?> fromList, final Class<T> toClazz) {
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
	protected static <T> List<T> convertList(final boolean caseInsensitive,
			final List<?> fromList, final Class<T> toClazz) {
		if (fromList == null) {
			return null;
		}
		if (fromList.size() == 0) {
			return new ArrayList<T>();
		}
		final List<T> result = new ArrayList<T>(fromList.size());
		final SimpleBeanWrapper toUtils = SimpleBeanUtils.getInstance(toClazz);
		for (int i = 0; i < fromList.size(); i++) {
			final T toObj = toUtils.newInstance();
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
	protected static <T> List<T> convertList(final boolean caseInsensitive,
			final Collection<?> fromCllection, final String propertyName) {
		if (fromCllection == null) {
			return null;
		}
		final List<T> list = CommonUtils.list(fromCllection.size());
		if (fromCllection.size() == 0) {
			return list;
		}
		for (final Object obj : fromCllection) {
			final T value = getValueRecursive(caseInsensitive, obj, propertyName);
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
	public static <T> List<T> convertList(final Collection<?> fromCllection,
			final String propertyName) {
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
	public static <T> List<T> convertListCI(final Collection<?> fromCllection,
			final String propertyName) {
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
	protected static <S, T> Map<S, T> convertMap(final boolean caseInsensitive,
			final Collection<T> fromCllection, final String propertyName) {
		if (fromCllection == null) {
			return null;
		}
		final Map<S, T> map = new LinkedHashMap<S, T>();
		if (fromCllection.size() == 0) {
			return map;
		}
		for (final Object obj : fromCllection) {
			final Object key = getValueRecursive(caseInsensitive, obj, propertyName);
			map.put((S) key, (T) obj);
		}
		return map;
	}

	private static <T> T getValueRecursive(final boolean caseInsensitive, final Object obj,
			final String propertyName) {
		if (obj == null) {
			return null;
		}
		final SimpleBeanWrapper utils = SimpleBeanUtils.getInstance(obj.getClass());
		final String[] args = propertyName.split("\\.");
		if (args.length == 1) {
			return utils.getValue(caseInsensitive, obj, propertyName);
		}
		final Object value = utils.getValue(caseInsensitive, obj, args[0]);
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
	public static <S, T> Map<S, T> convertMap(final Collection<T> fromCllection,
			final String propertyName) {
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
	public static <S, T> Map<S, T> convertMapCI(final Collection<T> fromCllection,
			final String propertyName) {
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
	public static void copyProperties(final Object fromObj, final Object toObj) {
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
	public static void copyPropertiesCI(final Object fromObj, final Object toObj) {
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
	protected static void copyProperties(final boolean caseInsensitive,
			final Object fromObj, final Object toObj) {
		if (fromObj == null) {
			return;
		}
		if (toObj == null) {
			return;
		}
		final SimpleBeanWrapper fromWrapper = SimpleBeanUtils.getInstance(fromObj
				.getClass());
		final SimpleBeanWrapper toWrapper = SimpleBeanUtils.getInstance(toObj
				.getClass());
		Map<String, Object> map;
		if (fromObj instanceof Map) {
			map = (Map<String, Object>) fromObj;
		} else {
			map = fromWrapper.toMap(fromObj, toWrapper, caseInsensitive);
		}
		for (final Map.Entry<String, Object> entry : map.entrySet()) {
			final String propertyName = entry.getKey();
			final Object child = entry.getValue();
			if (child == null) {
				toWrapper.setValue(caseInsensitive, toObj, propertyName, null);
			} else if (child instanceof Iterable<?>) {
				final Class<?> toChildClass = toWrapper.getPropertyClass(
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
				final Type toType = toWrapper.getPropertyGenericType(caseInsensitive,
						propertyName);
				if ((toType == String.class)
						&& (toWrapper.hasSetter(caseInsensitive, propertyName,
								child.getClass()))) {
					toWrapper.setValue(caseInsensitive, toObj, propertyName,
							cloneValue(child));
					continue;
				}
				final Class<?> toGenericClass = getGenericClass(toType, 0);
				final Collection<?> c = convertCollection(caseInsensitive,
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

	private static Object cloneValue(final Object obj) {
		if (obj == null) {
			return null;
		}
		final Class<?> clazz = obj.getClass();
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
			final int length = Array.getLength(obj);
			final Object clone = Array.newInstance(clazz.getComponentType(), length);
			for (int i = 0; i < length; i++) {
				final Object element = Array.get(obj, i);
				Array.set(clone, i, cloneValue(element));
			}
			return clone;
		} else if (doesNotCloneClasses.contains(clazz)) {
			return obj;
		} else if (obj instanceof Cloneable) {
			try {
				final Method method = clazz.getMethod("clone");
				return method.invoke(obj);
			} catch (final NoSuchMethodException e) {
				doesNotCloneClasses.add(clazz);
				return obj;
			} catch (final SecurityException e) {
				throw new RuntimeException(e);
			} catch (final IllegalAccessException e) {
				throw new RuntimeException(e);
			} catch (final IllegalArgumentException e) {
				throw new RuntimeException(e);
			} catch (final InvocationTargetException e) {
				throw throwInvocationTargetException(e);
			}
		} else {
			return obj;
		}
	}

	private static Set<Class<?>> doesNotCloneClasses=CommonUtils.set();
	
	protected static RuntimeException throwInvocationTargetException(
			final InvocationTargetException e) {
		if (e.getCause() instanceof RuntimeException) {
			throw (RuntimeException) e.getCause();
		}
		throw new RuntimeException(e);
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	private static Collection<?> convertCollection(final boolean caseInsensitive,
			final Class<?> toChildClass, final Class<?> toGenericClass,
			final Map.Entry<String, Object> entry) {
		final Collection c = getNewCollection(toChildClass);
		for (final Object o : (Iterable<?>) entry.getValue()) {
			if (toGenericClass != null) {
				if (getConverters().isConvertable(toGenericClass)) {
					c.add(getConverters().convertObject(o, toGenericClass));
				} else {
					c.add(SimpleBeanUtils.convert(caseInsensitive, o,
							toGenericClass));
				}
			} else {
				final Object newObj = SimpleBeanUtils.newInstance(o.getClass());
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
	private static Collection getNewCollection(final Class<?> clazz) {
		return (Collection) newInstance(clazz);
	}

	/**
	 * クラスからマップを生成します
	 * 
	 * @param clazz
	 */
	@SuppressWarnings({ "rawtypes", "unused" })
	private static Map getNewMap(final Class<?> clazz) {
		return (Map) newInstance(clazz);
	}

	private static Class<?> getGenericClass(final Type type, final int no) {
		if (type instanceof ParameterizedType) {
			final ParameterizedType pt = (ParameterizedType) type;
			final Type aType = pt.getActualTypeArguments()[no];
			final Class<?> componentType = (Class<?>) aType;
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
	public static Map<String, Object> toMap(final Object val) {
		if (val == null) {
			return null;
		}
		final SimpleBeanWrapper utils = SimpleBeanUtils.getInstance(val.getClass());
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
	public static <T> T newInstance(final Class clazz, final Object... initargs) {
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
	public static Class<?> getPropertyClass(final Class<?> clazz, final String propertyName) {
		final SimpleBeanWrapper wrapper = SimpleBeanUtils.getInstance(clazz);
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
	public static Type getPropertyGenericType(final Class<?> clazz,
			final String propertyName) {
		final SimpleBeanWrapper wrapper = SimpleBeanUtils.getInstance(clazz);
		return wrapper.getPropertyGenericType(propertyName);
	}

	/**
	 * Beanの全プロパティの型を返します
	 * 
	 * @param clazz
	 *            Bean
	 * @return プロパティ名、プロパティの型のマップ
	 */
	public static Map<String, Class<?>> getPropertyClassMap(final Class<?> clazz) {
		final Map<String, Class<?>> map = new LinkedHashMap<String, Class<?>>();
		final SimpleBeanWrapper wrapper = SimpleBeanUtils.getInstance(clazz);
		for (final String name : wrapper.getPropertyNames()) {
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
	public static Map<String, Annotation[]> getPropertyAnnotationMap(final Class<?> clazz) {
		final SimpleBeanWrapper wrapper = SimpleBeanUtils.getInstance(clazz);
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
	public static <T extends Annotation> Map<String, T> getPropertyAnnotation(final Class<?> clazz, final Class<T> annotationClass) {
		final Map<String, Annotation[]> annotationMap=getPropertyAnnotationMap(clazz);
		final Map<String, T> result=CommonUtils.map();
		for(final Map.Entry<String, Annotation[]> entry:annotationMap.entrySet()){
			for(final Annotation annotation:entry.getValue()){
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
	public static <T extends Annotation> Map<String, List<T>> getPropertyAnnotations(final Class<?> clazz, final Class<T> annotationClass) {
		final Map<String, Annotation[]> annotationMap=getPropertyAnnotationMap(clazz);
		final Map<String, List<T>> result=CommonUtils.map();
		for(final Map.Entry<String, Annotation[]> entry:annotationMap.entrySet()){
			final List<T> annotations=CommonUtils.list();
			for(final Annotation annotation:entry.getValue()){
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
	public static <T extends Annotation> Map<String, T> filterAnnotation(final Map<String, Annotation[]> prop, final Class<T> annotationClass){
		final Map<String, T> map=CommonUtils.map();
		for(final Map.Entry<String, Annotation[]> entry:prop.entrySet()){
			for(final Annotation annotation:entry.getValue()){
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
	public static Map<String, Annotation[]> getGetterAnnotationMap(final Class<?> clazz) {
		final SimpleBeanWrapper wrapper = SimpleBeanUtils.getInstance(clazz);
		return wrapper.getGetterAnnotationMap();
	}

	/**
	 * Beanの全Setterのアノテーションを返します
	 * 
	 * @param clazz
	 *            Bean
	 * @return プロパティ名、Setterのアノテーションのマップ
	 */
	public static Map<String, Annotation[]> getSetterAnnotationMap(final Class<?> clazz) {
		final SimpleBeanWrapper wrapper = SimpleBeanUtils.getInstance(clazz);
		return wrapper.getSetterAnnotationMap();
	}

	/**
	 * Beanの全Fieldのアノテーションを返します
	 * 
	 * @param clazz
	 *            Bean
	 * @return プロパティ名、Fieldのアノテーションのマップ
	 */
	public static Map<String, Annotation[]> getFieldAnnotationMap(final Class<?> clazz) {
		final SimpleBeanWrapper wrapper = SimpleBeanUtils.getInstance(clazz);
		return wrapper.getFieldAnnotationMap();
	}

	/**
	 * Beanの全プロパティのジェネリック型を返します
	 * 
	 * @param clazz
	 *            Bean
	 * @return プロパティ名、プロパティのジェネリック型のマップ
	 */
	public static Map<String, Type> getPropertyGenericTypeMap(final Class<?> clazz) {
		final Map<String, Type> map = new LinkedHashMap<String, Type>();
		final SimpleBeanWrapper wrapper = SimpleBeanUtils.getInstance(clazz);
		for (final String name : wrapper.getPropertyNames()) {
			map.put(name, wrapper.getPropertyGenericType(name));
		}
		return map;
	}

	/**
	 * プロパティ名のセットを取得します
	 * 
	 * @return プロパティ名のセット
	 */
	public static Set<String> getPropertyNames(final Class<?> clazz) {
		final SimpleBeanWrapper wrapper = SimpleBeanUtils.getInstance(clazz);
		return wrapper.getPropertyNames();
	}
	
	/**
	 * クラスをTableに変換します
	 * @param clazz 変換元のクラス
	 * @param primaryPredicate primary keyを示すpredicate
	 * @param toSnakecase テーブル名、カラム名をスネークケースに変換するか?
	 * @return　Table
	 */
	public static Table toTable(final Class<?> clazz, final Predicate<Column> primaryPredicate, final boolean toSnakecase) {
		if (toSnakecase) {
			return toTable(clazz, primaryPredicate, name->StringUtils.camelToSnake(name));
		}
		return toTable(clazz, primaryPredicate, name->name);
	}

	/**
	 * クラスをTableに変換します
	 * @param clazz 変換元のクラス
	 * @param primaryPredicate primary keyを示すpredicate
	 * @param nameConverter クラス名、プロパティ名の変換関数
	 * @return　Table
	 */
	public static Table toTable(final Class<?> clazz, final Predicate<Column> primaryPredicate, final Function<String,String> nameConverter) {
		final Annotation[] classAnnotations=clazz.getAnnotations();
		final Map<String, Annotation[]> annotationMap = SimpleBeanUtils.getPropertyAnnotationMap(clazz);
		final Table table=new Table();
		final Optional<javax.persistence.Table> optionalTable=getAnnotation(javax.persistence.Table.class, classAnnotations);
		javax.persistence.Index[] jpaIndexes=null;
		javax.persistence.UniqueConstraint[] jpaUcs=null;
		final Map<String,Column> nameColumnMap=CommonUtils.linkedMap();
		if (optionalTable.isPresent()) {
			final javax.persistence.Table pTable=optionalTable.get();
			table.setName(pTable.name());
			table.setSchemaName(pTable.name());
			table.setCatalogName(pTable.catalog());
			jpaIndexes=pTable.indexes();
			jpaUcs=pTable.uniqueConstraints();
		} else {
			table.setName(nameConverter.apply(clazz.getSimpleName()));
		}
		final List<Column> primaries=CommonUtils.list();
		final List<Column> uniqueColumns=CommonUtils.list();
		for(final String name:SimpleBeanUtils.getPropertyNames(clazz)) {
			final Column column=table.newColumn();
			final Class<?> propClass=SimpleBeanUtils.getPropertyClass(clazz, name);
			final DataType dataType=DataType.valueOf(propClass);
			column.setDataType(dataType);
			final Annotation[] annotations=annotationMap.get(name);
			column.setName(nameConverter.apply(name));
			final Optional<NotNull> optionalNotNull=getAnnotation(NotNull.class, annotations);
			if (propClass.isPrimitive()) {
				column.setNotNull(true);
			} else if (optionalNotNull.isPresent()) {
				column.setNotNull(true);
			} else {
				final Optional<NotEmpty> optionalNotEmpty=getAnnotation(NotEmpty.class, annotations);
				if (optionalNotEmpty.isPresent()) {
					column.setNotNull(true);
					column.setDefaultValue("");
				} else {
					final Optional<NotBlank> optionalNotBlank=getAnnotation(NotBlank.class, annotations);
					column.setNotNull(optionalNotBlank.isPresent());
				}
			}
			final Optional<Size> optionalSize=getAnnotation(Size.class, annotations);
			if (optionalSize.isPresent()) {
				final int max=optionalSize.get().max();
				if (byte[].class.equals(propClass)) {
				} else if (!propClass.isArray()) {
					column.setLength(max);
				} else {
					column.setArrayDimension(1);
					column.setArrayDimensionUpperBound(max);
				}
			}
			final Optional<javax.persistence.Column> optionalColumn=getAnnotation(javax.persistence.Column.class, annotations);
			if (optionalColumn.isPresent()) {
				final javax.persistence.Column jpaColumn=optionalColumn.get();
				if (!CommonUtils.isEmpty(jpaColumn.name())){
					column.setName(jpaColumn.name());
				}
				column.setNotNull(!jpaColumn.nullable()||propClass.isPrimitive());
				if (column.getDataType().isCharacter()) {
					column.setLength(jpaColumn.length());
				} else {
					if (column.getDataType().isNumeric()) {
						column.setLength(jpaColumn.precision());
					}
				}
				if (column.getDataType().isFixedScale()) {
					column.setScale(jpaColumn.scale());
				}
				if (jpaColumn.unique()){
					uniqueColumns.add(column);
				}
			}
			final Optional<GeneratedValue> optionalGeneratedValue=getAnnotation(GeneratedValue.class, annotations);
			if (optionalGeneratedValue.isPresent()) {
				final GeneratedValue generatedValue=optionalGeneratedValue.get();
				if (GenerationType.IDENTITY == generatedValue.strategy()) {
					column.setIdentity(true);
				}
				if (GenerationType.SEQUENCE == generatedValue.strategy()) {
					column.setSchemaName(table.getName()+"_"+column.getName()+"_seq");
				}
			}
			if (primaryPredicate.test(column)) {
				primaries.add(column);
			}
			nameColumnMap.put(column.getName(), column);
			nameColumnMap.put(name, column);
			table.getColumns().add(column);
		}
		table.setPrimaryKey(primaries.toArray(new Column[0]));
		int ucNo=1;
		int indexNo=1;
		for(final Column uniqueColumn:uniqueColumns) {
			final UniqueConstraint uc=new UniqueConstraint("UK_"+table.getName()+(ucNo++), uniqueColumn);
			table.getConstraints().add(uc);
		}
		if (jpaIndexes!=null) {
			for(final javax.persistence.Index jpaIndex:jpaIndexes) {
				if (CommonUtils.isEmpty(jpaIndex.columnList())) {
					continue;
				}
				final Column[] columns=getColumns(nameColumnMap, StringUtils.trim(jpaIndex.columnList().split(",")));
				if (CommonUtils.isEmpty(columns)) {
					continue;
				}
				final Index index=new Index("UK_"+table.getName()+(indexNo++), columns);
				index.setUnique(jpaIndex.unique());
				table.getIndexes().add(index);
			}
		}
		if (jpaIndexes!=null) {
			for(final javax.persistence.UniqueConstraint jpaUc:jpaUcs) {
				if (CommonUtils.isEmpty(jpaUc.columnNames())) {
					continue;
				}
				final Column[] columns=getColumns(nameColumnMap, StringUtils.trim(jpaUc.columnNames()));
				if (CommonUtils.isEmpty(columns)) {
					continue;
				}
				final UniqueConstraint uc=new UniqueConstraint("UK_"+table.getName()+(ucNo++), columns);
				table.getConstraints().add(uc);
			}
		}
		return table;
	}
	
	private static Column[] getColumns(final Map<String,Column> nameColumnMap, final String...names) {
		final Column[] columns=new Column[names.length];
		int i=0;
		for(final String name:names) {
			final Column column=nameColumnMap.get(name);
			if (column==null) {
				return null;
			}
			columns[i]=column;
			i++;
		}
		return columns;
	}

	private static <T extends Annotation> Optional<T> getAnnotation(final Class<T> clazz, final Annotation[] args){
		if (args==null) {
			return Optional.empty();
		}
		for(final Annotation arg:args) {
			if (clazz.isInstance(arg)) {
				return Optional.of(clazz.cast(arg));
			}
		}
		return Optional.empty();
	}
}
