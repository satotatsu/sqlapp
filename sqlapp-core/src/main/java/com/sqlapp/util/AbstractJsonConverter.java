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

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.Temporal;
import java.util.Calendar;
import java.util.Date;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.fasterxml.jackson.core.type.TypeReference;
import com.sqlapp.data.converter.Converter;
import com.sqlapp.data.converter.Converters;

/**
 * JSON変換用のユーティリティクラス
 * 
 * @author tatsuo satoh
 * 
 * @param <T>
 *            JacksonのObjectMapper型
 */
public abstract class AbstractJsonConverter<T> {
	protected static final Logger LOGGER = LogManager
			.getLogger(AbstractJsonConverter.class);

	private boolean utc = true;

	/**
	 * コンストラクタ
	 */
	public AbstractJsonConverter() {
	};

	/**
	 * @param utc
	 *            UTCで送信
	 */
	public AbstractJsonConverter(final boolean utc) {
		this.utc = utc;
	};

	protected static final Converter<ZonedDateTime> converter = Converters.createDefaultZonedDateTimeConverter()
			.setFormat(DateTimeFormatter.ISO_INSTANT);

	private static final ZoneId UTC_ZONE_ID=ZoneId.of("Z");
	
	private static ZonedDateTime toUtc(final ZonedDateTime dateTime) {
		return dateTime.withZoneSameInstant(UTC_ZONE_ID);
	}
	
	private static Converters converters=Converters.getDefault();

	/**
	 * JacksonのObjectMapperを取得します
	 * 
	 * @return ObjectMapper
	 */
	public abstract T getObjectMapper();

	/**
	 * 日付を文字列形式の文字列に変換して返します
	 * 
	 * @param dateTime
	 * @return 文字列変換した日付
	 */
	protected String formatUtc(final ZonedDateTime dateTime) {
		if (utc) {
			return converter.convertString(toUtc(dateTime));
		} else {
			return converter.convertString(dateTime);
		}
	}

	/**
	 * 日付を文字列形式の文字列に変換して返します
	 * 
	 * @param dateTime
	 * @return 文字列変換した日付
	 */
	protected String format(final ZonedDateTime dateTime) {
		return converter.convertString(dateTime);
	}

	/**
	 * 日付を文字列形式の文字列に変換して返します
	 * 
	 * @param date
	 * @return 日付の文字列形式
	 */
	protected String format(final Date date) {
		final ZonedDateTime dateTime = converters.convertObject(date, ZonedDateTime.class);
		return formatUtc(dateTime);
	}
	
	/**
	 * 日付を文字列形式の文字列に変換して返します
	 * 
	 * @param date
	 * @return 日付の文字列形式
	 */
	protected <S extends Temporal> String format(final S date) {
		final ZonedDateTime dateTime = converters.convertObject(date, ZonedDateTime.class);
		return formatUtc(dateTime);
	}

	/**
	 * カレンダーを文字列形式の文字列に変換して返します
	 * 
	 * @param calendar
	 * @return カレンダーの文字列形式
	 */
	protected String format(final Calendar calendar) {
		final ZonedDateTime dateTime = converters.convertObject(calendar, ZonedDateTime.class);
		return format(dateTime);
	}

	/**
	 * 文字列を解析して日付オブジェクトを返します
	 * 
	 * @param text
	 *            日付の文字列
	 * @return 日付
	 */
	protected static Date toDate(final String text) {
		if (text == null) {
			return null;
		}
		final ZonedDateTime dateTime = toDateTime(text);
		final Date date = converters.convertObject(dateTime, Date.class);
		return date;
	}

	/**
	 * 文字列を解析してカレンダーオブジェクトを返します
	 * 
	 * @param text
	 *            日付の文字列
	 * @return カレンダー
	 */
	protected static Calendar toCalendar(final String text) {
		if (text == null) {
			return null;
		}
		final ZonedDateTime dateTime = toDateTime(text);
		final Calendar date = converters.convertObject(dateTime, Calendar.class);
		return date;
	}

	/**
	 * 文字列を指定した日付形式で解析してDateTimeを返します
	 * 
	 * @param text
	 * @return DateTime
	 */
	protected static ZonedDateTime toDateTime(final String text) {
		return converter.convertObject(text);
	}

	/**
	 * オブジェクトからJSON文字列に変換します
	 * 
	 * @param value
	 *            変換前のオブジェクト
	 * @return 変換後のJSON文字列
	 */
	public abstract String toJsonString(Object value);

	/**
	 * JSON文字列からオブジェクトに変換します
	 * 
	 * @param value
	 *            変換前のJSON文字列
	 * @param clazz
	 *            変換後のクラス
	 * @return 変換後のオブジェクト
	 */
	public abstract <S> S fromJsonString(final String value, final Class<S> clazz);

	/**
	 * InputStreamからオブジェクトに変換します
	 * 
	 * @param value
	 *            変換前のJSON文字列を含むストリーム
	 * @param type
	 *            変換後のクラス
	 * @return 変換後のオブジェクト
	 */
	public abstract <S> S fromJsonString(final InputStream value, final TypeReference<S> type);


	/**
	 * JSON文字列からオブジェクトに変換します
	 * 
	 * @param value
	 *            変換前のJSON文字列
	 * @param type
	 *            変換後のクラス
	 * @return 変換後のオブジェクト
	 */
	public abstract <S> S fromJsonString(final String value, final TypeReference<S> type);

	/**
	 * InputStreamからオブジェクトに変換します
	 * 
	 * @param value
	 *            変換前のJSON文字列を含むストリーム
	 * @param clazz
	 *            変換後のクラス
	 * @return 変換後のオブジェクト
	 */
	public abstract <S> S fromJsonString(final InputStream value, final Class<S> clazz);
	
	/**
	 * Fileからオブジェクトに変換します
	 * 
	 * @param file
	 *            ファイル
	 * @param clazz
	 *            変換後のクラス
	 * @return 変換後のオブジェクト
	 */
	public abstract <S> S fromJsonString(final File file, final Class<S> clazz);
	
	/**
	 * Fileからオブジェクトに変換します
	 * 
	 * @param file
	 *            ファイル
	 * @param type
	 *            変換後の型の参照
	 * @return 変換後のオブジェクト
	 */
	public abstract <S> S fromJsonString(final File file, final TypeReference<S> type);

	protected String inputStreamToString(final InputStream in) throws IOException {
		try(BufferedReader reader = new BufferedReader(new InputStreamReader(in,
				"UTF-8"));){
			final StringBuilder buf = new StringBuilder(128);
			String str = reader.readLine();
			if (str != null) {
				buf.append(str);
				while ((str = reader.readLine()) != null) {
					buf.append("\n");
					buf.append(str);
				}
			}
			return buf.toString();
		}
	}

	protected boolean isUtc() {
		return utc;
	}

	/**
	 * オブジェクトをJSON文字列にしてストリームに書き込みます
	 * 
	 * @param ostream
	 * @param value
	 */
	public abstract void writeJsonValue(OutputStream ostream, Object value);
	/**
	 * オブジェクトをJSON文字列にしてファイルに書き込みます
	 * 
	 * @param file
	 * @param value
	 */
	public abstract void writeJsonValue(File file, Object value);

	/**
	 * @param failOnUnknownProperties
	 *            the failOnUnknownProperties to set
	 */
	public abstract void setFailOnUnknownProperties(
			boolean failOnUnknownProperties);
}
