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

import java.io.InputStream;
import java.io.OutputStream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * JSON変換用のユーティリティクラス
 * 
 * @author tatsuo satoh
 * 
 */
public class JsonUtils {
	protected static final Logger LOGGER = LogManager
			.getLogger(JsonUtils.class);

	/**
	 * JSON(UTC日付)変換用のインスタンス
	 */
	private static AbstractJsonConverter<?> utcJsonConverter;

	static {
		utcJsonConverter = new JsonConverter();
	}

	/**
	 * 内部で使用しているJacksonのオブジェクトマッパーを返します
	 * 
	 * @return Jacksonのオブジェクトマッパー
	 */
	@SuppressWarnings("unchecked")
	public static <T> T getObjectMapper() {
		return (T) getJsonConverter().getObjectMapper();
	}

	/**
	 * 既定のJsonコンバーターを取得します
	 * 
	 * @return Jsonコンバーター
	 */
	@SuppressWarnings("unchecked")
	public static <T extends AbstractJsonConverter<?>> T getJsonConverter() {
		return (T) utcJsonConverter;
	}

	/**
	 * オブジェクトからJSON文字列に変換します
	 * 
	 * @param value
	 *            変換前のオブジェクト
	 * @return 変換後のJSON文字列
	 */
	public static String toJsonString(Object value) {
		return getJsonConverter().toJsonString(value);
	}

	/**
	 * JSON文字列からオブジェクトに変換します
	 * 
	 * @param value
	 *            変換前のJSON文字列
	 * @param clazz
	 *            変換後のクラス
	 * @return 変換後のオブジェクト
	 */
	public static <T> T fromJsonString(String value, Class<T> clazz) {
		return getJsonConverter().fromJsonString(value, clazz);
	}

	/**
	 * InputStreamからオブジェクトに変換します
	 * 
	 * @param value
	 *            変換前のJSON文字列を含むストリーム
	 * @param clazz
	 *            変換後のクラス
	 * @return 変換後のオブジェクト
	 */
	public static <T> T fromJsonString(InputStream value, Class<T> clazz) {
		return getJsonConverter().fromJsonString(value, clazz);
	}

	/**
	 * オブジェクトをJSON文字列にしてストリームに書き込みます
	 * 
	 * @param ostream
	 * @param value
	 */
	public static void writeJsonValue(OutputStream ostream, Object value) {
		getJsonConverter().writeJsonValue(ostream, value);
	}

}
