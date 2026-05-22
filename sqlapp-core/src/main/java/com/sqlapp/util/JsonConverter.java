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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.Calendar;
import java.util.Date;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;
import tools.jackson.core.Version;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.cfg.MapperBuilder;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;

/**
 * JSON変換用のユーティリティクラス(Jackson 2.X系用)
 * 
 * @author tatsuo satoh
 * 
 */
public class JsonConverter extends AbstractJsonConverter<ObjectMapper> implements Cloneable {
	protected static final Logger LOGGER = LogManager.getLogger(JsonConverter.class);

	protected ObjectMapper objectMapper;

	/** 未知のプロパティをエラーにする。(デフォル:エラーにしない) */
	private boolean failOnUnknownProperties = false;

	/** DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT */
	private boolean acceptEmptyStringAsNullObject = true;

	/**
	 * コンストラクタ
	 */
	public JsonConverter() {
		final MapperBuilder<?, ?> builder = createObjectMapper();
		initialize(builder);
		objectMapper = builder.build();
	};

	/**
	 * @param utc UTCで送信
	 */
	public JsonConverter(final boolean utc) {
		super(utc);
		final MapperBuilder<?, ?> builder = createObjectMapper();
		initialize(builder);
	};

	protected MapperBuilder<?, ?> createObjectMapper() {
		return JsonMapper.builder();
	}

	protected void initialize(final MapperBuilder<?, ?> mapperBuilder) {
		final SimpleModule instantModule = new SimpleModule("InstantModule", new Version(1, 0, 0, null, null, null))
				.addSerializer(Instant.class, new InstantSerializer())
				.addDeserializer(Instant.class, new InstantDeserializer());
		mapperBuilder.addModule(instantModule);
		//
		final SimpleModule localDateTimeModule = new SimpleModule("LocalDateTimeModule",
				new Version(1, 0, 0, null, null, null))
				.addSerializer(LocalDateTime.class, new LocalDateTimeSerializer())
				.addDeserializer(LocalDateTime.class, new LocalDateTimeDeserializer());
		mapperBuilder.addModule(localDateTimeModule);
		//
		final SimpleModule offsetDateTimeModule = new SimpleModule("offsetDateTimeModule",
				new Version(1, 0, 0, null, null, null))
				.addSerializer(OffsetDateTime.class, new OffsetDateTimeSerializer())
				.addDeserializer(OffsetDateTime.class, new OffsetDateTimeDeserializer());
		mapperBuilder.addModule(offsetDateTimeModule);
		//
		final SimpleModule zonedDateTimeModule = new SimpleModule("zonedDateTimeModule",
				new Version(1, 0, 0, null, null, null))
				.addSerializer(ZonedDateTime.class, new ZonedDateTimeSerializer())
				.addDeserializer(ZonedDateTime.class, new ZonedDateTimeDeserializer());
		mapperBuilder.addModule(zonedDateTimeModule);
		//
		final SimpleModule calendarModule = new SimpleModule("CalendarModule", new Version(1, 0, 0, null, null, null))
				.addSerializer(Calendar.class, new CalendarSerializer())
				.addDeserializer(Calendar.class, new CalendarDeserializer());
		mapperBuilder.addModule(calendarModule);
		//
		final SimpleModule dateModule = new SimpleModule("DateModule", new Version(1, 0, 0, null, null, null))
				.addSerializer(Date.class, new DateSerializer()).addDeserializer(Date.class, new DateDeserializer());
		mapperBuilder.addModule(dateModule);
		//
		mapperBuilder.configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, false);
		//
		setIndentOutput(mapperBuilder, indentOutput);
		objectMapper = mapperBuilder.build();
	}

	/**
	 * Date専用のシリアライザ
	 * 
	 * @author tatsuo satoh
	 * 
	 */
	class DateSerializer extends ValueSerializer<Date> {

		@Override
		public void serialize(Date value, JsonGenerator gen, SerializationContext ctxt) throws JacksonException {
			final String text = format(value);
			gen.writeString(text);
		}
	}

	/**
	 * Date専用のデシリアライザ
	 * 
	 * @author tatsuo satoh
	 * 
	 */
	static class DateDeserializer extends ValueDeserializer<Date> {
		@Override
		public Date deserialize(final JsonParser jsonParser, final DeserializationContext paramDeserializationContext) {
			if (jsonParser.getString() == null || jsonParser.getString().length() == 0) {
				return null;
			}
			final Date date = toDate(jsonParser.getString());
			return date;
		}
	}

	/**
	 * Calendar専用のシリアライザ
	 * 
	 * @author tatsuo satoh
	 * 
	 */
	class CalendarSerializer extends ValueSerializer<Calendar> {

		@Override
		public void serialize(Calendar value, JsonGenerator gen, SerializationContext ctxt) throws JacksonException {
			gen.writeString(format(value));
		}
	}

	/**
	 * Calendar専用のデシリアライザ
	 * 
	 * @author tatsuo satoh
	 * 
	 */
	static class CalendarDeserializer extends ValueDeserializer<Calendar> {
		@Override
		public Calendar deserialize(final JsonParser jsonParser,
				final DeserializationContext paramDeserializationContext) {
			if (jsonParser.getString() == null || jsonParser.getString().length() == 0) {
				return null;
			}
			return toCalendar(jsonParser.getString());
		}
	}

	/**
	 * Instant専用のシリアライザ
	 * 
	 * @author tatsuo satoh
	 * 
	 */
	class InstantSerializer extends ValueSerializer<Instant> {

		@Override
		public void serialize(Instant value, JsonGenerator gen, SerializationContext ctxt) throws JacksonException {
			gen.writeString(format(value));
		}
	}

	/**
	 * Instant専用のデシリアライザ
	 * 
	 * @author tatsuo satoh
	 * 
	 */
	static class InstantDeserializer extends ValueDeserializer<Instant> {

		@Override
		public Instant deserialize(final JsonParser jsonParser,
				final DeserializationContext paramDeserializationContext) {
			if (jsonParser.getString() == null || jsonParser.getString().length() == 0) {
				return null;
			}
			final ZonedDateTime dateTime = toDateTime(jsonParser.getString());
			return dateTime.toInstant();
		}
	}

	/**
	 * LocalDateTime専用のシリアライザ
	 * 
	 * @author tatsuo satoh
	 * 
	 */
	class LocalDateTimeSerializer extends ValueSerializer<LocalDateTime> {

		@Override
		public void serialize(LocalDateTime value, JsonGenerator gen, SerializationContext ctxt)
				throws JacksonException {
			gen.writeString(format(value));
		}
	}

	/**
	 * LocalDateTime専用のデシリアライザ
	 * 
	 * @author tatsuo satoh
	 * 
	 */
	static class LocalDateTimeDeserializer extends ValueDeserializer<LocalDateTime> {

		@Override
		public LocalDateTime deserialize(final JsonParser jsonParser,
				final DeserializationContext paramDeserializationContext) {
			if (jsonParser.getString() == null || jsonParser.getString().length() == 0) {
				return null;
			}
			final ZonedDateTime dateTime = toDateTime(jsonParser.getString());
			return dateTime.toLocalDateTime();
		}
	}

	/**
	 * LocalDateTime専用のシリアライザ
	 * 
	 * @author tatsuo satoh
	 * 
	 */
	class OffsetDateTimeSerializer extends ValueSerializer<OffsetDateTime> {

		@Override
		public void serialize(OffsetDateTime value, JsonGenerator gen, SerializationContext ctxt)
				throws JacksonException {
			gen.writeString(format(value));
		}
	}

	/**
	 * LocalDateTime専用のデシリアライザ
	 * 
	 * @author tatsuo satoh
	 * 
	 */
	static class OffsetDateTimeDeserializer extends ValueDeserializer<OffsetDateTime> {

		@Override
		public OffsetDateTime deserialize(final JsonParser jsonParser,
				final DeserializationContext paramDeserializationContext) {
			if (jsonParser.getString() == null || jsonParser.getString().length() == 0) {
				return null;
			}
			final ZonedDateTime dateTime = toDateTime(jsonParser.getString());
			return dateTime.toOffsetDateTime();
		}
	}

	/**
	 * LocalDateTime専用のシリアライザ
	 * 
	 * @author tatsuo satoh
	 * 
	 */
	class ZonedDateTimeSerializer extends ValueSerializer<ZonedDateTime> {

		@Override
		public void serialize(ZonedDateTime value, JsonGenerator gen, SerializationContext ctxt)
				throws JacksonException {
			gen.writeString(format(value));
		}
	}

	/**
	 * LocalDateTime専用のデシリアライザ
	 * 
	 * @author tatsuo satoh
	 * 
	 */
	static class ZonedDateTimeDeserializer extends ValueDeserializer<ZonedDateTime> {

		@Override
		public ZonedDateTime deserialize(final JsonParser jsonParser,
				final DeserializationContext paramDeserializationContext) {
			if (jsonParser.getString() == null || jsonParser.getString().length() == 0) {
				return null;
			}
			final ZonedDateTime dateTime = toDateTime(jsonParser.getString());
			return dateTime;
		}
	}

	/**
	 * 内部で使用しているJacksonのオブジェクトマッパーを返します
	 * 
	 * @return Jacksonのオブジェクトマッパー
	 */
	@Override
	public ObjectMapper getObjectMapper() {
		return objectMapper;
	}

	/**
	 * オブジェクトからJSON文字列に変換します
	 * 
	 * @param value 変換前のオブジェクト
	 * @return 変換後のJSON文字列
	 */
	@Override
	public String toJsonString(final Object value) {
		try {
			final String result = getObjectMapper().writeValueAsString(value);
			if (LOGGER.isTraceEnabled()) {
				LOGGER.trace("value=" + value);
			}
			return result;
		} catch (final JacksonException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * JSON文字列からオブジェクトに変換します
	 * 
	 * @param value 変換前のJSON文字列
	 * @param clazz 変換後のクラス
	 * @return 変換後のオブジェクト
	 */
	@Override
	public <S> S fromJsonString(final String value, final Class<S> clazz) {
		try {
			if (LOGGER.isTraceEnabled()) {
				LOGGER.trace("value=" + value);
			}
			return getObjectMapper().readValue(value, clazz);
		} catch (final JacksonException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * JSON文字列からオブジェクトに変換します
	 * 
	 * @param value 変換前のJSON文字列
	 * @param type  変換後のクラス
	 * @return 変換後のオブジェクト
	 */
	@Override
	public <S> S fromJsonString(final String value, final TypeReference<S> type) {
		try {
			if (LOGGER.isTraceEnabled()) {
				LOGGER.trace("value=" + value);
			}
			return getObjectMapper().readValue(value, type);
		} catch (final JacksonException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * InputStreamからオブジェクトに変換します
	 * 
	 * @param value 変換前のJSON文字列を含むストリーム
	 * @param clazz 変換後のクラス
	 * @return 変換後のオブジェクト
	 */
	@Override
	public <T> T fromJsonString(final InputStream value, final Class<T> clazz) {
		try {
			if (LOGGER.isTraceEnabled()) {
				final String val = inputStreamToString(value);
				return fromJsonString(val, clazz);
			} else {
				return getObjectMapper().readValue(value, clazz);
			}
		} catch (final JacksonException e) {
			throw new RuntimeException(e);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * InputStreamからオブジェクトに変換します
	 * 
	 * @param value 変換前のJSON文字列を含むストリーム
	 * @param type  変換後のクラス
	 * @return 変換後のオブジェクト
	 */
	@Override
	public <T> T fromJsonString(final InputStream value, final TypeReference<T> type) {
		try {
			if (LOGGER.isTraceEnabled()) {
				final String val = inputStreamToString(value);
				return fromJsonString(val, type);
			} else {
				return getObjectMapper().readValue(value, type);
			}
		} catch (final JacksonException e) {
			throw new RuntimeException(e);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.util.AbstractJsonConverter#fromJsonString(java.io.File,
	 * java.lang.Class)
	 */
	@Override
	public <T> T fromJsonString(final File file, final TypeReference<T> clazz) {
		try {
			return getObjectMapper().readValue(file, clazz);
		} catch (final JacksonException e) {
			throw new RuntimeException(e);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.util.AbstractJsonConverter#fromJsonString(java.io.File,
	 * java.lang.Class)
	 */
	@Override
	public <T> T fromJsonString(final File file, final Class<T> clazz) {
		try {
			return getObjectMapper().readValue(file, clazz);
		} catch (final JacksonException e) {
			throw new RuntimeException(e);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.sqlapp.util.AbstractJsonConverter#writeJsonValue(java.io.OutputStream,
	 * java.lang.Object)
	 */
	@Override
	public void writeJsonValue(final OutputStream ostream, final Object value) {
		try {
			getObjectMapper().writeValue(ostream, value);
		} catch (final JacksonException e) {
			throw new RuntimeException(e);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.util.AbstractJsonConverter#writeJsonValue(java.io.File,
	 * java.lang.Object)
	 */
	@Override
	public void writeJsonValue(final File file, final Object value) {
		try {
			getObjectMapper().writeValue(file, value);
		} catch (final JacksonException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * @param failOnUnknownProperties the failOnUnknownProperties to set
	 */
	@Override
	public void setFailOnUnknownProperties(final boolean failOnUnknownProperties) {
		this.failOnUnknownProperties = failOnUnknownProperties;
		final MapperBuilder<?, ?> builder = objectMapper.rebuild();
		setFailOnUnknownProperties(builder, failOnUnknownProperties);
		this.objectMapper = builder.build();
	}

	private void setFailOnUnknownProperties(final MapperBuilder<?, ?> builder, final boolean failOnUnknownProperties) {
		if (indentOutput) {
			builder.enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
		} else {
			builder.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
		}
	}

	private boolean indentOutput;

	private void setIndentOutput(final MapperBuilder<?, ?> builder, final boolean indentOutput) {
		if (indentOutput) {
			builder.enable(SerializationFeature.INDENT_OUTPUT);
		} else {
			builder.disable(SerializationFeature.INDENT_OUTPUT);
		}
	}

	public void setIndentOutput(final boolean indentOutput) {
		this.indentOutput = indentOutput;
		final MapperBuilder<?, ?> builder = objectMapper.rebuild();
		setIndentOutput(builder, indentOutput);
		this.objectMapper = builder.build();
	}

	/**
	 * @return the failOnUnknownProperties
	 */
	public boolean isFailOnUnknownProperties() {
		return failOnUnknownProperties;
	}

	@Override
	public JsonConverter clone() {
		try {
			return (JsonConverter) super.clone();
		} catch (CloneNotSupportedException e) {
			throw new RuntimeException(e);
		}
	}
}
