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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Pattern;

import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.DbInfo;
import com.sqlapp.data.schemas.ReferenceColumn;

/**
 * 汎用ユーティリティ
 * 
 * @author SATOH
 * 
 */
public final class CommonUtils {
	private CommonUtils() {
	}

	/**
	 * 1KBのサイズ
	 */
	public static final int LEN_1KB = pow(2, 10);
	/**
	 * 64KBのサイズ
	 */
	public static final int LEN_64KB = pow(2, 16);
	/**
	 * 1MBのサイズ
	 */
	public static final int LEN_1MB = pow(2, 20);
	/**
	 * 16MBのサイズ
	 */
	public static final int LEN_16MB = pow(2, 24);
	/**
	 * 1GBのサイズ
	 */
	public static final int LEN_1GB = pow(2, 30);
	/**
	 * 2GBのサイズ
	 */
	public static final long LEN_2GB = pow(2, 31);
	/**
	 * 4GBのサイズ
	 */
	public static final long LEN_4GB = pow(2L, 32);
	/**
	 * shortのバイト数
	 */
	public static final int SHORT_SIZE = 2;
	/**
	 * shortのバイト数
	 */
	public static final int INT16_SIZE = 2;
	/**
	 * intのバイト数
	 */
	public static final int INT_SIZE = 4;
	/**
	 * intのバイト数
	 */
	public static final int INT32_SIZE = 4;
	/**
	 * longのバイト数
	 */
	public static final int LONG_SIZE = 8;
	/**
	 * longのバイト数
	 */
	public static final int INT64_SIZE = 8;
	/**
	 * int128のバイト数
	 */
	public static final int INT128_SIZE = 16;

	private static final Map<Class<?>, Class<?>> PRIMITIVE_WRAPPER_CLASS_MAP = map(16);
	/**
	 * 環境変数の一覧
	 */
	private static Map<String, String> ENV_MAP = null;

	static {
		setPrimitiveWrapperMapping(Boolean.TYPE, Boolean.class);
		setPrimitiveWrapperMapping(Byte.TYPE, Byte.class);
		setPrimitiveWrapperMapping(Short.TYPE, Short.class);
		setPrimitiveWrapperMapping(Integer.TYPE, Integer.class);
		setPrimitiveWrapperMapping(Long.TYPE, Long.class);
		setPrimitiveWrapperMapping(Float.TYPE, Float.class);
		setPrimitiveWrapperMapping(Double.TYPE, Double.class);
		setPrimitiveWrapperMapping(Character.TYPE, Character.class);
		setPrimitiveWrapperMapping(boolean[].class, Boolean[].class);
		setPrimitiveWrapperMapping(byte[].class, Byte[].class);
		setPrimitiveWrapperMapping(short[].class, Short[].class);
		setPrimitiveWrapperMapping(int[].class, Integer[].class);
		setPrimitiveWrapperMapping(long[].class, Long[].class);
		setPrimitiveWrapperMapping(float[].class, Float[].class);
		setPrimitiveWrapperMapping(double[].class, Double[].class);
		setPrimitiveWrapperMapping(char[].class, Character[].class);
		initializeEnv();
	}

	private static void setPrimitiveWrapperMapping(final Class<?> clazz1,
			final Class<?> clazz2) {
		PRIMITIVE_WRAPPER_CLASS_MAP.put(clazz1, clazz2);
		PRIMITIVE_WRAPPER_CLASS_MAP.put(clazz2, clazz1);
	}

	/**
	 * システム環境変数を初期化します
	 */
	private static void initializeEnv() {
		final Map<String, String> map = new HashMap<String, String>();
		final Properties prop = System.getProperties();
		for (final Object obj : prop.keySet()) {
			final String key = (String) obj;
			map.put(key, prop.getProperty(key));
		}
		map.putAll(System.getenv());
		ENV_MAP = Collections.unmodifiableMap(map);
	}

	/**
	 * システム環境変数を取得します
	 * 
	 * @return システム環境変数
	 */
	public static final Map<String, String> getSystemEnv() {
		return ENV_MAP;
	}

	/**
	 * キャストメソッド
	 * 
	 * @param <T>
	 * @param obj
	 */
	@SuppressWarnings("unchecked")
	public static <T> T cast(final Object obj) {
		return (T) obj;
	}

	/**
	 * 引数で与えられた要素を持つリストを作成します
	 * 
	 * @param <T>
	 * @param args
	 */
	@SafeVarargs
	public static <T> List<T> list(final T... args) {
		final int size = args.length;
		final List<T> result = list(size);
		for (int i = 0; i < size; i++) {
			result.add(args[i]);
		}
		return result;
	}

	/**
	 * 引数で与えられたコレクションの要素を持つリストを作成します
	 * 
	 * @param <T>
	 * @param c
	 */
	public static <T> List<T> list(final Collection<T> c) {
		final List<T> result = list(c.size());
		result.addAll(c);
		return result;
	}

	/**
	 * イテレータをリストに変換します。
	 * 
	 * @param itr
	 */
	public static <T> List<T> list(final Iterable<T> itr) {
		final List<T> list = new ArrayList<T>();
		for (final T obj : itr) {
			list.add(obj);
		}
		return list;
	}

	/**
	 * イテレータをセットに変換します。
	 * 
	 * @param itr
	 */
	public static <T> Set<T> set(final Iterable<T> itr) {
		final Set<T> set = new HashSet<T>();
		for (final T obj : itr) {
			set.add(obj);
		}
		return set;
	}

	/**
	 * イテレータをセットに変換します。
	 * 
	 * @param itr
	 */
	public static <T> Set<T> linkedSet(final Iterable<T> itr) {
		final Set<T> set = new LinkedHashSet<T>();
		for (final T obj : itr) {
			set.add(obj);
		}
		return set;
	}

	/**
	 * リスト作成メソッド
	 * 
	 * @param <T>
	 * @param arg
	 */
	public static <T> List<T> list(final T arg) {
		final List<T> result = list();
		result.add(arg);
		return result;
	}

	/**
	 * リスト作成メソッド
	 * 
	 * @param <T>
	 * @param arg1
	 * @param arg2
	 */
	public static <T> List<T> list(final T arg1, final T arg2) {
		final List<T> result = list();
		result.add(arg1);
		result.add(arg2);
		return result;
	}

	/**
	 * Listへ値を追加します
	 * 
	 * @param <T>
	 * @param list
	 * @param args
	 */
	@SafeVarargs
	public static <T> void add(final List<T> list, final T... args) {
		final int size = args.length;
		for (int i = 0; i < size; i++) {
			list.add(args[i]);
		}
	}

	/**
	 * Listへ値を追加します
	 * 
	 * @param <T>
	 * @param list
	 * @param arg
	 */
	public static <T> void add(final List<T> list, final T arg) {
		list.add(arg);
	}

	/**
	 * リスト作成メソッド
	 * 
	 * @param <T>
	 * @param args
	 */
	public static <T> List<T> list(final List<T> args) {
		final int size = args.size();
		final List<T> result = list(size);
		for (int i = 0; i < size; i++) {
			result.add(args.get(i));
		}
		return result;
	}

	/**
	 * リスト作成メソッド
	 * 
	 * @param <T>
	 */
	public static <T> List<T> list() {
		return new ArrayList<T>();
	}

	/**
	 * リスト作成メソッド
	 * 
	 * @param <T>
	 * @param capacity
	 *            リストの作成サイズ
	 */
	public static <T> List<T> list(final int capacity) {
		return new ArrayList<T>(capacity);
	}

	/**
	 * マップ作成メソッド
	 * 
	 * @param <T>
	 */
	public static <S, T> Map<S, T> map() {
		return new HashMap<S, T>();
	}

	/**
	 * ConcurrentHashMap作成メソッド
	 * 
	 * @param <T>
	 */
	public static <S, T> ConcurrentMap<S, T> concurrentMap() {
		return new ConcurrentHashMap<S, T>();
	}

	/**
	 * Enumマップ作成メソッド
	 * 
	 * @param <T>
	 * @param clazz
	 *            Enum型
	 */
	public static <S extends Enum<S>, T> Map<S, T> enumMap(final Class<S> clazz) {
		return new EnumMap<S, T>(clazz);
	}

	/**
	 * TreeMap作成メソッド
	 * 
	 * @param <T>
	 */
	public static <S, T> SortedMap<S, T> treeMap() {
		return new TreeMap<S, T>();
	}

	/**
	 * TreeSet作成メソッド
	 * 
	 */
	public static <S> Set<S> treeSet() {
		return new TreeSet<S>();
	}
	
	/**
	 * TreeSet作成メソッド
	 * 
	 * @param <T>
	 */
	@SafeVarargs
	public static <T> Set<T> treeSet(final T... args) {
		final Set<T> result = new TreeSet<T>();
		for (final T arg : args) {
			result.add(arg);
		}
		return result;
	}


	/**
	 * マップ作成メソッド
	 * 
	 * @param <T>
	 * @param capacity
	 *            初期容量
	 */
	public static <S, T> Map<S, T> map(final int capacity) {
		return new HashMap<S, T>(capacity);
	}

	/**
	 * マップ作成メソッド
	 * 
	 */
	public static <S, T> Map<S, T> map(final Map<S,T> map) {
		return new HashMap<S, T>(map);
	}

	/**
	 * マップ作成メソッド
	 * 
	 * @param <S>
	 * @param <T>
	 * @param capacity
	 *            初期容量
	 * @param loadFactor
	 */
	public static <S, T> Map<S, T> map(final int capacity,
			final float loadFactor) {
		return new HashMap<S, T>(capacity, loadFactor);
	}

	/**
	 * ConcurrentHashMap作成メソッド
	 * 
	 * @param capacity
	 *            初期容量
	 * @param <T>
	 */
	public static <S, T> ConcurrentMap<S, T> concurrentMap(final int capacity) {
		return new ConcurrentHashMap<S, T>(capacity);
	}

	/**
	 * LinkedHashMapを作成します
	 * 
	 * @param <S>
	 * @param <T>
	 */
	public static <S, T> Map<S, T> linkedMap() {
		return new LinkedHashMap<S, T>();
	}

	/**
	 * LinkedHashMapを作成します
	 * 
	 * @param map
	 * @param <S>
	 * @param <T>
	 */
	public static <S, T> Map<S, T> linkedMap(final Map<S, T> map) {
		return new LinkedHashMap<S, T>(map);
	}

	/**
	 * LinkedHashMap作成メソッド
	 * 
	 * @param <T>
	 * @param capacity
	 *            リストの作成サイズ
	 */
	public static <S, T> Map<S, T> linkedMap(final int capacity) {
		return new LinkedHashMap<S, T>(capacity);
	}

	/**
	 * LinkedHashMap作成メソッド
	 * 
	 * @param <S>
	 * @param <T>
	 * @param capacity
	 * @param loadFactor
	 */
	public static <S, T> Map<S, T> linkedMap(final int capacity,
			final float loadFactor) {
		return new LinkedHashMap<S, T>(capacity, loadFactor);
	}

	/**
	 * ダブルキーマップを作成します
	 * 
	 * @param <S>
	 * @param <T>
	 * @param <U>
	 */
	public static <S, T, U> DoubleKeyMap<S, T, U> doubleKeyMap() {
		return new DoubleKeyMap<S, T, U>();
	}

	/**
	 * トリプルキーマップを作成します
	 * 
	 * @param <S>
	 * @param <T>
	 * @param <U>
	 * @param <V>
	 */
	public static <S, T, U, V> TripleKeyMap<S, T, U, V> tripleKeyMap() {
		return new TripleKeyMap<S, T, U, V>();
	}

	/**
	 * クァッドキーマップを作成します
	 * 
	 * @param <S>
	 * @param <T>
	 * @param <U>
	 * @param <V>
	 */
	public static <S, T, U, V, W> QuadKeyMap<S, T, U, V, W> quadKeyMap() {
		return new QuadKeyMap<S, T, U, V, W>();
	}

	/**
	 * 大文字格納マップ作成メソッド
	 * 
	 * @param <T>
	 */
	public static <T> Map<String, T> upperMap() {
		return new UpperMap<T>();
	}

	/**
	 * 大文字格納マップ作成メソッド
	 * 
	 * @param <T>
	 * @param capacity
	 *            リストの作成サイズ
	 */
	public static <T> Map<String, T> upperMap(final int capacity) {
		return new UpperMap<T>(capacity);
	}

	/**
	 * 小文字格納マップ作成メソッド
	 * 
	 * @param <T>
	 */
	public static <T> Map<String, T> lowerMap() {
		return new LowerMap<T>();
	}

	/**
	 * 小文字格納順序保持マップ作成メソッド
	 * 
	 * @param <T>
	 */
	public static <T> Map<String, T> lowerLinkedMap() {
		return new LowerMap<T>(new LinkedHashMap<String, T>());
	}

	/**
	 * 小文字格納順序保持マップ作成メソッド
	 * 
	 * @param <T>
	 * @param capacity
	 *            リストの作成サイズ
	 */
	public static <T> Map<String, T> lowerLinkedMap(final int capacity) {
		return new LowerMap<T>(new LinkedHashMap<String, T>(capacity));
	}

	/**
	 * 小文字格納マップ作成メソッド
	 * 
	 * @param <T>
	 * @param capacity
	 *            リストの作成サイズ
	 */
	public static <T> Map<String, T> lowerMap(final int capacity) {
		return new LowerMap<T>(capacity);
	}

	/**
	 * 大文字格納順序保持マップを作成します
	 * 
	 * @param <T>
	 */
	public static <T> Map<String, T> upperLinkedMap() {
		return new UpperMap<T>(new LinkedHashMap<String, T>());
	}

	/**
	 * 大文字格納順序保持マップを作成します
	 * 
	 * @param <T>
	 */
	public static <T> Map<String, T> upperSortedMap() {
		return new UpperMap<T>(new TreeMap<String, T>());
	}

	/**
	 * 大文字格納順序保持マップ作成メソッド
	 * 
	 * @param <T>
	 * @param capacity
	 *            初期サイズ
	 */
	public static <T> Map<String, T> upperLinkedMap(final int capacity) {
		return new UpperMap<T>(new LinkedHashMap<String, T>(capacity));
	}

	/**
	 * CaseInsensitive文字格納保持マップ作成メソッド
	 * 
	 * @param <T>
	 */
	public static <T> Map<String, T> caseInsensitiveMap() {
		return new CaseInsensitiveMap<T>();
	}

	/**
	 * CaseInsensitive文字格納保持マップ作成メソッド
	 * 
	 * @param <T>
	 * @param capacity
	 *            初期サイズ
	 */
	public static <T> Map<String, T> caseInsensitiveMap(final int capacity) {
		return new CaseInsensitiveMap<T>(capacity);
	}

	/**
	 * CaseInsensitive文字格納順序保持マップ作成メソッド
	 * 
	 * @param <T>
	 */
	public static <T> Map<String, T> caseInsensitiveLinkedMap() {
		return new CaseInsensitiveMap<T>(new LinkedHashMap<String, T>());
	}

	/**
	 * CaseInsensitive文字格納順序保持セットを作成します
	 * 
	 */
	public static Set<String> caseInsensitiveLinkedSet() {
		return new CaseInsensitiveSet(new LinkedHashSet<String>());
	}

	/**
	 * CaseInsensitive文字格納順序保持マップ作成メソッド
	 * 
	 * @param <T>
	 * @param capacity
	 *            初期サイズ
	 */
	public static <T> Map<String, T> caseInsensitiveLinkedMap(final int capacity) {
		return new CaseInsensitiveMap<T>(new LinkedHashMap<String, T>(capacity));
	}

	/**
	 * CaseInsensitive文字ツリーマップ作成メソッド
	 * 
	 * @param <T>
	 */
	public static <T> Map<String, T> caseInsensitiveTreeMap() {
		return new CaseInsensitiveMap<T>(new TreeMap<String, T>());
	}

	/**
	 * CaseInsensitive文字ツリーマップ作成メソッド
	 * 
	 * @param <T>
	 * @param m
	 */
	public static <T> Map<String, T> caseInsensitiveTreeMap(final Map<String, T> m) {
		return new CaseInsensitiveMap<T>(new TreeMap<String, T>(m));
	}

	/**
	 * セット作成メソッド
	 * 
	 * @param <T>
	 * @param capacity
	 *            セットの作成サイズ
	 */
	public static <T> Set<T> set(final int capacity) {
		return new HashSet<T>(capacity);
	}

	/**
	 * セット作成メソッド
	 * 
	 * @param <T>
	 * @param capacity
	 *            セットの作成サイズ
	 * @param loadFactor
	 */
	public static <T> Set<T> set(final int capacity, final float loadFactor) {
		return new HashSet<T>(capacity, loadFactor);
	}

	/**
	 * Enumマップ作成メソッド
	 * 
	 */
	public static <S extends Enum<S>> Set<S> enumSetOf(final S enm) {
		return EnumSet.of(enm);
	}

	/**
	 * セット作成メソッド
	 * 
	 * @param <T>
	 * @param args
	 *            セットに設定する値
	 */
	@SafeVarargs
	public static <T> Set<T> set(final T... args) {
		final int size = args.length;
		final Set<T> result = set(args.length);
		for (int i = 0; i < size; i++) {
			result.add(args[i]);
		}
		return result;
	}

	/**
	 * セット作成メソッド
	 * 
	 * @param <T>
	 * @param arg
	 *            セットに設定する値
	 */
	public static <T> Set<T> set(final T arg) {
		final Set<T> result = set();
		result.add(arg);
		return result;
	}

	/**
	 * 小文字のセット作成メソッド
	 * 
	 */
	public static Set<String> lowerSet() {
		final Set<String> result = new LowerSet();
		return result;
	}

	/**
	 * 小文字のセット作成メソッド
	 * 
	 * @param args
	 *            セットに設定する値
	 */
	public static Set<String> lowerSet(final String... args) {
		final int size = args.length;
		final Set<String> result = new LowerSet(size);
		for (int i = 0; i < size; i++) {
			result.add(args[i]);
		}
		return result;
	}

	/**
	 * 大文字のセット作成メソッド
	 * 
	 * @param args
	 *            セットに設定する値
	 */
	public static Set<String> upperSet(final String... args) {
		final int size = args.length;
		final Set<String> result = new UpperSet(size);
		for (int i = 0; i < size; i++) {
			result.add(args[i]);
		}
		return result;
	}

	/**
	 * 大文字のセット作成メソッド
	 * 
	 */
	public static Set<String> upperSet() {
		final Set<String> result = new UpperSet();
		return result;
	}

	/**
	 * セット作成メソッド
	 * 
	 * @param <T>
	 */
	public static <T> Set<T> set() {
		final Set<T> result = new HashSet<T>();
		return result;
	}

	/**
	 * セット作成メソッド
	 * 
	 * @param <T>
	 * @param args
	 */
	public static <T> Set<T> set(final Collection<T> args) {
		final Set<T> result = new HashSet<T>();
		for (final T arg : args) {
			result.add(arg);
		}
		return result;
	}

	/**
	 * セット作成メソッド
	 * 
	 * @param <T>
	 * @param args
	 */
	public static <T> Set<T> set(final List<T> args) {
		final Set<T> result = new HashSet<T>();
		for (final T arg : args) {
			result.add(arg);
		}
		return result;
	}

	/**
	 * LinkedHashSet作成メソッド
	 * 
	 * @param <T>
	 */
	public static <T> Set<T> linkedSet() {
		final Set<T> result = new LinkedHashSet<T>();
		return result;
	}

	/**
	 * LinkedHashSet作成メソッド
	 * 
	 * @param <T>
	 * @param capacity
	 */
	public static <T> Set<T> linkedSet(final int capacity) {
		final Set<T> result = new LinkedHashSet<T>(capacity);
		return result;
	}

	/**
	 * LinkedHashSet作成メソッド
	 * 
	 * @param <T>
	 */
	public static <T> Set<T> linkedSet(final Collection<T> set) {
		final Set<T> result = new LinkedHashSet<T>(set);
		return result;
	}

	/**
	 * LinkedHashSet作成メソッド
	 * 
	 * @param <T>
	 */
	@SafeVarargs
	public static <T> Set<T> linkedSet(final T... args) {
		final Set<T> result = new LinkedHashSet<T>(args.length);
		for (final T arg : args) {
			result.add(arg);
		}
		return result;
	}

	/**
	 * 大文字格納のLinkedHashSet作成メソッド
	 * 
	 */
	public static Set<String> upperLinkedSet() {
		final Set<String> result = new UpperSet(new LinkedHashSet<String>());
		return result;
	}

	/**
	 * 大文字格納のTreeSet作成メソッド
	 * 
	 */
	public static Set<String> upperTreeSet() {
		final Set<String> result = new UpperSet(new TreeSet<String>());
		return result;
	}

	/**
	 * 配列作成メソッド
	 * 
	 * @param <T>
	 * @param args
	 */
	@SafeVarargs
	public static <T> T[] array(final T... args) {
		return args;
	}

	/**
	 * NULLでない最初の要素を返します
	 * 
	 * @param <T>
	 * @param args
	 */
	public static <T> T coalesce(final Object... args) {
		final int size = args.length;
		for (int i = 0; i < size; i++) {
			@SuppressWarnings("unchecked")
			final
			T arg = (T)args[i];
			if (arg != null) {
				return arg;
			}
		}
		return null;
	}

	/**
	 * NULLでない最初の要素を返します
	 * 
	 * @param <T>
	 * @param arg1
	 *            要素1
	 * @param arg2
	 *            要素2
	 */
	public static <T> T coalesce(final T arg1, final T arg2) {
		return arg1 == null ? arg2 : arg1;
	}

	/**
	 * NULLでない最初の要素を返します
	 * 
	 * @param <T>
	 * @param arg1
	 *            要素1
	 * @param arg2
	 *            要素2
	 * @param arg3
	 *            要素3
	 */
	public static <T> T coalesce(final T arg1, final T arg2, final T arg3) {
		return arg1 == null ? (arg2 == null ? arg3 : arg2) : arg1;
	}

	/**
	 * NULLでない最初の要素を返します
	 * 
	 * @param <T>
	 * @param arg1
	 *            要素1
	 * @param arg2
	 *            要素2
	 * @param arg3
	 *            要素3
	 * @param arg4
	 *            要素4
	 */
	public static <T> T coalesce(final T arg1, final T arg2, final T arg3,
			final T arg4) {
		return arg1 == null ? (arg2 == null ? (arg3 == null ? arg4 : arg3)
				: arg2) : arg1;
	}

	/**
	 * NULLでない最初の要素を返します
	 * 
	 * @param <T>
	 * @param args
	 */
	public static <T> T coalesce(final Collection<T> args) {
		for (final T arg : args) {
			if (arg != null) {
				return arg;
			}
		}
		return null;
	}

	/**
	 * NULLまたは空文字でない最初の要素を返す関数
	 * 
	 * @param <T>
	 * @param args
	 */
	@SafeVarargs
	public static <T> T notEmpty(final T... args) {
		for (final T arg : args) {
			if (!isEmpty(arg)) {
				return arg;
			}
		}
		return null;
	}

	/**
	 * NULLまたは空文字でない最初の要素を返す関数
	 * 
	 * @param <T>
	 * @param arg1
	 * @param arg2
	 */
	public static <T> T notEmpty(final T arg1, final T arg2) {
		if (isEmpty(arg1)) {
			return arg2;
		}
		return arg1;
	}

	/**
	 * NULLまたは空文字でない最初の要素を返す関数
	 * 
	 * @param <T>
	 * @param arg1
	 * @param arg2
	 * @param arg3
	 */
	public static <T> T notEmpty(final T arg1, final T arg2, final T arg3) {
		if (isEmpty(arg1)) {
			return notEmpty(arg2, arg3);
		}
		return arg1;
	}

	/**
	 * NULLまたは空文字でない最初の要素を返す関数
	 * 
	 * @param <T>
	 * @param args
	 */
	public static <T> T notEmpty(final Collection<T> args) {
		for (final T arg : args) {
			if (!isEmpty(arg)) {
				return arg;
			}
		}
		return null;
	}

	/**
	 * NULLでない最初の要素を返す関数
	 * 
	 * @param <T>
	 * @param args
	 */
	public static <T> T coalesce(final List<T> args) {
		final int size = args.size();
		for (int i = 0; i < size; i++) {
			final T arg = args.get(i);
			if (arg != null) {
				return arg;
			}
		}
		return null;
	}

	/**
	 * リストの要素から重複を除いた結果を返す
	 * 
	 * @param <T>
	 * @param list
	 */
	public static <T> List<T> unique(final List<T> list) {
		if (list == null) {
			return list;
		}
		final List<T> result = cast(newInstance(list));
		final Set<T> set = set();
		final int size = list.size();
		for (int i = 0; i < size; i++) {
			final T arg = list.get(i);
			if (!set.contains(arg)) {
				result.add(arg);
				set.add(arg);
			}
		}
		return result;
	}

	/**
	 * 配列の要素から重複を除いた結果を返す
	 * 
	 * @param args
	 */
	public static String[] unique(final String[] args) {
		if (args == null) {
			return args;
		}
		final Set<String> set = linkedSet();
		return set.toArray(new String[0]);
	}

	/**
	 * リストの要素の加算
	 * 
	 * @param <T>
	 * @param args
	 */
	@SafeVarargs
	public static <T> List<T> plus(final List<T> list1, final T... args) {
		if (args == null) {
			return list1;
		}
		final List<T> result = list(list1.size());
		java.util.Collections.copy(result, list1);
		final int size = args.length;
		for (int i = 0; i < size; i++) {
			final T arg = args[i];
			result.add(arg);
		}
		return result;
	}

	/**
	 * リストの結合
	 * 
	 * @param <T>
	 * @param lists
	 */
	@SafeVarargs
	public static <T> List<T> union(final List<T>... lists) {
		final List<T> result = list();
		final Set<T> set = set();
		final int size = lists.length;
		for (int i = 0; i < size; i++) {
			final List<T> current = lists[i];
			final int csize = current.size();
			for (int j = 0; j < csize; j++) {
				final T arg = current.get(j);
				if (set.contains(arg)) {
					continue;
				}
				result.add(arg);
				set.add(arg);
			}
		}
		return result;
	}

	/**
	 * Setの結合
	 * 
	 * @param <T>
	 * @param sets
	 */
	@SafeVarargs
	public static <T> Set<T> union(final Set<T>... sets) {
		if (sets == null || sets.length == 0) {
			return set();
		}
		final Set<T> result = cast(newInstance(first(sets)));
		for (final Set<T> set : sets) {
			for (final T obj : set) {
				set.add(obj);
			}
		}
		return result;
	}

	/**
	 * リストからリストの要素の引き算
	 * 
	 * @param <T>
	 */
	public static <T> List<T> minus(final List<T> list1, final List<T> list2) {
		if (list1 == null || list2 == null) {
			return list1;
		}
		final List<T> result = cast(newInstance(list1));
		final int size1 = list1.size();
		final Set<T> set = set(list2);
		for (int i = 0; i < size1; i++) {
			final T arg = list1.get(i);
			if (!set.contains(arg)) {
				result.add(arg);
			}
		}
		return result;
	}

	/**
	 * リストからリストの要素の引き算
	 * 
	 * @param <T>
	 * @param args
	 */
	public static <T> List<T> minus(final List<T> list1, final Collection<T> args) {
		if (list1 == null || args == null) {
			return list1;
		}
		final List<T> result = cast(newInstance(list1));
		final int size1 = list1.size();
		final Set<T> set = set(args);
		for (int i = 0; i < size1; i++) {
			final T arg = list1.get(i);
			if (!set.contains(arg)) {
				result.add(arg);
			}
		}
		return result;
	}

	/**
	 * リストから配列の要素の引き算
	 * 
	 * @param <T>
	 * @param args
	 */
	public static <T> List<T> minus(final List<T> list1, final T[] args) {
		if (list1 == null || args == null) {
			return list1;
		}
		final List<T> result = cast(newInstance(list1));
		final int size1 = list1.size();
		final Set<T> set = set(args);
		for (int i = 0; i < size1; i++) {
			final T arg = list1.get(i);
			if (!set.contains(arg)) {
				result.add(arg);
			}
		}
		return result;
	}

	private static ConcurrentMap<String, Class<?>> classCache = new ConcurrentHashMap<String, Class<?>>();

	/**
	 * 文字列でクラスを取得します
	 * 
	 * @param className
	 *            クラス名
	 */
	@SuppressWarnings("unchecked")
	public static <T> Class<T> classForName(final String className) {
		final Class<?> result = classForNameInternal(className);
		return (Class<T>) result;
	}

	/**
	 * 文字列でクラスを取得します
	 * 
	 * @param className
	 *            クラス名
	 */
	protected static Class<?> classForNameInternal(final String className) {
		Class<?> result = classCache.get(className);
		if (result != null) {
			return result;
		}
		try {
			result = Class.forName(className, false, Thread.currentThread()
					.getContextClassLoader());
			final Class<?> oldValue = classCache.putIfAbsent(className, result);
			return oldValue == null ? result : oldValue;
		} catch (final ClassNotFoundException e) {
			try {
				result = Class.forName(className);
				final Class<?> oldValue = classCache.putIfAbsent(className, result);
				return oldValue == null ? result : oldValue;
			} catch (final ClassNotFoundException e1) {
				throw new RuntimeException(e);
			}
		}
	}

	/**
	 * コンストラクタの取得
	 * 
	 * @param clazz
	 * @param parameterTypes
	 */
	public static Constructor<?> getConstructor(final Class<?> clazz,
			final Class<?>... parameterTypes) {
		Constructor<?> constructor;
		try {
			constructor = clazz.getConstructor(parameterTypes);
		} catch (final SecurityException e) {
			throw new RuntimeException(e);
		} catch (final NoSuchMethodException e) {
			throw new RuntimeException(e);
		}
		return constructor;
	}

	/**
	 * インスタンスの作成
	 * 
	 * @param args
	 */
	public static Object newInstance(final Object... args) {
		final Class<?> clazz = coalesce(args).getClass();
		try {
			return clazz.getDeclaredConstructor().newInstance();
		} catch (final Exception e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * インスタンスの作成
	 * 
	 * @param arg
	 */
	public static Object newInstance(final Object arg) {
		if (arg instanceof ArrayList<?>) {
			return list(((List<?>) arg).size());
		}
		final Class<?> clazz = arg.getClass();
		try {
			return clazz.getDeclaredConstructor().newInstance();
		} catch (final Exception e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * インスタンスの作成
	 * 
	 * @param <T>
	 * @param clazz
	 */
	public static <T> T newInstance(final Class<T> clazz) {
		try {
			return clazz.getDeclaredConstructor().newInstance();
		} catch (final Exception e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Mapインスタンスの作成
	 * 
	 * @param arg
	 */
	@SuppressWarnings("rawtypes")
	public static Map<?, ?> newInstance(final Map<?, ?> arg) {
		if (arg instanceof HashMap<?, ?>) {
			return new HashMap(arg.size());
		} else if (arg instanceof CaseInsensitiveMap<?>) {
			final CaseInsensitiveMap caseInsensitiveMap= ((CaseInsensitiveMap)arg).clone();
			caseInsensitiveMap.clear();
			return caseInsensitiveMap;
		} else if (arg instanceof ConcurrentHashMap<?, ?>) {
			return new ConcurrentHashMap(arg.size());
		} else if (arg instanceof WeakHashMap<?, ?>) {
			return new WeakHashMap(arg.size());
		} else if (arg instanceof AbstractStringMap<?>) {
			return ((AbstractStringMap<?>) arg).newInstance();
		} else if (arg instanceof EnumMap<?, ?>) {
			final Map<?, ?> map = ((EnumMap<?, ?>) arg).clone();
			map.clear();
			return map;
		}
		final Class<?> clazz = arg.getClass();
		try {
			return cast(clazz.getDeclaredConstructor().newInstance());
		} catch (final Exception e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * マップから指定したキーの値を取得する
	 * 
	 * @param <S>
	 * @param <T>
	 * @param map
	 *            取得する対象のマップ
	 * @param args
	 *            マップから取得する値のキー
	 */
	@SafeVarargs
	public static <S, T> List<T> getValues(final Map<S, T> map, final S... args) {
		if (map == null) {
			return null;
		}
		final List<T> list = list();
		final int size = args.length;
		for (int i = 0; i < size; i++) {
			final S arg = args[i];
			if (arg == null) {
				continue;
			}
			if (map.containsKey(arg)) {
				list.add(map.get(arg));
			}
		}
		return list;
	}

	/**
	 * マップから指定したキーの値を取得する
	 * 
	 * @param <S>
	 * @param <T>
	 * @param map
	 *            取得する対象のマップ
	 * @param args
	 *            マップから取得する値のキー
	 */
	public static <S, T> List<T> getValues(final Map<S, T> map,
			final Collection<S> args) {
		if (map == null) {
			return null;
		}
		final List<T> list = list(args.size());
		for (final S arg : args) {
			if (arg == null) {
				continue;
			}
			if (map.containsKey(arg)) {
				list.add(map.get(arg));
			}
		}
		return list;
	}

	/**
	 * マップから指定したキーの値を取得する
	 * 
	 * @param <S>
	 * @param <T>
	 * @param map
	 *            取得する対象のマップ
	 * @param args
	 *            マップから取得する値のキー
	 */
	public static <S, T> List<T> getValues(final Map<S, T> map,
			final List<S> args) {
		if (map == null) {
			return null;
		}
		final List<T> list = list(args.size());
		final int size = args.size();
		for (int i = 0; i < size; i++) {
			final S arg = args.get(i);
			if (arg == null) {
				continue;
			}
			if (map.containsKey(arg)) {
				list.add(map.get(arg));
			}
		}
		return list;
	}

	/**
	 * リストの最初の要素を返す
	 * 
	 * @param <T>
	 * @param args
	 */
	public static <T> T first(final List<T> args) {
		if (args == null) {
			return null;
		}
		if (args.size() == 0) {
			return null;
		}
		return args.get(0);
	}

	/**
	 * Mapの最初の要素を返す
	 * 
	 * @param <T>
	 * @param args
	 */
	public static <S, T> Map.Entry<S, T> first(final Map<S, T> args) {
		if (args == null) {
			return null;
		}
		if (args.size() == 0) {
			return null;
		}
		for (final Map.Entry<S, T> entry : args.entrySet()) {
			return entry;
		}
		return null;
	}

	/**
	 * コレクションの最初の要素を返す
	 * 
	 * @param <T>
	 * @param args
	 */
	public static <T> T first(final Collection<T> args) {
		if (args == null) {
			return null;
		}
		if (args.size() == 0) {
			return null;
		}
		for (final T arg : args) {
			return arg;
		}
		return null;
	}

	/**
	 * リストの最後の要素を返す
	 * 
	 * @param <T>
	 * @param args
	 */
	public static <T> T last(final List<T> args) {
		if (args == null) {
			return null;
		}
		if (args.size() == 0) {
			return null;
		}
		return args.get(args.size() - 1);
	}

	/**
	 * 配列の最初の要素を返す
	 * 
	 * @param <T>
	 * @param args
	 */
	public static <T> T first(final T[] args) {
		if (args == null) {
			return null;
		}
		if (args.length == 0) {
			return null;
		}
		return args[0];
	}

	/**
	 * 配列の指定した位置の要素を返す
	 * 
	 * @param <T>
	 * @param args
	 * @param pos
	 *            取得する配列の位置
	 */
	public static <T> T get(final T[] args, final int pos) {
		if (args == null) {
			return null;
		}
		if (args.length == 0) {
			return null;
		}
		if (args.length <= pos) {
			return null;
		}
		if (pos < 0) {
			return null;
		}
		return args[pos];
	}

	/**
	 * 配列の最初の要素を返す
	 * 
	 * @param <T>
	 * @param args
	 */
	public static <T> T last(final T[] args) {
		if (args == null) {
			return null;
		}
		if (args.length == 0) {
			return null;
		}
		return args[args.length - 1];
	}

	/**
	 * 最小値を取得します
	 * 
	 * @param arg1
	 * @param arg2
	 */
	public static long min(final long arg1, final long arg2) {
		return arg1 < arg2 ? arg1 : arg2;
	}

	/**
	 * 最小値を取得します
	 * 
	 * @param arg1
	 * @param arg2
	 * @param arg3
	 */
	public static long min(final long arg1, final long arg2, final long arg3) {
		return arg1 < arg2 ? (arg1 < arg3 ? arg1 : arg3) : (arg2 < arg3 ? arg2
				: arg3);
	}

	/**
	 * 最小値を取得します
	 * 
	 * @param arg1
	 * @param arg2
	 * @param arg3
	 */
	public static Long min(final Long arg1, final Long arg2, final Long arg3) {
		return min(min(arg1, arg2), arg3);
	}

	/**
	 * 最小値を取得します
	 * 
	 * @param arg1
	 * @param arg2
	 * @param arg3
	 * @param arg4
	 */
	public static long min(final long arg1, final long arg2, final long arg3,
			final long arg4) {
		return min(min(arg1, arg2), min(arg3, arg4));
	}

	/**
	 * 最小値を取得します
	 * 
	 * @param arg1
	 * @param arg2
	 * @param arg3
	 * @param arg4
	 */
	public static Long min(final Long arg1, final Long arg2, final Long arg3,
			final Long arg4) {
		return min(min(arg1, arg2), min(arg3, arg4));
	}

	/**
	 * 最小値を取得します
	 * 
	 * @param arg1
	 * @param arg2
	 * @param arg3
	 * @param arg4
	 * @param arg5
	 */
	public static long min(final long arg1, final long arg2, final long arg3,
			final long arg4, final long arg5) {
		return min(min(arg1, arg2), min(arg3, arg4, arg5));
	}

	/**
	 * 最小値を取得します
	 * 
	 * @param arg1
	 * @param arg2
	 * @param arg3
	 * @param arg4
	 * @param arg5
	 */
	public static Long min(final Long arg1, final Long arg2, final Long arg3,
			final Long arg4, final Long arg5) {
		return min(min(min(arg1, arg2), min(arg3, arg4)), arg5);
	}

	/**
	 * 最小値を取得します
	 * 
	 * @param arg1
	 * @param arg2
	 */
	public static int min(final int arg1, final int arg2) {
		return arg1 < arg2 ? arg1 : arg2;
	}

	/**
	 * 最小値を取得します
	 * 
	 * @param arg1
	 * @param arg2
	 */
	public static Integer min(final Integer arg1, final Integer arg2) {
		if (arg1 == null) {
			if (arg2 == null) {
				return null;
			} else {
				return arg2;
			}
		} else {
			if (arg2 == null) {
				return arg1;
			} else {
				return min(arg1.intValue(), arg2.intValue());
			}
		}
	}

	/**
	 * 最小値を取得します
	 * 
	 * @param arg1
	 *            数値1
	 * @param arg2
	 *            数値2
	 * @param arg3
	 *            数値3
	 */
	public static int min(final int arg1, final int arg2, final int arg3) {
		return arg1 < arg2 ? (arg1 < arg3 ? arg1 : arg3) : (arg2 < arg3 ? arg2
				: arg3);
	}

	/**
	 * 最小値を取得します
	 * 
	 * @param arg1
	 *            数値1
	 * @param arg2
	 *            数値2
	 * @param arg3
	 *            数値3
	 * @param arg4
	 *            数値4
	 */
	public static int min(final int arg1, final int arg2, final int arg3,
			final int arg4) {
		return min(min(arg1, arg2), min(arg3, arg4));
	}

	/**
	 * 最小値を取得します
	 * 
	 * @param arg1
	 *            数値1
	 * @param arg2
	 *            数値2
	 * @param arg3
	 *            数値3
	 */
	public static Integer min(final Integer arg1, final Integer arg2,
			final Integer arg3) {
		return min(min(arg1, arg2), arg3);
	}

	/**
	 * 最小値を取得します
	 * 
	 * @param arg1
	 *            数値1
	 * @param arg2
	 *            数値2
	 * @param arg3
	 *            数値3
	 * @param arg4
	 *            数値4
	 */
	public static Integer min(final Integer arg1, final Integer arg2,
			final Integer arg3, final Integer arg4) {
		return min(min(arg1, arg2), min(arg3, arg4));
	}

	/**
	 * 最大値を取得します
	 * 
	 * @param arg1
	 * @param arg2
	 */
	public static long max(final long arg1, final long arg2) {
		return arg1 > arg2 ? arg1 : arg2;
	}

	/**
	 * 最小値を取得します
	 * 
	 * @param arg1
	 * @param arg2
	 */
	public static Long min(final Long arg1, final Long arg2) {
		if (arg1 == null) {
			if (arg2 == null) {
				return null;
			} else {
				return arg2;
			}
		} else {
			if (arg2 == null) {
				return arg1;
			} else {
				return min(arg1.longValue(), arg2.longValue());
			}
		}
	}

	/**
	 * 最大値を取得します
	 * 
	 * @param arg1
	 * @param arg2
	 */
	public static int max(final int arg1, final int arg2) {
		return arg1 > arg2 ? arg1 : arg2;
	}

	/**
	 * 最大値を取得します
	 * 
	 * @param arg1
	 * @param arg2
	 */
	public static Integer max(final Integer arg1, final Integer arg2) {
		if (arg1 == null) {
			if (arg2 == null) {
				return null;
			} else {
				return arg2;
			}
		} else {
			if (arg2 == null) {
				return arg1;
			} else {
				return max(arg1.intValue(), arg2.intValue());
			}
		}
	}

	/**
	 * 最大値を取得します
	 * 
	 * @param arg1
	 * @param arg2
	 */
	public static Long max(final Long arg1, final Long arg2) {
		if (arg1 == null) {
			if (arg2 == null) {
				return null;
			} else {
				return arg2;
			}
		} else {
			if (arg2 == null) {
				return arg1;
			} else {
				return max(arg1.longValue(), arg2.longValue());
			}
		}
	}

	/**
	 * 最大値を取得します
	 * 
	 * @param arg1
	 * @param arg2
	 * @param arg3
	 */
	public static long max(final long arg1, final long arg2, final long arg3) {
		return arg1 > arg2 ? (arg1 > arg3 ? arg1 : arg3) : (arg2 > arg3 ? arg2
				: arg3);
	}

	/**
	 * 最大値を取得します
	 * 
	 * @param arg1
	 * @param arg2
	 * @param arg3
	 */
	public static Long max(final Long arg1, final Long arg2, final Long arg3) {
		return max(max(arg1, arg2), arg3);
	}

	/**
	 * 最大値を取得します
	 * 
	 * @param arg1
	 * @param arg2
	 * @param arg3
	 * @param arg4
	 */
	public static long max(final long arg1, final long arg2, final long arg3,
			final long arg4) {
		return max(max(arg1, arg2), max(arg3, arg4));
	}

	/**
	 * 最大値を取得します
	 * 
	 * @param arg1
	 * @param arg2
	 * @param arg3
	 * @param arg4
	 */
	public static Long max(final Long arg1, final Long arg2, final Long arg3,
			final Long arg4) {
		return max(max(arg1, arg2), max(arg3, arg4));
	}

	/**
	 * ゼロでない最初の数値を取得します
	 * 
	 * @param args
	 *            数値
	 */
	public static Long notZero(final Long... args) {
		for (final Long arg : args) {
			if (arg != null && arg.longValue() != 0) {
				return arg;
			}
		}
		return null;
	}

	/**
	 * ゼロでない最初の数値を取得します
	 * 
	 * @param args
	 *            数値
	 */
	public static Integer notZero(final Integer... args) {
		for (final Integer arg : args) {
			if (arg != null && arg.intValue() != 0) {
				return arg;
			}
		}
		return null;
	}

	
	/**
	 * ゼロでない数値を取得します
	 * 
	 * @param arg1
	 *            数値1
	 * @param arg2
	 *            数値2
	 */
	public static int notZero(final int arg1, final int arg2) {
		return arg1 == 0 ? arg2 : arg1;
	}


	/**
	 * 値が正数かの判定をします
	 * 
	 * @param value
	 */
	public static boolean isPositive(final Number value) {
		if (value == null) {
			return false;
		}
		return value.longValue() > 0;
	}

	/**
	 * 文字列のサイズを取得します
	 * 
	 * @param c
	 *            文字列
	 */
	public static int size(final StringBuilder c) {
		if (c == null) {
			return 0;
		}
		return c.length();
	}

	/**
	 * 文字列のサイズを取得します
	 * 
	 * @param c
	 *            文字列
	 */
	public static int size(final String c) {
		if (c == null) {
			return 0;
		}
		return c.length();
	}

	/**
	 * コレクションのサイズを取得します
	 * 
	 * @param c
	 *            コレクション
	 */
	public static int size(final Collection<?> c) {
		if (c == null) {
			return 0;
		}
		return c.size();
	}

	/**
	 * マップのサイズを取得します
	 * 
	 * @param m
	 *            マップ
	 */
	public static int size(final Map<?, ?> m) {
		if (m == null) {
			return 0;
		}
		return m.size();
	}

	/**
	 * 配列のサイズを取得します
	 * 
	 * @param args
	 *            配列
	 */
	public static int size(final Object[] args) {
		if (args == null) {
			return 0;
		}
		return args.length;
	}

	/**
	 * 配列のサイズを取得します
	 * 
	 * @param args
	 *            配列
	 */
	public static int size(final byte[] args) {
		if (args == null) {
			return 0;
		}
		return args.length;
	}

	/**
	 * 同値判定をします
	 * 
	 * @param obj1
	 * @param obj2
	 */
	public static boolean eq(final Object obj1, final Object obj2) {
		if (obj1 == obj2) {
			return true;
		}
		if (obj1 == null) {
			return false;
		}
		return obj1.equals(obj2);
	}

	/**
	 * 同値判定
	 * 
	 * @param obj1
	 * @param obj2
	 */
	public static boolean eq(final Object[] obj1, final Object[] obj2) {
		return Arrays.deepEquals(obj1, obj2);
	}

	/**
	 * 同値判定
	 * 
	 * @param obj1
	 * @param obj2
	 */
	public static boolean eq(final String[] obj1, final String[] obj2) {
		return Arrays.deepEquals(obj1, obj2);
	}

	/**
	 * 同値判定
	 * 
	 * @param a
	 * @param a2
	 */
	public static boolean eq(final boolean[] a, final boolean[] a2) {
		return Arrays.equals(a, a2);
	}

	/**
	 * 同値判定
	 * 
	 * @param a
	 * @param a2
	 */
	public static boolean eq(final byte[] a, final byte[] a2) {
		return Arrays.equals(a, a2);
	}

	/**
	 * 同値判定
	 * 
	 * @param a
	 * @param a2
	 */
	public static boolean eq(final char[] a, final char[] a2) {
		return Arrays.equals(a, a2);
	}

	/**
	 * 同値判定
	 * 
	 * @param a
	 * @param a2
	 */
	public static boolean eq(final short[] a, final short[] a2) {
		return Arrays.equals(a, a2);
	}

	/**
	 * 同値判定
	 * 
	 * @param a
	 * @param a2
	 */
	public static boolean eq(final int[] a, final int[] a2) {
		return Arrays.equals(a, a2);
	}

	/**
	 * 同値判定
	 * 
	 * @param a
	 * @param a2
	 */
	public static boolean eq(final long[] a, final long[] a2) {
		return Arrays.equals(a, a2);
	}

	/**
	 * 同値判定
	 * 
	 * @param a
	 * @param a2
	 */
	public static boolean eq(final float[] a, final float[] a2) {
		return Arrays.equals(a, a2);
	}

	/**
	 * 同値判定
	 * 
	 * @param a
	 * @param a2
	 */
	public static boolean eq(final double[] a, final double[] a2) {
		return Arrays.equals(a, a2);
	}

	/**
	 * 同値判定
	 * 
	 * @param obj1
	 * @param obj2
	 */
	public static boolean eq(final long obj1, final long obj2) {
		return obj1 == obj2;
	}

	/**
	 * 大文字、小文字を無視した比較
	 * 
	 * @param obj1
	 * @param obj2
	 */
	public static boolean eqIgnoreCase(final String obj1, final String obj2) {
		if (obj1 == null) {
			if (obj2 == null) {
				return true;
			}
			return false;
		}
		return obj1.equalsIgnoreCase(obj2);
	}

	/**
	 * カラム名の一致を判定します
	 * 
	 * @param column
	 * @param targetColumn
	 */
	public static boolean eqColumnName(final Column column,
			final Column targetColumn) {
		return eqIgnoreCase(column.getName(), targetColumn.getName());
	}

	/**
	 * カラム名の一致を判定します
	 * 
	 * @param column
	 * @param targetColumn
	 */
	public static boolean eqColumnName(final Column column,
			final ReferenceColumn targetColumn) {
		return eqIgnoreCase(column.getName(), targetColumn.getName());
	}

	/**
	 * カラム名の一致を判定します
	 * 
	 * @param column
	 * @param targetColumn
	 */
	public static boolean eqColumnName(final ReferenceColumn column,
			final Column targetColumn) {
		return eqIgnoreCase(column.getName(), targetColumn.getName());
	}

	/**
	 * カラム名の一致を判定します
	 * 
	 * @param column
	 * @param targetColumn
	 */
	public static boolean eqColumnName(final ReferenceColumn column,
			final ReferenceColumn targetColumn) {
		return eqIgnoreCase(column.getName(), targetColumn.getName());
	}

	/**
	 * カラム名の一致を判定します
	 * 
	 * @param columns
	 * @param targetColumns
	 */
	public static boolean eqColumnNames(final Column[] columns,
			final Column[] targetColumns) {
		if (columns.length != targetColumns.length) {
			return false;
		}
		final int size = columns.length;
		for (int i = 0; i < size; i++) {
			if (!eqColumnName(columns[i], targetColumns[i])) {
				return false;
			}
		}
		return true;
	}

	/**
	 * カラム名の一致を判定します
	 * 
	 * @param columns
	 * @param targetColumns
	 */
	public static boolean eqColumnNames(final Column[] columns,
			final ReferenceColumn[] targetColumns) {
		if (columns.length != targetColumns.length) {
			return false;
		}
		final int size = columns.length;
		for (int i = 0; i < size; i++) {
			if (!eqColumnName(columns[i], targetColumns[i])) {
				return false;
			}
		}
		return true;
	}

	/**
	 * カラム名の一致を判定します
	 * 
	 * @param columns
	 * @param targetColumns
	 */
	public static boolean eqColumnNames(final ReferenceColumn[] columns,
			final Column[] targetColumns) {
		if (columns.length != targetColumns.length) {
			return false;
		}
		final int size = columns.length;
		for (int i = 0; i < size; i++) {
			if (!eqColumnName(columns[i], targetColumns[i])) {
				return false;
			}
		}
		return true;
	}

	/**
	 * カラム名の一致を判定します
	 * 
	 * @param columns
	 * @param targetColumns
	 */
	public static boolean eqColumnNames(final ReferenceColumn[] columns,
			final ReferenceColumn[] targetColumns) {
		if (columns.length != targetColumns.length) {
			return false;
		}
		final int size = columns.length;
		for (int i = 0; i < size; i++) {
			if (!eqIgnoreCase(columns[i].getName(), targetColumns[i].getName())) {
				return false;
			}
		}
		return true;
	}

	/**
	 * リストのコピー
	 * 
	 * @param <T>
	 * @param list
	 */
	public static <T> List<T> cloneList(final List<T> list) {
		if (list == null) {
			return null;
		}
		final List<T> clone = cast(newInstance(list));
		for (final T val : list) {
			clone.add(val);
		}
		return clone;
	}

	/**
	 * セットのコピー
	 * 
	 * @param <T>
	 * @param set
	 */
	public static <T> Set<T> cloneSet(final Set<T> set) {
		if (set == null) {
			return null;
		}
		final Set<T> clone = cast(newInstance(set));
		for (final T val : set) {
			clone.add(val);
		}
		return clone;
	}

	/**
	 * マップのコピー
	 * 
	 * @param <T>
	 * @param map
	 */
	public static <S, T> Map<S, T> cloneMap(final Map<S, T> map) {
		if (map == null) {
			return null;
		}
		final Map<S, T> clone = cast(newInstance(map));
		for (final Entry<S, T> entry : map.entrySet()) {
			clone.put(entry.getKey(), entry.getValue());
		}
		return clone;
	}

	/**
	 * 数値型のクラスの判定
	 * 
	 * @param clazz
	 */
	public static boolean isNumberClass(final Class<?> clazz) {
		if (clazz.isAssignableFrom(Number.class)) {
			return true;
		}
		return false;
	}

	/**
	 * 配列で与えた数値の和の返却します
	 * 
	 * @param val1
	 *             値1
	 * @param val2
	 *             値2
	 */
	public static int sum(final int val1, final int val2) {
		return val1 + val2;
	}

	/**
	 * 配列で与えた数値の和の返却します
	 * 
	 * @param val1
	 *             値1
	 * @param val2
	 *             値2
	 * @param val3
	 *             値3
	 */
	public static int sum(final int val1, final int val2, final int val3) {
		return val1 + val2 + val3;
	}

	/**
	 * 配列で与えた数値の和の返却します
	 * 
	 * @param val1
	 *             値1
	 * @param val2
	 *             値2
	 * @param val3
	 *             値3
	 * @param val4
	 *             値4
	 */
	public static int sum(final int val1, final int val2, final int val3,
			final int val4) {
		return val1 + val2 + val3 + val4;
	}

	/**
	 * 配列で与えた数値の和の返却します
	 * 
	 * @param vals
	 *             合計値
	 */
	public static int sum(final int... vals) {
		int count = 0;
		final int size = vals.length;
		for (int i = 0; i < size; i++) {
			count = count + vals[i];
		}
		return count;
	}

	/**
	 * 配列で与えた数値に指定した値が含まれているかの判定
	 * 
	 * @param vals
	 *             合計値
	 * @param containsVal
	 *             判定対象の値
	 */
	public static boolean containsValue(final int[] vals, final int containsVal) {
		final int size = vals.length;
		for (int i = 0; i < size; i++) {
			if (containsVal == vals[i]) {
				return true;
			}
		}
		return false;
	}

	/**
	 * 配列で与えた数値の和の返却
	 * 
	 * @param vals
	 *             合計値
	 */
	public static long sum(final long... vals) {
		long count = 0;
		final int size = vals.length;
		for (int i = 0; i < size; i++) {
			count = count + vals[i];
		}
		return count;
	}

	/**
	 * 配列で与えた数値の和の返却します
	 * 
	 * @param val1
	 *             値1
	 * @param val2
	 *             値2
	 */
	public static long sum(final long val1, final long val2) {
		return val1 + val2;
	}

	/**
	 * 配列で与えた数値の和の返却します
	 * 
	 * @param val1
	 *            値1
	 * @param val2
	 *             値2
	 * @param val3
	 *             値3
	 */
	public static long sum(final long val1, final long val2, final long val3) {
		return val1 + val2 + val3;
	}

	/**
	 * 配列で与えた数値の和の返却します
	 * 
	 * @param val1
	 *             値1
	 * @param val2
	 *             値2
	 * @param val3
	 *             値3
	 * @param val4
	 *             値4
	 */
	public static long sum(final long val1, final long val2, final long val3,
			final long val4) {
		return val1 + val2 + val3 + val4;
	}

	/**
	 * Beanかどうかの判定
	 * 
	 * @param obj
	 */
	public static boolean isBean(final Object obj) {
		return obj.getClass().getDeclaringClass() == null;
	}

	/**
	 * NULLもしくは空文字もしくはスペースかタブのみで構成されているかを判定します
	 * 
	 * @param obj
	 *            判定対象のオブジェクト
	 * @return NULLもしくは空文字の場合、true
	 */
	public static boolean isBlank(final Object obj) {
		if (obj == null) {
			return true;
		}
		if (obj instanceof CharSequence) {
			return isBlank((CharSequence) obj);
		}
		return isEmpty(obj);
	}

	/**
	 * NULLもしくは空文字もしくはスペースかタブのみで構成されているかを判定します
	 * 
	 * @param obj
	 *            判定対象のオブジェクト
	 * @return NULLもしくは空文字の場合、true
	 */
	public static boolean isBlank(final CharSequence obj) {
		if (obj == null || obj.length() == 0) {
			return true;
		}
		final int size = obj.length();
		for (int i = 0; i < size; i++) {
			final char c = obj.charAt(i);
			if (!Character.isWhitespace(c)) {
				return false;
			}
		}
		return true;
	}

	/**
	 * NULLもしくは空文字もしくはスペースかタブのみで構成されていない場合、<code>true</code>を返します
	 * 
	 * @param obj
	 *            判定対象のオブジェクト
	 * @return NULLもしくは空文字のみで構成されていない場合、<code>true</code>
	 */
	public static boolean isNotBlank(final CharSequence obj) {
		return !isBlank(obj);
	}

	/**
	 * NULLもしくは空文字もしくはスペースかタブのみで構成されていない場合、<code>true</code>を返します
	 * 
	 * @param obj
	 *            判定対象のオブジェクト
	 * @return NULLもしくは空文字のみで構成されていない場合、<code>true</code>
	 */
	public static boolean isNotBlank(final Object obj) {
		return !isBlank(obj);
	}

	/**
	 * NULLもしくは空文字の判定
	 * 
	 * @param obj
	 *            判定対象のオブジェクト
	 * @return NULLもしくは空文字の場合、true
	 */
	public static boolean isEmpty(final Object obj) {
		if (obj == null) {
			return true;
		}
		if (obj instanceof CharSequence) {
			return ((CharSequence) obj).length() == 0;
		} else if (obj instanceof Collection<?>) {
			return ((Collection<?>) obj).size() == 0;
		} else if (obj instanceof Map<?, ?>) {
			return ((Map<?, ?>) obj).size() == 0;
		} else if (obj instanceof DbInfo) {
			return ((DbInfo) obj).isEmpty();
		} else if (obj.getClass().isArray()) {
			return Array.getLength(obj) == 0;
		}
		return false;
	}

	/**
	 * NULLもしくは空文字か判定します
	 * 
	 * @param obj
	 *            判定対象のオブジェクト
	 * @return NULLもしくは空文字の場合、true
	 */
	public static boolean isEmpty(final CharSequence obj) {
		if (obj == null) {
			return true;
		}
		return obj.length() == 0;
	}

	/**
	 * NULLもしくは空文字か判定します
	 * 
	 * @param obj
	 *            判定対象のオブジェクト
	 * @return NULLもしくは空文字の場合、true
	 */
	public static boolean isEmpty(final Collection<?> obj) {
		if (obj == null) {
			return true;
		}
		return obj.size() == 0;
	}

	/**
	 * NULLもしくは空文字か判定します
	 * 
	 * @param obj
	 *            判定対象のオブジェクト
	 * @return NULLもしくは空文字の場合、true
	 */
	public static boolean isEmpty(final Map<?, ?> obj) {
		if (obj == null) {
			return true;
		}
		return obj.size() == 0;
	}

	/**
	 * NULLもしくは空文字か判定します
	 * 
	 * @param obj
	 *            判定対象のオブジェクト
	 * @return NULLもしくは空文字の場合、true
	 */
	public static boolean isEmpty(final DbInfo obj) {
		if (obj == null) {
			return true;
		}
		return obj.isEmpty();
	}

	/**
	 * NULLでも空文字でもない場合、<code>true</code>を返します。
	 * 
	 * @param obj
	 *            判定対象のオブジェクト
	 * @return NULLでも空文字でもない場合、<code>true</code>
	 */
	public static boolean isNotEmpty(final Object obj) {
		return !isEmpty(obj);
	}

	/**
	 * NULLでも空文字でもない場合、<code>true</code>を返します。
	 * 
	 * @param obj
	 *            判定対象のオブジェクト
	 * @return NULLでも空文字でもない場合、<code>true</code>
	 */
	public static boolean isNotEmpty(final CharSequence obj) {
		return !isEmpty(obj);
	}

	/**
	 * NULLでも空文字でもない場合、<code>true</code>を返します。
	 * 
	 * @param obj
	 *            判定対象のオブジェクト
	 * @return NULLでも空文字でもない場合、<code>true</code>
	 */
	public static boolean isNotEmpty(final Collection<?> obj) {
		return !isEmpty(obj);
	}

	/**
	 * NULLでも空文字でもない場合、<code>true</code>を返します。
	 * 
	 * @param obj
	 *            判定対象のオブジェクト
	 * @return NULLでも空文字でもない場合、<code>true</code>
	 */
	public static boolean isNotEmpty(final Map<?, ?> obj) {
		return !isEmpty(obj);
	}

	/**
	 * NULLでも空文字でもない場合、<code>true</code>を返します。
	 * 
	 * @param obj
	 *            判定対象のオブジェクト
	 * @return NULLでも空文字でもない場合、<code>true</code>
	 */
	public static boolean isNotEmpty(final DbInfo obj) {
		return !isEmpty(obj);
	}

	/**
	 * NULLもしくは空文字の判定
	 * 
	 * @param args
	 */
	public static boolean isAllEmpty(final Object... args) {
		for (final Object arg : args) {
			if (!isEmpty(arg)) {
				return false;
			}
		}
		return true;
	}

	/**
	 * NULLもしくは空文字の判定
	 * 
	 * @param args
	 */
	public static boolean isAllNullOrEmpty(final String... args) {
		for (final Object arg : args) {
			if (!isEmpty(arg)) {
				return false;
			}
		}
		return true;
	}

	/**
	 * 空文字の場合NULLでそれ以外の場合はそのまま値を返す
	 * 
	 * @param value
	 */
	public static String emptyToNull(final String value) {
		if (value == null || value.length() == 0) {
			return null;
		}
		return value;
	}

	/**
	 * NULLもしくは空文字でない最初の文字を返します
	 * 
	 * @param arg1
	 * @param arg2
	 */
	public static String notEmpty(final String arg1, final String arg2) {
		if (!isEmpty(arg1)) {
			return arg1;
		}
		return arg2;
	}

	/**
	 * NULLもしくは空文字でない最初の文字を返します
	 * 
	 * @param arg1
	 * @param arg2
	 * @param arg3
	 */
	public static String notEmpty(final String arg1, final String arg2,
			final String arg3) {
		if (!isEmpty(arg1)) {
			return arg1;
		}
		if (!isEmpty(arg2)) {
			return arg2;
		}
		return arg3;
	}

	/**
	 * NULLもしくは空文字でない最初の文字を返します
	 * 
	 * @param arg1
	 * @param arg2
	 * @param arg3
	 * @param arg4
	 */
	public static String notEmpty(final String arg1, final String arg2,
			final String arg3, final String arg4) {
		if (!isEmpty(arg1)) {
			return arg1;
		}
		if (!isEmpty(arg2)) {
			return arg2;
		}
		if (!isEmpty(arg3)) {
			return arg3;
		}
		return arg4;
	}

	/**
	 * NULLもしくは空文字かを判定します
	 * 
	 * @param args
	 */
	public static String notEmpty(final String... args) {
		for (final String arg : args) {
			if (!isEmpty(arg)) {
				return arg;
			}
		}
		return null;
	}

	/**
	 * 空白を削除します
	 * 
	 * @param value
	 */
	public static String trim(final String value) {
		if (value == null) {
			return value;
		}
		return value.trim();
	}

	/**
	 * 指定した文字をトリムします
	 * 
	 * @param value
	 */
	public static String trim(final String value, final char... cs) {
		if (value == null) {
			return value;
		}
		final int length = value.length();
		int i = 0;
		int j = 0;
		for (i = 0; i < length; i++) {
			boolean exists = false;
			for (int k = 0; k < cs.length; k++) {
				if (value.charAt(i) == cs[k]) {
					exists = true;
					break;
				}
			}
			if (!exists) {
				break;
			}
		}
		for (j = length - 1; j >= 0; j--) {
			boolean exists = false;
			for (int k = 0; k < cs.length; k++) {
				if (value.charAt(j) == cs[k]) {
					exists = true;
					break;
				}
			}
			if (!exists) {
				break;
			}
		}
		if (i > j) {
			return "";
		}
		return value.substring(i, j + 1);
	}

	/**
	 * オブジェクトのコピー
	 * 
	 * @param <T>
	 * @param targetObject
	 */
	public static <T> T copy(final Object targetObject) {
		try {
			final ByteArrayOutputStream bos = new ByteArrayOutputStream();
			final ObjectOutputStream oos = new ObjectOutputStream(bos);
			oos.writeObject(targetObject);
			final ObjectInputStream ois = new ObjectInputStream(
					new ByteArrayInputStream(bos.toByteArray()));
			@SuppressWarnings("unchecked")
			final
			T val = (T) (ois.readObject());
			return val;
		} catch (final Exception e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * 絶対値の取得
	 * 
	 * @param val
	 */
	public static int abs(final int val) {
		if (val < 0) {
			return -val;
		}
		return val;
	}

	/**
	 * 絶対値の取得
	 * 
	 * @param val
	 */
	public static long abs(final long val) {
		if (val < 0) {
			return -val;
		}
		return val;
	}

	/**
	 * 絶対値の取得
	 * 
	 * @param val
	 */
	public static double abs(final double val) {
		if (val < 0) {
			return -val;
		}
		return val;
	}

	/**
	 * 絶対値の取得
	 * 
	 * @param val
	 */
	public static BigDecimal abs(final BigDecimal val) {
		if (val == null) {
			return null;
		}
		return val.abs();
	}

	/**
	 * 左側から指定した長さの文字列の取得
	 * 
	 * @param value
	 * @param len
	 */
	public static String left(final String value, final int len) {
		if (value == null || value.length() == 0) {
			return value;
		}
		if (value.length() < len) {
			return value;
		}
		return value.substring(0, len);
	}

	/**
	 * 右側から指定した長さの文字列の取得
	 * 
	 * @param value
	 * @param len
	 */
	public static String right(final String value, final int len) {
		if (value == null || value.length() == 0) {
			return value;
		}
		if (value.length() < len) {
			return value;
		}
		return value.substring(value.length() - len, value.length());
	}

	/**
	 * 右側の空白の削除
	 * 
	 * @param value
	 */
	public static String rtrim(final String value, final char... deleteChars) {
		if (value == null || value.length() == 0) {
			return value;
		}
		if (deleteChars == null || deleteChars.length == 0) {
			return value;
		}
		int i = 0;
		for (i = value.length() - 1; i >= 0; i--) {
			boolean find=false;;
			for(final char deleteChar:deleteChars){
				if (value.charAt(i) == deleteChar) {
					find=true;
					break;
				}
			}
			if (!find){
				break;
			}
		}
		return value.substring(0, i + 1);
	}

	/**
	 * 右側の空白を削除します
	 * 
	 * @param value
	 */
	public static String rtrim(final String value) {
		return rtrim(value, ' ', '\n', '\r', '\t');
	}

	/**
	 * 左側の空白の削除
	 * 
	 * @param value
	 */
	public static String ltrim(final String value, final char deleteChar) {
		if (value == null || value.length() == 0) {
			return value;
		}
		int i = 0;
		for (i = 0; i < value.length(); i++) {
			if (value.charAt(i) != deleteChar) {
				break;
			}
		}
		return value.substring(i);
	}

	/**
	 * 右側の空白の削除
	 * 
	 * @param value
	 */
	public static String ltrim(final String value) {
		return ltrim(value, ' ');
	}

	/**
	 * 文字列をLongに変換
	 * 
	 * @param val
	 */
	public static Long toLong(final String val) {
		if (isEmpty(val)) {
			return null;
		}
		try {
			final Long size = Long.valueOf(val);
			return size;
		} catch (final NumberFormatException e) {
			return null;
		}
	}

	/**
	 * 文字列をIntegerに変換
	 * 
	 * @param val
	 */
	public static Integer toInteger(final String val) {
		if (isEmpty(val)) {
			return null;
		}
		try {
			final Integer size = Integer.valueOf(val);
			return size;
		} catch (final NumberFormatException e) {
			return null;
		}
	}

	/**
	 * Long配列をlong配列に変換
	 * 
	 * @param vals
	 */
	public static long[] toLongArray(final Long[] vals) {
		final int size = vals.length;
		final long[] ret = new long[vals.length];
		for (int i = 0; i < size; i++) {
			if (vals[i] != null) {
				ret[i] = vals[i].longValue();
			}
		}
		return ret;
	}

	/**
	 * Integer配列をint配列に変換
	 * 
	 * @param vals
	 */
	public static int[] toIntArray(final Integer[] vals) {
		final int size = vals.length;
		final int[] ret = new int[vals.length];
		for (int i = 0; i < size; i++) {
			if (vals[i] != null) {
				ret[i] = vals[i].intValue();
			}
		}
		return ret;
	}

	/**
	 * Short配列をshort配列に変換
	 * 
	 * @param vals
	 */
	public static short[] toShortArray(final Short[] vals) {
		final int size = vals.length;
		final short[] ret = new short[vals.length];
		for (int i = 0; i < size; i++) {
			if (vals[i] != null) {
				ret[i] = vals[i].shortValue();
			}
		}
		return ret;
	}

	/**
	 * Byte配列をbyte配列に変換
	 * 
	 * @param vals
	 */
	public static byte[] toByteArray(final Byte[] vals) {
		final int size = vals.length;
		final byte[] ret = new byte[vals.length];
		for (int i = 0; i < size; i++) {
			if (vals[i] != null) {
				ret[i] = vals[i].byteValue();
			}
		}
		return ret;
	}

	/**
	 * Float配列をfloat配列に変換
	 * 
	 * @param vals
	 */
	public static float[] toFloatArray(final Float[] vals) {
		final int size = vals.length;
		final float[] ret = new float[vals.length];
		for (int i = 0; i < size; i++) {
			if (vals[i] != null) {
				ret[i] = vals[i].floatValue();
			}
		}
		return ret;
	}

	/**
	 * Double配列をdouble配列に変換
	 * 
	 * @param vals
	 */
	public static double[] toDoubleArray(final Double[] vals) {
		final int size = vals.length;
		final double[] ret = new double[vals.length];
		for (int i = 0; i < size; i++) {
			if (vals[i] != null) {
				ret[i] = vals[i].doubleValue();
			}
		}
		return ret;
	}

	/**
	 * 改行の削除
	 * 
	 * @param val
	 */
	public static String deleteLineSeparator(final String val) {
		if (isEmpty(val)) {
			return val;
		}
		return val.replace("\r", "").replace("\n", "");
	}

	public static String replaceInvalidCharacter(final String text) {
		if (text==null){
			return null;
		}
		final StringBuilder builder=new StringBuilder(text.length());
		final int len=text.codePointCount(0, text.length());
		for(int i=0;i<len;i++){
			final int codePoint=text.codePointAt(i);
			if (codePoint=='\n'){
				builder.appendCodePoint(codePoint);
			}else if (codePoint=='\t'){
				builder.appendCodePoint(codePoint);
			}else if (Character.isISOControl(codePoint)){
				
			} else{
				builder.appendCodePoint(codePoint);
			}
		}
		//return text.replaceAll("[\u0000-\u0008\u000B\u000C\u000E-\u001F\u007F\u0080-\u009F]", "");
		return builder.toString();
	}

	/**
	 * 文字列を正規表現で分割した配列を返します
	 * 
	 * @param value
	 * @param regex
	 */
	public static String[] split(final String value, final String regex) {
		if (isEmpty(value)) {
			return new String[0];
		}
		return value.split(regex);
	}

	/**
	 * 文字列を改行でリストに変換します
	 * 
	 * @param value
	 */
	public static List<String> splitLine(final String value) {
		if (isEmpty(value)) {
			return list();
		}
		final List<String> result = list();
		final String[] split = value.split("\n");
		for (final String val : split) {
			result.add(replaceInvalidCharacter(val));
		}
		return result;
	}

	/**
	 * BigDecimalのScaleの設定
	 * 
	 * @param val
	 * @param newScale
	 */
	public static BigDecimal setScale(final BigDecimal val, final int newScale) {
		if (val == null) {
			return null;
		}
		return val.setScale(newScale);
	}

	/**
	 *  排他的論理和を算出します
	 * 
	 * @param arg1
	 *            引数1
	 * @param arg2
	 *            引数2
	 */
	public static int xor(final int arg1, final int arg2) {
		return arg1 ^ arg2;
	}

	/**
	 *  排他的論理和を算出します
	 * 
	 * @param arg1
	 *            引数1
	 * @param arg2
	 *            引数2
	 * @param arg3
	 *            引数3
	 */
	public static int xor(final int arg1, final int arg2, final int arg3) {
		return arg1 ^ arg2 ^ arg3;
	}

	/**
	 *  排他的論理和を算出します
	 * 
	 * @param arg1
	 *            引数1
	 * @param arg2
	 *            引数2
	 * @param arg3
	 *            引数3
	 * @param arg4
	 *            引数4
	 */
	public static int xor(final int arg1, final int arg2, final int arg3,
			final int arg4) {
		return arg1 ^ arg2 ^ arg3 ^ arg4;
	}

	/**
	 *  排他的論理和を算出します
	 * 
	 * @param args
	 */
	public static int xor(final int... args) {
		if (args == null || args.length == 0) {
			return 0;
		}
		int val = args[0];
		final int len = args.length;
		for (int i = 1; i < len; i++) {
			val = val ^ args[i];
		}
		return val;
	}

	/**
	 *  ハッシュコードを算出します
	 * 
	 * @param arg
	 *            引数
	 */
	public static int hashCode(final Object arg) {
		return arg == null ? 0 : arg.hashCode();
	}

	/**
	 *  ハッシュコードを算出します
	 * 
	 * @param arg1
	 *            引数1
	 * @param arg2
	 *            引数2
	 */
	public static int hashCode(final Object arg1, final Object arg2) {
		return hashCode(arg1) ^ hashCode(arg2);
	}

	/**
	 *  ハッシュコードを算出します
	 * 
	 * @param arg1
	 *            引数1
	 * @param arg2
	 *            引数2
	 * @param arg3
	 *            引数3
	 */
	public static int hashCode(final Object arg1, final Object arg2,
			final Object arg3) {
		return hashCode(arg1) ^ hashCode(arg2) ^ hashCode(arg3);
	}

	/**
	 *  ハッシュコードを算出します
	 * 
	 * @param arg1
	 *            引数1
	 * @param arg2
	 *            引数2
	 * @param arg3
	 *            引数3
	 * @param arg4
	 *            引数4
	 */
	public static int hashCode(final Object arg1, final Object arg2,
			final Object arg3, final Object arg4) {
		return hashCode(arg1) ^ hashCode(arg2) ^ hashCode(arg3)
				^ hashCode(arg4);
	}

	/**
	 *  ハッシュコードを算出します
	 * 
	 * @param arg1
	 *            引数1
	 * @param arg2
	 *            引数2
	 * @param arg3
	 *            引数3
	 * @param arg4
	 *            引数4
	 * @param arg5
	 *            引数5
	 */
	public static int hashCode(final Object arg1, final Object arg2,
			final Object arg3, final Object arg4, final Object arg5) {
		return hashCode(arg1) ^ hashCode(arg2) ^ hashCode(arg3)
				^ hashCode(arg4) ^ hashCode(arg5);
	}

	/**
	 *  ハッシュコードを算出します
	 * 
	 * @param arg1
	 *            引数1
	 * @param arg2
	 *            引数2
	 * @param arg3
	 *            引数3
	 * @param arg4
	 *            引数4
	 * @param arg5
	 *            引数5
	 * @param arg6
	 *            引数6
	 */
	public static int hashCode(final Object arg1, final Object arg2,
			final Object arg3, final Object arg4, final Object arg5,
			final Object arg6) {
		return hashCode(arg1) ^ hashCode(arg2) ^ hashCode(arg3)
				^ hashCode(arg4) ^ hashCode(arg5) ^ hashCode(arg6);
	}

	/**
	 *  排他的論理和の算出
	 * 
	 * @param args
	 */
	public static int xor(final Object... args) {
		if (args == null || args.length == 0) {
			return 0;
		}
		int val = 0;
		if (args[0] != null) {
			val = args[0].hashCode();
		}
		final int len = args.length;
		for (int i = 1; i < len; i++) {
			if (args[i] != null) {
				val = val ^ hashCode(args[i]);
			}
		}
		return val;
	}

	/**
	 *  排他的論理和を算出します
	 * 
	 * @param arg
	 */
	public static int xor(final boolean caseInsensitive, final String arg) {
		if (arg == null) {
			return 0;
		}
		int val = 0;
		if (caseInsensitive) {
			val = arg.toUpperCase().hashCode();
		} else {
			val = arg.hashCode();
		}
		return val;
	}

	/**
	 *  排他的論理和の算出
	 * 
	 * @param args
	 */
	public static int xor(final boolean caseInsensitive, final String... args) {
		if (args == null || args.length == 0) {
			return 0;
		}
		int val = xor(caseInsensitive, args[0]);
		final int len = args.length;
		for (int i = 1; i < len; i++) {
			val = val ^ xor(caseInsensitive, args[i]);
		}
		return val;
	}

	/**
	 * ルート例外の取得
	 * 
	 * @param t
	 */
	public static Throwable getRootCause(final Throwable t) {
		if (t.getCause() == null || t == t.getCause()) {
			return t;
		}
		return getRootCause(t.getCause());
	}

	/**
	 * プレフィクスとサフィックスを除いた文字を返す
	 * 
	 * @param val
	 * @param prefix
	 * @param suffix
	 */
	public static String unwrap(final String val, final String prefix,
			final String suffix) {
		if (isEmpty(val)) {
			return val;
		}
		if (val.startsWith(prefix) && val.endsWith(suffix)) {
			final String newVal = val.substring(prefix.length(), val.length()
					- suffix.length());
			if (newVal.startsWith(prefix)) {
				return unwrap(newVal, prefix, suffix);
			}
			return newVal;
		}
		return val;
	}

	/**
	 * プレフィクスとサフィックスを除いた文字を返す
	 * 
	 * @param val
	 * @param prefix
	 * @param suffix
	 */
	public static String unwrap(final String val, final char prefix,
			final char suffix) {
		if (isEmpty(val)) {
			return val;
		}
		if (val.charAt(0) == prefix && val.charAt(val.length() - 1) == suffix) {
			final String newVal = val.substring(1, val.length() - 1);
			if (newVal.length() == 0) {
				return newVal;
			}
			if (newVal.charAt(0) == prefix) {
				return unwrap(newVal, prefix, suffix);
			}
			return newVal;
		}
		return val;
	}

	/**
	 * 両端のクォートを除いた文字を返す
	 * 
	 * @param val
	 * @param quate
	 */
	public static String unwrap(final String val, final String quate) {
		if (isEmpty(val)) {
			return val;
		}
		if (val.startsWith(quate) && val.endsWith(quate)) {
			final String newVal = val.substring(quate.length(),
					val.length() - quate.length());
			if (newVal.startsWith(quate)) {
				return unwrap(newVal, quate);
			}
			return newVal;
		}
		return val;
	}

	/**
	 * 両端のクォートを除いた文字を返す
	 * 
	 * @param val
	 * @param quate
	 */
	public static String unwrap(final String val, final char quate) {
		if (isEmpty(val)) {
			return val;
		}
		if (val.charAt(0) == quate && val.charAt(val.length() - 1) == quate) {
			final String newVal = val.substring(1, val.length() - 1);
			if (newVal.length() == 0) {
				return newVal;
			}
			if (newVal.charAt(0) == quate) {
				return unwrap(newVal, quate);
			}
			return newVal;
		}
		return val;
	}

	/**
	 * 文字列の置換を行います
	 * 
	 * @param val
	 * @param target
	 * @param replacement
	 */
	public static String replace(final String val, final String target,
			final String replacement) {
		if (isEmpty(val)) {
			return val;
		}
		return val.replace(target, replacement);
	}

	/**
	 * 文字列の正規表現での置換を行います
	 * 
	 * @param val
	 * @param target
	 * @param replacement
	 */
	public static String replaceAll(final String val, final String target,
			final String replacement) {
		if (isEmpty(val)) {
			return val;
		}
		return val.replaceAll(target, replacement);
	}

	public static String toString(final Collection<?> c) {
		final StringBuilder builder = new StringBuilder("{");
		boolean first = true;
		for (final Object obj : c) {
			if (!first) {
				builder.append(", ");
			}
			builder.append(obj);
			first = false;
		}
		builder.append("}");
		return builder.toString();
	}

	/**
	 * 数値の比較
	 * 
	 * @param a
	 * @param b
	 */
	public static int compare(final int a, final int b) {
		return a - b;
	}

	/**
	 * booleanの比較
	 * 
	 * @param a
	 * @param b
	 */
	public static int compare(final boolean a, final boolean b) {
		if (a == b) {
			return 0;
		} else {
			if (a) {
				return 1;
			}
		}
		return -1;
	}

	/**
	 * Comparableの比較をおこないます
	 * 
	 * @param a
	 * @param b
	 */
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public static int compare(final Comparable a, final Object b) {
		if (a == null) {
			if (b == null) {
				return 0;
			}
			return -1;
		} else {
			if (b == null) {
				return 1;
			}
		}
		return a.compareTo(b);
	}

	/**
	 * Comparableの比較をおこないます
	 * 
	 * @param a
	 * @param b
	 */
	@SuppressWarnings("rawtypes")
	public static int compare(final Object a, final Object b) {
		if (a == null) {
			if (b == null) {
				return 0;
			}
			return -1;
		} else {
			if (b == null) {
				return 1;
			}
		}
		if (a instanceof Comparable) {
			return compare((Comparable) a, b);
		}
		return compare(a.toString(), b);
	}

	/**
	 * 指定した文字、長さの文字列の取得
	 * 
	 * @param c
	 *             指定文字
	 * @param size
	 *            長さ
	 * @return 指定した文字、長さの文字列
	 */
	public static char[] getChars(final char c, final int size) {
		final char[] result = new char[size];
		for (int i = 0; i < size; i++) {
			result[i] = c;
		}
		return result;
	}

	/**
	 * 指定した文字、長さの文字列の取得
	 * 
	 * @param c
	 *             指定文字
	 * @param size
	 *            長さ
	 * @return 指定した文字、長さの文字列
	 */
	public static String getString(final char c, final int size) {
		final StringBuilder builder = new StringBuilder(size);
		for (int i = 0; i < size; i++) {
			builder.append(c);
		}
		return builder.toString();
	}

	/**
	 * 指定した文字、長さの文字列の取得
	 * 
	 * @param val
	 *             指定文字
	 * @param size
	 *            長さ
	 * @return 指定した文字、長さの文字列
	 */
	public static String getString(final String val, final int size) {
		final StringBuilder builder = new StringBuilder(val.length() * size);
		for (int i = 0; i < size; i++) {
			builder.append(val);
		}
		return builder.toString();
	}

	/**
	 * Timestampを現在時刻で新規作成
	 * 
	 */
	public static Timestamp newTimestamp() {
		final Timestamp ts = new Timestamp(new java.util.Date().getTime());
		return ts;
	}

	private static ConcurrentMap<String, Pattern> compiledPatternMap = concurrentMap();

	private static ConcurrentMap<String, ConcurrentMap<Integer, Pattern>> compiledOptionPatternMap = concurrentMap();

	/**
	 * コンパイル済の正規表現をキャッシュから取得します
	 * 
	 * @param regex
	 */
	public static Pattern getPattern(final String regex) {
		Pattern pattern = compiledPatternMap.get(regex);
		if (pattern != null) {
			return pattern;
		}
		pattern = Pattern.compile(regex);
		final Pattern oldPattern = compiledPatternMap.putIfAbsent(regex, pattern);
		return oldPattern == null ? pattern : oldPattern;
	}

	/**
	 * コンパイル済の正規表現をキャッシュから取得します
	 * 
	 * @param regex
	 * @param option
	 *            正規表現のコンパイルオプション
	 */
	public static Pattern getPattern(final String regex, final int option) {
		ConcurrentMap<Integer, Pattern> map = compiledOptionPatternMap
				.get(regex);
		if (map == null) {
			map = concurrentMap();
			final ConcurrentMap<Integer, Pattern> oldMap = compiledOptionPatternMap
					.putIfAbsent(regex, map);
			if (oldMap != null) {
				map = oldMap;
			}
		}
		final Integer intOp = Integer.valueOf(option);
		Pattern pattern = map.get(intOp);
		if (pattern == null) {
			pattern = Pattern.compile(regex, option);
			final Pattern oldPattern = map.putIfAbsent(intOp, pattern);
			if (oldPattern != null) {
				pattern = oldPattern;
			}
		}
		return pattern;
	}

	public static RuntimeException toRe(final Exception e) {
		return new RuntimeException(e);
	}

	/**
	 * 対応したラッパークラスを返します。
	 * 
	 * @param clazz
	 */
	public static Class<?> getWrapperClass(final Class<?> clazz) {
		return PRIMITIVE_WRAPPER_CLASS_MAP.get(clazz);
	}

	/**
	 * 文字列を大文字に変換します
	 * 
	 * @param val
	 */
	public static String toUpperCase(final String val) {
		if (val == null) {
			return val;
		}
		return val.toUpperCase();
	}

	/**
	 * 文字列を小文字に変換します
	 * 
	 * @param val
	 */
	public static String toLowerCase(final String val) {
		if (val == null) {
			return val;
		}
		return val.toLowerCase();
	}

	/**
	 * key1=value1;key2=value2;形式の文字列を解析して結果をマップで返します。
	 * 
	 * @param texts
	 *             解析対象のテキスト
	 * @return 結果を格納したマップ
	 */
	public static Map<String, String> parseKeyValue(final String... texts) {
		return parseKeyValue(";", "=", texts);
	}

	/**
	 * key1=value1;key2=value2;形式の文字列を解析して結果をマップで返します。
	 * 
	 * @param separator
	 *            パラメタのセパレータ
	 * @param keyValueSeparator
	 *            キーと値のセパレータ
	 * @param texts
	 */
	public static Map<String, String> parseKeyValue(final String separator,
			final String keyValueSeparator, final String... texts) {
		if (isEmpty(texts)) {
			return linkedMap(0);
		}
		final Map<String, String> result = linkedMap();
		for (final String text : texts) {
			final String[] splits = trim(text).split(separator);
			for (final String sp : splits) {
				final String[] keyValue = trim(sp).split(keyValueSeparator);
				if (keyValue.length > 1) {
					result.put(trim(keyValue[0]), trim(keyValue[1]));
				}
			}
		}
		return result;
	}

	/**
	 * 文字列を反対に並べ替えた結果を取得します。
	 * 
	 * @param value
	 */
	public static String reverse(final String value) {
		if (value == null || value.length() <= 1) {
			return value;
		}
		final StringBuilder builder = new StringBuilder(value.length());
		for (int i = value.length() - 1; i >= 0; i--) {
			builder.append(value.charAt(i));
		}
		return builder.toString();
	}

	/**
	 * バイト配列をコピーします
	 * 
	 * @param bytes
	 *            コピー元のバイト
	 */
	public static byte[] clone(final byte[] bytes) {
		if (bytes == null) {
			return null;
		}
		final byte[] cloneBytes = new byte[bytes.length];
		System.arraycopy(bytes, 0, cloneBytes, 0, bytes.length);
		return cloneBytes;
	}

	/**
	 * 先頭1文字を大文字にした結果を返します
	 * 
	 * @param value
	 *            変換対象の文字列
	 */
	public static String initCap(final String value) {
		if (value == null || value.length() == 0) {
			return null;
		}
		return value.substring(0, 1).toUpperCase() + value.substring(1);
	}

	private static final Map<String, Locale> LOCALE_CACHE = upperMap();
	
	static{
		final Locale[] locales=Locale.getAvailableLocales();
		for (final Locale locale : locales) {
			LOCALE_CACHE.put(locale.toString(), locale);
			LOCALE_CACHE.put(locale.toLanguageTag(), locale);
		}
		for (final Locale locale : locales) {
			if (!LOCALE_CACHE.containsKey(locale.getLanguage())) {
				LOCALE_CACHE.put(locale.getLanguage(), locale);
			}
		}
		for (final Locale locale : locales) {
			if ("no".equals(locale.toLanguageTag())) {
				LOCALE_CACHE.put("nb", locale);
			}
			if ("no-NO".equals(locale.toLanguageTag())) {
				LOCALE_CACHE.put("nb-NO", locale);
			}
		}
	}
	/**
	 * ロケールを取得します
	 * 
	 * @param text
	 *            ロケールの文字列
	 * @return ロケール
	 */
	public static Locale getLocale(final String text) {
		if (isEmpty(text)) {
			return null;
		}
		final Locale result = LOCALE_CACHE.get(text);
		if (result != null) {
			return result;
		}
		return null;
	}

	/**
	 * スレッドローカルをクリアします
	 */
	public static final void clear() {
		DateUtils.clear();
	}

	private static final Map<Class<?>, Map<Class<?>, Boolean>> ASSIGNABLE_FROM_CACHE = map();

	/**
	 * isAssignableFromの高速化版です
	 * 
	 * @param clazz1
	 * @param clazz2
	 */
	public static boolean isAssignableFrom(final Class<?> clazz1,
			final Class<?> clazz2) {
		Map<Class<?>, Boolean> map = ASSIGNABLE_FROM_CACHE.get(clazz1);
		if (map == null) {
			map = map();
			ASSIGNABLE_FROM_CACHE.put(clazz1, map);
		}
		final Boolean val = map.get(clazz2);
		if (val != null) {
			return val.booleanValue();
		}
		final boolean ret = clazz1.isAssignableFrom(clazz2);
		map.put(clazz2, ret);
		return ret;
	}

	/**
	 * 空のリストを取得します
	 * 
	 */
	@SuppressWarnings("unchecked")
	public static <T> List<T> emptyList() {
		return Collections.EMPTY_LIST;
	}

	/**
	 * 空のセットを取得します
	 * 
	 */
	@SuppressWarnings("unchecked")
	public static <T> Set<T> emptySet() {
		return Collections.EMPTY_SET;
	}

	/**
	 * 空のマップを取得します
	 * 
	 */
	@SuppressWarnings("unchecked")
	public static <S, T> Map<S, T> emptyMap() {
		return Collections.EMPTY_MAP;
	}

	/**
	 * べき乗を返します
	 * 
	 * @param val
	 * @param n
	 *            べき数
	 * @return べき乗した値
	 */
	private static int pow(final int val, final int n) {
		return (int) Math.pow(val, n);
	}

	/**
	 * べき乗を返します
	 * 
	 * @param val
	 * @param n
	 *            べき数
	 * @return べき乗した値
	 */
	private static long pow(final long val, final int n) {
		return (long) Math.pow(val, n);
	}

	/**
	 * セット型のANDを行った結果を返します
	 * 
	 * @param sets
	 *            ANDを行うSET型
	 */
	@SafeVarargs
	public static <T> Set<T> and(final Set<T>... sets) {
		if (isEmpty(sets)) {
			return set();
		}
		final Set<T> firstSet = first(sets);
		final Set<T> result = set(firstSet);
		for (final T obj : firstSet) {
			for (int i = 1; i < sets.length; i++) {
				final Set<T> set = sets[1];
				if (!set.contains(obj)) {
					result.remove(obj);
				}
			}
		}
		return result;
	}

	/**
	 * セット型のANDを行った結果を返します
	 * 
	 * @param set1
	 *            ANDを行うSET型
	 * @param set2
	 *            ANDを行うSET型
	 */
	public static <T> Set<T> and(final Set<T> set1, final Set<T> set2) {
		if (isEmpty(set1) || isEmpty(set2)) {
			return set();
		}
		final Set<T> result = set(set1);
		for (final T obj : set1) {
			if (!set2.contains(obj)) {
				result.remove(obj);
			}
		}
		return result;
	}

	/**
	 * 配列を指定した型の配列に変換します。
	 * 
	 * @param args
	 *            変換元の配列
	 * @param clazz
	 *            変換先の配列の型
	 * @return 変換先の配列
	 */
	@SuppressWarnings("unchecked")
	public static <T> T[] toArray(final Object args, final Class<T[]> clazz) {
		if (args == null) {
			return null;
		}
		if (args.getClass() == clazz) {
			return (T[]) args;
		}
		final int length = Array.getLength(args);
		final T[] ret = (T[]) Array.newInstance(clazz.getComponentType(), length);
		System.arraycopy(args, 0, ret, 0, length);
		return ret;
	}

	/**
	 * 文字列のsubstring結果を返します
	 * 
	 * @param text
	 *            対象の文字列
	 * @param beginIndex
	 *            開始位置
	 * @param endIndex
	 *            終了位置
	 * @return 文字列のsubstring結果
	 */
	public static String substring(final String text, final int beginIndex,
			final int endIndex) {
		if (text == null) {
			return null;
		}
		final int startPos = beginIndex;
		int endPos = endIndex;
		if (beginIndex >= endIndex) {
			return null;
		}
		if (text.length() <= endIndex) {
			endPos = text.length();
		}
		return text.substring(startPos, endPos);
	}
	
	/**
	 * 対応するプリミティブクラスを取得します。
	 * @param clazz
	 * @return プリミティブクラス
	 */
	public static Class<?> getPrimitiveClass(final Class<?> clazz){
		return PRIMITIVE_WRAPPER_CLASS_MAP.get(clazz);
	}

}