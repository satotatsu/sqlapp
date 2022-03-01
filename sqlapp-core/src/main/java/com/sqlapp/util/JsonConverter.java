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
import java.util.HashMap;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.DeserializationConfig;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.deser.Deserializers;
import com.fasterxml.jackson.databind.deser.std.EnumDeserializer;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.util.EnumResolver;
import com.sqlapp.data.converter.EnumConvertable;

/**
 * JSON変換用のユーティリティクラス(Jackson 2.X系用)
 * 
 * @author tatsuo satoh
 * 
 */
public class JsonConverter extends AbstractJsonConverter<ObjectMapper> implements Cloneable{
	protected static final Logger LOGGER = LogManager.getLogger(JsonConverter.class);

	protected final ObjectMapper objectMapper;

	/** 未知のプロパティをエラーにする。(デフォル:エラーにしない) */
	private boolean failOnUnknownProperties = false;

	/**
	 * コンストラクタ
	 */
	public JsonConverter() {
		objectMapper = createObjectMapper();
		initialize(objectMapper);
	};

	/**
	 * @param utc
	 *          UTCで送信
	 */
	public JsonConverter(final boolean utc) {
		super(utc);
		objectMapper = createObjectMapper();
		initialize(objectMapper);
	};
	
	protected ObjectMapper createObjectMapper() {
		return new ObjectMapper();
	}

	protected void initialize(final ObjectMapper objectMapper) {
		initializeEnum();
		final SimpleModule instantModule = new SimpleModule("InstantModule",
				new Version(1, 0, 0, null, null, null)).addSerializer(
				Instant.class, new InstantSerializer()).addDeserializer(
						Instant.class, new InstantDeserializer());
		objectMapper.registerModule(instantModule);
		//
		final SimpleModule localDateTimeModule = new SimpleModule("LocalDateTimeModule",
				new Version(1, 0, 0, null, null, null)).addSerializer(
				LocalDateTime.class, new LocalDateTimeSerializer()).addDeserializer(
						LocalDateTime.class, new LocalDateTimeDeserializer());
		objectMapper.registerModule(localDateTimeModule);
		//
		final SimpleModule offsetDateTimeModule = new SimpleModule("offsetDateTimeModule",
				new Version(1, 0, 0, null, null, null)).addSerializer(
				OffsetDateTime.class, new OffsetDateTimeSerializer()).addDeserializer(
						OffsetDateTime.class, new OffsetDateTimeDeserializer());
		objectMapper.registerModule(offsetDateTimeModule);
		//
		final SimpleModule zonedDateTimeModule = new SimpleModule("zonedDateTimeModule",
				new Version(1, 0, 0, null, null, null)).addSerializer(
				ZonedDateTime.class, new ZonedDateTimeSerializer()).addDeserializer(
						ZonedDateTime.class, new ZonedDateTimeDeserializer());
		objectMapper.registerModule(zonedDateTimeModule);
		//
		final SimpleModule calendarModule = new SimpleModule("CalendarModule",
				new Version(1, 0, 0, null, null, null)).addSerializer(
				Calendar.class, new CalendarSerializer()).addDeserializer(
				Calendar.class, new CalendarDeserializer());
		objectMapper.registerModule(calendarModule);
		//
		final SimpleModule dateModule = new SimpleModule("DateModule", new Version(1,
				0, 0, null, null, null)).addSerializer(Date.class,
				new DateSerializer()).addDeserializer(Date.class,
				new DateDeserializer());
		objectMapper.registerModule(dateModule);
		setFailOnUnknownProperties(this.failOnUnknownProperties);
		setIndentOutput(this.indentOutput);
	}

	protected void initializeEnum() {
		objectMapper.getDeserializationConfig().with(
				DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT);
		objectMapper.registerModule(new EnumMapperModule());
	}

	/**
	 * Enum用のモジュール
	 * 
	 * @author tatsuo satoh
	 * 
	 */
	public class EnumMapperModule extends Module {

		@Override
		public String getModuleName() {
			return "EnumMapper";
		}

		@Override
		public Version version() {
			return new Version(2, 0, 0, null, null, null);
		}

		@Override
		public void setupModule(final SetupContext context) {
			context.addDeserializers(new Deserializers.Base() {
				@Override
				public JsonDeserializer<?> findEnumDeserializer(final Class<?> type, final DeserializationConfig config, final BeanDescription beanDesc)
						throws JsonMappingException {
					return new ExEnumDeserializer(type);
				}
			});
		}
	}

	/**
	 * Enum用のデシリアライザー
	 * 
	 * @author tatsuo satoh
	 * 
	 */
	static class ExEnumDeserializer extends EnumDeserializer {
		/** serialVersionUID */
		private static final long serialVersionUID = 1L;

		public ExEnumDeserializer(final Class<?> res) {
			super(createResolver(res), false);
		}

		@SuppressWarnings({ "unchecked", "rawtypes" })
		private static EnumResolver createResolver(final Class<?> cls) {
			final Class<Enum<?>> enumCls = (Class<Enum<?>>) cls;
			final Enum<?>[] enumValues = enumCls.getEnumConstants();
			final HashMap<String, Enum<?>> map = new HashMap<String, Enum<?>>();
			for (int i = enumValues.length; --i >= 0;) {
				final Enum<?> e = enumValues[i];
				map.put(e.toString(), e);
				if (e instanceof EnumConvertable) {
					map.put(((EnumConvertable) e).getValue().toString(), e);
				}
			}
			return new ExEnumResolver(enumCls, enumValues, map);
		}

		@Override
		public Object deserialize(final JsonParser jp, final DeserializationContext ctxt) throws IOException, JsonProcessingException {
			final JsonToken curr = jp.getCurrentToken();
			if ((curr == JsonToken.VALUE_STRING) || (curr == JsonToken.FIELD_NAME)) {
				final String name = jp.getText();
				if ("".equals(name)) {
					return null;
				}
			}
			return super.deserialize(jp, ctxt);
		}
	}

	/**
	 * Enumの変換用のリゾルバ
	 * 
	 * @author tatsuo satoh
	 */
	static class ExEnumResolver extends EnumResolver {
		/** serialVersionUID */
		private static final long serialVersionUID = 1L;

		protected ExEnumResolver(final Class<Enum<?>> enumClass, final Enum<?>[] enums,
	            final HashMap<String, Enum<?>> map, final Enum<?> defaultValue,
	            final boolean isIgnoreCase, final boolean isFromIntValue) {
			super(enumClass, enums, map, defaultValue, isIgnoreCase, isFromIntValue);
		}

		protected ExEnumResolver(final Class<Enum<?>> enumClass, final Enum<?>[] enums, final HashMap<String, Enum<?>> map) {
			super(enumClass, enums, map, null, false, false);
		}

		@Override
		public Enum<?> findEnum(final String key) {
			return EnumUtils.parse(this.getEnumClass(), key);
		}

		@Override
		public Enum<?> getEnum(final int index) {
			return EnumUtils.parse(this.getEnumClass(), index);
		}
	}

	/**
	 * Date専用のシリアライザ
	 * 
	 * @author tatsuo satoh
	 * 
	 */
	class DateSerializer extends JsonSerializer<Date> {
		@Override
		public void serialize(final Date value, final JsonGenerator paramJsonGenerator, final SerializerProvider paramSerializerProvider) throws IOException,
				JsonGenerationException {
			final String text = format(value);
			paramJsonGenerator.writeString(text);
		}
	}

	/**
	 * Date専用のデシリアライザ
	 * 
	 * @author tatsuo satoh
	 * 
	 */
	static class DateDeserializer extends JsonDeserializer<Date> {
		@Override
		public Date deserialize(final JsonParser jsonParser,
				final DeserializationContext paramDeserializationContext)
				throws IOException, JsonProcessingException {
			if (jsonParser.getText() == null
					|| jsonParser.getText().length() == 0) {
				return null;
			}
			final Date date = toDate(jsonParser.getText());
			return date;
		}
	}

	/**
	 * Calendar専用のシリアライザ
	 * 
	 * @author tatsuo satoh
	 * 
	 */
	class CalendarSerializer extends JsonSerializer<Calendar> {

		@Override
		public void serialize(final Calendar value, final JsonGenerator paramJsonGenerator,
				final SerializerProvider paramSerializerProvider) throws IOException,
				JsonGenerationException {
			paramJsonGenerator.writeString(format(value));
		}
	}

	/**
	 * Calendar専用のデシリアライザ
	 * 
	 * @author tatsuo satoh
	 * 
	 */
	static class CalendarDeserializer extends JsonDeserializer<Calendar> {
		@Override
		public Calendar deserialize(final JsonParser jsonParser,
				final DeserializationContext paramDeserializationContext)
				throws IOException, JsonProcessingException {
			if (jsonParser.getText() == null
					|| jsonParser.getText().length() == 0) {
				return null;
			}
			return toCalendar(jsonParser.getText());
		}
	}

	/**
	 * Instant専用のシリアライザ
	 * 
	 * @author tatsuo satoh
	 * 
	 */
	class InstantSerializer extends JsonSerializer<Instant> {

		@Override
		public void serialize(final Instant value, final JsonGenerator jgen,
				final SerializerProvider provider) throws IOException,
				JsonProcessingException {
			jgen.writeString(format(value));
		}
	}

	/**
	 * Instant専用のデシリアライザ
	 * 
	 * @author tatsuo satoh
	 * 
	 */
	static class InstantDeserializer extends JsonDeserializer<Instant> {

		@Override
		public Instant deserialize(final JsonParser jsonParser,
				final DeserializationContext paramDeserializationContext)
				throws IOException, JsonProcessingException {
			if (jsonParser.getText() == null
					|| jsonParser.getText().length() == 0) {
				return null;
			}
			final ZonedDateTime dateTime=toDateTime(jsonParser.getText());
			return dateTime.toInstant();
		}
	}

	/**
	 * LocalDateTime専用のシリアライザ
	 * 
	 * @author tatsuo satoh
	 * 
	 */
	class LocalDateTimeSerializer extends JsonSerializer<LocalDateTime> {

		@Override
		public void serialize(final LocalDateTime value, final JsonGenerator jgen,
				final SerializerProvider provider) throws IOException,
				JsonProcessingException {
			jgen.writeString(format(value));
		}
	}
	
	/**
	 * LocalDateTime専用のデシリアライザ
	 * 
	 * @author tatsuo satoh
	 * 
	 */
	static class LocalDateTimeDeserializer extends JsonDeserializer<LocalDateTime> {

		@Override
		public LocalDateTime deserialize(final JsonParser jsonParser,
				final DeserializationContext paramDeserializationContext)
				throws IOException, JsonProcessingException {
			if (jsonParser.getText() == null
					|| jsonParser.getText().length() == 0) {
				return null;
			}
			final ZonedDateTime dateTime=toDateTime(jsonParser.getText());
			return dateTime.toLocalDateTime();
		}
	}

	/**
	 * LocalDateTime専用のシリアライザ
	 * 
	 * @author tatsuo satoh
	 * 
	 */
	class OffsetDateTimeSerializer extends JsonSerializer<OffsetDateTime> {

		@Override
		public void serialize(final OffsetDateTime value, final JsonGenerator jgen,
				final SerializerProvider provider) throws IOException,
				JsonProcessingException {
			jgen.writeString(format(value));
		}
	}
	
	/**
	 * LocalDateTime専用のデシリアライザ
	 * 
	 * @author tatsuo satoh
	 * 
	 */
	static class OffsetDateTimeDeserializer extends JsonDeserializer<OffsetDateTime> {

		@Override
		public OffsetDateTime deserialize(final JsonParser jsonParser,
				final DeserializationContext paramDeserializationContext)
				throws IOException, JsonProcessingException {
			if (jsonParser.getText() == null
					|| jsonParser.getText().length() == 0) {
				return null;
			}
			final ZonedDateTime dateTime=toDateTime(jsonParser.getText());
			return dateTime.toOffsetDateTime();
		}
	}
	

	/**
	 * LocalDateTime専用のシリアライザ
	 * 
	 * @author tatsuo satoh
	 * 
	 */
	class ZonedDateTimeSerializer extends JsonSerializer<ZonedDateTime> {

		@Override
		public void serialize(final ZonedDateTime value, final JsonGenerator jgen,
				final SerializerProvider provider) throws IOException,
				JsonProcessingException {
			jgen.writeString(format(value));
		}
	}
	
	/**
	 * LocalDateTime専用のデシリアライザ
	 * 
	 * @author tatsuo satoh
	 * 
	 */
	static class ZonedDateTimeDeserializer extends JsonDeserializer<ZonedDateTime> {

		@Override
		public ZonedDateTime deserialize(final JsonParser jsonParser,
				final DeserializationContext paramDeserializationContext)
				throws IOException, JsonProcessingException {
			if (jsonParser.getText() == null
					|| jsonParser.getText().length() == 0) {
				return null;
			}
			final ZonedDateTime dateTime=toDateTime(jsonParser.getText());
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
	 * @param value
	 *          変換前のオブジェクト
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
		} catch (final JsonGenerationException e) {
			throw new RuntimeException(e);
		} catch (final IOException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * JSON文字列からオブジェクトに変換します
	 * 
	 * @param value
	 *          変換前のJSON文字列
	 * @param clazz
	 *          変換後のクラス
	 * @return 変換後のオブジェクト
	 */
	@Override
	public <S> S fromJsonString(final String value, final Class<S> clazz) {
		try {
			if (LOGGER.isTraceEnabled()) {
				LOGGER.trace("value=" + value);
			}
			return getObjectMapper().readValue(value, clazz);
		} catch (final JsonGenerationException e) {
			throw new RuntimeException(e);
		} catch (final IOException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * JSON文字列からオブジェクトに変換します
	 * 
	 * @param value
	 *          変換前のJSON文字列
	 * @param type
	 *          変換後のクラス
	 * @return 変換後のオブジェクト
	 */
	@Override
	public <S> S fromJsonString(final String value, final TypeReference<S> type) {
		try {
			if (LOGGER.isTraceEnabled()) {
				LOGGER.trace("value=" + value);
			}
			return getObjectMapper().readValue(value, type);
		} catch (final JsonGenerationException e) {
			throw new RuntimeException(e);
		} catch (final IOException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * InputStreamからオブジェクトに変換します
	 * 
	 * @param value
	 *          変換前のJSON文字列を含むストリーム
	 * @param clazz
	 *          変換後のクラス
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
		} catch (final JsonGenerationException e) {
			throw new RuntimeException(e);
		} catch (final IOException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * InputStreamからオブジェクトに変換します
	 * 
	 * @param value
	 *          変換前のJSON文字列を含むストリーム
	 * @param type
	 *          変換後のクラス
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
		} catch (final JsonGenerationException e) {
			throw new RuntimeException(e);
		} catch (final IOException e) {
			throw new RuntimeException(e);
		}
	}

	/* (non-Javadoc)
	 * @see com.sqlapp.util.AbstractJsonConverter#fromJsonString(java.io.File, java.lang.Class)
	 */
	@Override
	public <T> T fromJsonString(final File file, final TypeReference<T> clazz) {
		try {
			return getObjectMapper().readValue(file, clazz);
		} catch (final JsonGenerationException e) {
			throw new RuntimeException(e);
		} catch (final IOException e) {
			throw new RuntimeException(e);
		}
	}
	

	/* (non-Javadoc)
	 * @see com.sqlapp.util.AbstractJsonConverter#fromJsonString(java.io.File, java.lang.Class)
	 */
	@Override
	public <T> T fromJsonString(final File file, final Class<T> clazz) {
		try {
			return getObjectMapper().readValue(file, clazz);
		} catch (final JsonGenerationException e) {
			throw new RuntimeException(e);
		} catch (final IOException e) {
			throw new RuntimeException(e);
		}
	}

	/* (non-Javadoc)
	 * @see com.sqlapp.util.AbstractJsonConverter#writeJsonValue(java.io.OutputStream, java.lang.Object)
	 */
	@Override
	public void writeJsonValue(final OutputStream ostream, final Object value) {
		try {
			getObjectMapper().writeValue(ostream, value);
		} catch (final JsonGenerationException e) {
			throw new RuntimeException(e);
		} catch (final IOException e) {
			throw new RuntimeException(e);
		}
	}

	/* (non-Javadoc)
	 * @see com.sqlapp.util.AbstractJsonConverter#writeJsonValue(java.io.File, java.lang.Object)
	 */
	@Override
	public void writeJsonValue(final File file, final Object value) {
		try {
			getObjectMapper().writeValue(file, value);
		} catch (final JsonGenerationException e) {
			throw new RuntimeException(e);
		} catch (final IOException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * @param failOnUnknownProperties
	 *          the failOnUnknownProperties to set
	 */
	@Override
	public void setFailOnUnknownProperties(final boolean failOnUnknownProperties) {
		this.failOnUnknownProperties = failOnUnknownProperties;
		objectMapper.configure(
				DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
				this.failOnUnknownProperties);
	}

	private boolean indentOutput;
	
	public void setIndentOutput(final boolean indentOutput){
		this.indentOutput = indentOutput;
		objectMapper.configure(SerializationFeature.INDENT_OUTPUT, this.indentOutput);
	}
	
	/**
	 * @return the failOnUnknownProperties
	 */
	public boolean isFailOnUnknownProperties() {
		return failOnUnknownProperties;
	}
	
	@Override
	public JsonConverter clone() {
		final JsonConverter clone=new JsonConverter(this.isUtc());
		clone.setFailOnUnknownProperties(this.isFailOnUnknownProperties());
		clone.setIndentOutput(this.indentOutput);
		return clone;
	}
}
