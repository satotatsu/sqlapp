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

package com.sqlapp.thread;

import java.io.Serializable;
import java.util.Iterator;
import java.util.Map;

import com.sqlapp.data.converter.Converters;
import com.sqlapp.util.CommonUtils;

/**
 * Thread の開始～終了までコンテキストを保持するクラス.
 * 内部では、{@link org.apache.logging.log4j.ThreadContext} にコンテキスト保持責務を委譲する.
 * スレッドプールを使用するシステムではスレッド状態が他の処理に伝染する可能性があるため、
 * スレッドプールからの取り出し～アプリケーションで使用するまでに間に初期化をする必要がある。
 * また、メモリ的な観点からスレッドプールに返却される直前にコンテキストの開放を行った方が望ましい。
 * 
 * @author tatsuo satoh
 */
public class ThreadContext implements Serializable {

	private static final long serialVersionUID = 7286837483883803285L;
	// オブジェクト入出力操作用ハンドラ
	private static ObjectHandler objectHandler = new ObjectHandler();
	// 文字列入出力用のハンドラー
	private static StringParameterHandler stringParameterHandler = new StringParameterHandler();

	/**
	 * スレッドローカルでオブジェクト格納用のキャッシュ
	 */
	private static final ThreadLocal<Map<Object, Object>> THREAD_LOCAL_OBJECT_MAP = new ThreadLocal<Map<Object, Object>>() {
		@Override
		protected Map<Object, Object> initialValue() {
			return CommonUtils.map();
		}
	};

	/**
	 * コンテキスト情報を、以下の手順にて初期化する.
	 * 各種情報にアクセスする前には、必ず呼ばれければならない。
	 */
	public static void init() {
		release();
		THREAD_LOCAL_OBJECT_MAP.remove();
	}

	/**
	 * コンテキスト情報を、以下の手順にて初期化する.
	 * 各種情報にアクセスする前には、必ず呼ばれければならない。
	 * 
	 * @param mdcContext
	 *            初期化するMDCコンテキスト
	 * @param context
	 *            初期化するコンテキスト
	 */
	@SuppressWarnings("rawtypes")
	public static void init(final Map mdcContext, final Map<Object, Object> context) {
		release();
		if (mdcContext != null) {
			final Iterator itr = mdcContext.entrySet().iterator();
			while (itr.hasNext()) {
				final Map.Entry entry = (Map.Entry) itr.next();
				org.apache.logging.log4j.ThreadContext.put(entry.getKey().toString(), entry.getValue().toString());
			}
		}
		THREAD_LOCAL_OBJECT_MAP.remove();
		if (context != null) {
			THREAD_LOCAL_OBJECT_MAP.get().putAll(context);
		}
	}

	/**
	 * コンテキスト情報をクリアする。
	 */
	public static void release() {
		THREAD_LOCAL_OBJECT_MAP.remove();
		org.apache.logging.log4j.ThreadContext.clearAll();
	}

	/**
	 * MDCに保存されている全オブジェクトを取得します
	 * 
	 * @return MDCに保存されている全オブジェクト
	 */
	@SuppressWarnings("rawtypes")
	public static Map getMdcContext() {
		return org.apache.logging.log4j.ThreadContext.getContext();
	}

	/**
	 * MDCに値を設定します。 通常はこのメソッドではなく、それぞれのsetメソッドを使用してください。
	 * 
	 * @param key
	 * @param value
	 */
	public void setMDC(final String key, final String value) {
		org.apache.logging.log4j.ThreadContext.put(key, value);
	}

	/**
	 * MDCから値を設定します。 通常はこのメソッドではなく、それぞれのgetメソッドを使用してください。
	 * 
	 * @param key
	 * @return 指定したキーに対応した値
	 */
	public static String getMDC(final String key) {
		return org.apache.logging.log4j.ThreadContext.get(key);
	}

	/**
	 * コンテキストに保存されている全オブジェクトを取得します
	 * 
	 * @return コンテキストに保存されている全オブジェクト
	 */
	public static Map<Object, Object> getContext() {
		return THREAD_LOCAL_OBJECT_MAP.get();
	}

	/**
	 * コンテキストに値を保存する.（オブジェクト）
	 * 保存する際のキー名は、オブジェクトのFQCNとなる。
	 * 
	 * @param value
	 *            保存する値
	 */
	public static void setObject(final Object value) {
		objectHandler.setObject(value);
	}

	/**
	 * コンテキストから値を削除する.（オブジェクト）
	 * 
	 * @param key
	 *            保存する値
	 */
	public static void removeObject(final Object key) {
		objectHandler.removeObject(key);
	}

	/**
	 * コンテキストに値を保存する.（オブジェクト）
	 * 
	 * @param key
	 *            保存するキー
	 * @param value
	 *            保存する値
	 */
	public static void setObject(final Object key, final Object value) {
		objectHandler.setObject(key, value);
	}

	/**
	 * 指定されたクラスに紐づく、コンテキストの値を取得する.（オブジェクト）
	 * 取得する際のキー名は、オブジェクトのFQCNとなる。
	 * 
	 * @param clazz
	 *            返却値の型
	 * @return 指定されたクラスに紐づく、コンテキストの値
	 */
	public static <T> T getObject(final Class<T> clazz) {
		return objectHandler.getObject(clazz);
	}

	/**
	 * 指定されたキーに紐づく、コンテキストの値を取得する.（オブジェクト）
	 * 
	 * @param key
	 *            返却値の型
	 * @return 指定されたクラスに紐づく、コンテキストの値
	 */
	public static <T> T getObject(final Object key) {
		return objectHandler.getObject(key);
	}

	/**
	 * コンテキストに[SQL]を設定します。
	 * 
	 * @param sql
	 *            SQL
	 */
	public static void setSql(final String sql) {
		set("sql", sql);
	}

	/**
	 * コンテキストに保存されている[SQL]を返却します。
	 * 
	 * @return SQL
	 */
	public static String getSql() {
		return get("sql");
	}

	/**
	 * コンテキストに、指定の属性で値を保存する.（文字列）
	 * 
	 * @param attribute
	 *            保存対象の属性
	 * @param value
	 *            保存する値
	 */
	protected static void set(final String attribute, final String value) {
		getStringParameterHandler().set(attribute, value);
	}

	/**
	 * 指定された属性に紐づく、コンテキストの値を取得する.（文字列）
	 * 
	 * @param attribute
	 *            コンテキストの属性
	 * @return 指定された属性に紐づく、コンテキストの値
	 */
	protected static String get(final String attribute) {
		return getStringParameterHandler().get(attribute);
	}

	/**
	 * コンテキストに[SQL_PROCESS_TIME]を設定します。
	 * 
	 * @param sqlProcessTime
	 *            処理時間(ミリ秒)
	 */
	public static void setSqlProcessTime(final Long sqlProcessTime) {
		if (sqlProcessTime != null) {
			set("sqlProcessTime", "" + sqlProcessTime);
		} else {
			set("sqlProcessTime", null);
		}
	}

	/**
	 * コンテキストに保存されている[SQL_PROCESS_TIME]を返却します。
	 * 
	 * @return SQL
	 */
	public static Long getSqlProcessTime() {
		return Converters.getDefault().convertObject(get("sqlProcessTime"),
				Long.class);
	}

	/**
	 * 文字列パラメター用のハンドラー
	 * 
	 * @author tatsuo satoh
	 * 
	 */
	public static class StringParameterHandler {

		/**
		 * スレッドコンテキストから値を取得します
		 * 
		 * @param key
		 * @return 取得した値
		 */
		public String get(final String key) {
			return org.apache.logging.log4j.ThreadContext.get(key);
		}

		/**
		 * スレッドコンテキストへ値を取得します
		 * 
		 * @param key
		 *            設定するキー
		 * @param value
		 *            設定する値
		 */
		public void set(final String key, final String value) {
			if (value == null) {
				org.apache.logging.log4j.ThreadContext.remove(key);
			} else {
				org.apache.logging.log4j.ThreadContext.put(key, value);
			}
		}
	}

	/**
	 * オブジェクト用のハンドラー
	 * 
	 * @author tatsuo satoh
	 * 
	 */
	public static class ObjectHandler {

		/**
		 * 指定されたクラスに紐づく、コンテキストの値を取得する.（オブジェクト）
		 * 取得する際のキー名は、オブジェクトのFQCNとなる。
		 * 
		 * @param clazz
		 *            返却値の型
		 * @return 指定されたクラスに紐づく、コンテキストの値
		 */
		public <T> T getObject(final Class<T> clazz) {
			return clazz.cast(getContext().get(clazz));
		}

		/**
		 * 指定されたキーに紐づく、コンテキストの値を取得する.（オブジェクト）
		 * 
		 * @param key
		 *            キー
		 * @return 指定されたキーに紐づく、コンテキストの値
		 */
		@SuppressWarnings("unchecked")
		public <T> T getObject(final Object key) {
			return (T) getContext().get(key);
		}

		/**
		 * コンテキストに値を保存する.（オブジェクト）
		 * 保存する際のキー名は、オブジェクトのFQCNとなる。
		 * 
		 * @param value
		 *            保存する値
		 */
		public void setObject(final Object value) {
			if (value == null) {
				return;
			}
			getContext().put(value.getClass(), value);
		}

		/**
		 * コンテキストに値を保存する.（オブジェクト）
		 * 保存する際のキー名は、オブジェクトのFQCNとなる。
		 * 
		 * @param key
		 *            キー
		 * @param value
		 *            保存する値
		 */
		public void setObject(final Object key, final Object value) {
			getContext().put(key, value);
		}

		/**
		 * コンテキストに保存されたオブジェクトを削除する.（オブジェクト）
		 * 
		 * @param key
		 *            キー
		 */
		public void removeObject(final Object key) {
			getContext().remove(key);
		}

	}

	/**
	 * @param objectHandler
	 *            the objectHandler to set
	 */
	public static void setObjectHandler(final ObjectHandler objectHandler) {
		ThreadContext.objectHandler = objectHandler;
	}

	/**
	 * @return the objectHandler
	 */
	public static ObjectHandler getObjectHandler() {
		return objectHandler;
	}

	/**
	 * @return the stringParameterHandler
	 */
	public static StringParameterHandler getStringParameterHandler() {
		return stringParameterHandler;
	}

	/**
	 * @param stringParameterHandler
	 *            the stringParameterHandler to set
	 */
	public static void setStringParameterHandler(
			final StringParameterHandler stringParameterHandler) {
		ThreadContext.stringParameterHandler = stringParameterHandler;
	}

}
