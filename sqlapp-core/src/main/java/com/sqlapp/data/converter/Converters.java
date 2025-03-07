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

import static com.sqlapp.util.CommonUtils.map;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URL;
import java.sql.Blob;
import java.sql.Clob;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Period;
import java.time.chrono.JapaneseDate;
import java.time.chrono.JapaneseEra;
import java.time.format.DateTimeFormatter;
import java.util.Calendar;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TimeZone;
import java.util.UUID;

import org.geolatte.geom.Geometry;

import com.sqlapp.data.db.dialect.util.GeometryUtils;
import com.sqlapp.data.geometry.Box;
import com.sqlapp.data.geometry.Circle;
import com.sqlapp.data.geometry.Line;
import com.sqlapp.data.geometry.Lseg;
import com.sqlapp.data.geometry.Path;
import com.sqlapp.data.geometry.Point;
import com.sqlapp.data.geometry.Polygon;
import com.sqlapp.data.interval.Interval;
import com.sqlapp.data.interval.IntervalDay;
import com.sqlapp.data.interval.IntervalDayToHour;
import com.sqlapp.data.interval.IntervalDayToMinute;
import com.sqlapp.data.interval.IntervalDayToSecond;
import com.sqlapp.data.interval.IntervalHour;
import com.sqlapp.data.interval.IntervalHourToMinute;
import com.sqlapp.data.interval.IntervalHourToSecond;
import com.sqlapp.data.interval.IntervalMinute;
import com.sqlapp.data.interval.IntervalMinuteToSecond;
import com.sqlapp.data.interval.IntervalMonth;
import com.sqlapp.data.interval.IntervalSecond;
import com.sqlapp.data.interval.IntervalYear;
import com.sqlapp.data.interval.IntervalYearToDay;
import com.sqlapp.data.interval.IntervalYearToMonth;
import com.sqlapp.util.CommonUtils;

/**
 * 
 * @author SATOH
 * 
 */
public class Converters implements Serializable {

	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = -7044716555455402735L;

	private Map<Class<?>, Converter<?>> converterMap = map();

	private static final Converters DEFAULT_CONVERTER = new Converters();
	/**
	 * default converter
	 */
	private final Converter<?> defaultConveter = new DefaultConverter();
	/**
	 */
	private boolean enumEmptyToNull = false;
	
	/**
	 * コンストラクタ
	 */
	public Converters() {
		initialize(this);
	}

	protected void initialize(final Converters converters) {
		setNumberConverters();
		setByteConverters();
		setBooleanConverter();
		puts(new URLConverter(), URL.class);
		puts(new URLArrayConverter(this.getConverter(URL.class)), URL[].class);
		puts(new UUIDConverter(), UUID.class);
		puts(new UUIDArrayConverter(this.getConverter(UUID.class)),
				UUID[].class);
		puts(new LocaleConverter(), Locale.class);
		puts(new LocaleArrayConverter(this.getConverter(Locale.class)),
				Locale[].class);
		setZoneIdConverters();
		GeometryUtils.run(new Runnable(){
			@Override
			public void run() {
				puts(new GeometryConverter(), Geometry.class);
			}
		});
		//
		setIntervalConverters();
		setDateConverter(converters);
		//
		setGeometryConverters();
		//
		setStringConverter(new StringConverter());
		puts(this.getConverter(String.class), Clob.class);
	}

	protected void setBooleanConverter(){
		puts(new BooleanConverter(), Boolean.class);
		puts(new BooleanConverter().setDefaultValue(Boolean.FALSE), boolean.class);
	}
	
	protected void setZoneIdConverters() {
		// TimeZone
		puts(new TimeZoneConverter(), TimeZone.class);
		puts(new TimeZoneArrayConverter(this.getConverter(TimeZone.class)),
				TimeZone[].class);
		// ZoneId
		puts(new ZoneIdConverter(), java.time.ZoneId.class);
		puts(new ZoneIdArrayConverter(this.getConverter(java.time.ZoneId.class)),
				java.time.ZoneId[].class);
		// ZoneOffset
		puts(new ZoneOffsetConverter(), java.time.ZoneOffset.class);
		puts(new ZoneOffsetArrayConverter(this.getConverter(java.time.ZoneOffset.class)),
				java.time.ZoneOffset[].class);
	}
	
	public Converters setStringConverter(final StringConverter converter) {
		converter.setConverters(this);
		puts(new StringConverter(), String.class, Clob.class);
		puts(new StringArrayConverter(converter), String[].class);
		return this;
	}

	public Converters setNumberConverter(final NumberConverter converter) {
		converter.setConverters(this);
		puts(new NumberArrayConverter(converter), Number[].class);
		return this;
	}

	protected void setByteConverters() {
		final ByteConverter byteConverter=new ByteConverter();
		byteConverter.setDefaultValue((byte)0);
		puts(new Base64Converter(byteConverter), byte[].class);
		puts(new Base64Converter(), Blob.class);
		puts(new ByteConverter(), Byte.class);
		puts(byteConverter, byte.class);
		puts(new ByteObjectArrayConverter(this.getConverter(Byte.class)),
				Byte[].class);
	}

	protected void setNumberConverters() {
		puts(new LongConverter(), Long.class);
		puts(new LongConverter().setDefaultValue(0L), long.class);
		puts(new LongArrayConverter(this.getConverter(long.class)),
				long[].class);
		puts(new LongObjectArrayConverter(this.getConverter(Long.class)),
				Long[].class);
		//
		puts(new IntegerConverter(), Integer.class);
		puts(new IntegerConverter().setDefaultValue(0), int.class);
		puts(new IntArrayConverter(this.getConverter(int.class)), int[].class);
		puts(new IntegerArrayConverter(this.getConverter(Integer.class)),
				Integer[].class);
		//
		puts(new ShortConverter(), Short.class);
		puts(new ShortConverter().setDefaultValue((short)0), short.class);
		puts(new ShortArrayConverter(this.getConverter(short.class)),
				short[].class);
		puts(new ShortObjectArrayConverter(this.getConverter(Short.class)),
				Short[].class);
		//
		puts(new FloatConverter(), Float.class);
		puts(new FloatConverter().setDefaultValue(0.0f), float.class);
		puts(new FloatArrayConverter(this.getConverter(float.class)),
				float[].class);
		puts(new FloatObjectArrayConverter(this.getConverter(Float.class)),
				Float[].class);
		//
		puts(new DoubleConverter(), Double.class);
		puts(new DoubleConverter().setDefaultValue(0.0d), double.class);
		puts(new DoubleArrayConverter(this.getConverter(double.class)),
				double[].class);
		puts(new DoubleObjectArrayConverter(this.getConverter(Double.class)),
				Double[].class);
		//
		puts(new BigDecimalConverter(), BigDecimal.class);
		puts(new BigDecimalArrayConverter(this.getConverter(BigDecimal.class)),
				BigDecimal[].class);
		//
		puts(new BigIntegerConverter(), BigInteger.class);
		puts(new BigIntegerArrayConverter(this.getConverter(BigInteger.class)),
				BigInteger[].class);
		//
		setNumberConverter(new NumberConverter());
	}

	protected void setIntervalConverters() {
		// Interval
		puts(new IntervalConverter(), Interval.class);
		puts(new IntervalArrayConverter(this.getConverter(Interval.class)),
				Interval[].class);
		//
		puts(new IntervalYearConverter(), IntervalYear.class);
		puts(new IntervalYearArrayConverter(
				this.getConverter(IntervalYear.class)), IntervalYear[].class);
		//
		puts(new IntervalMonthConverter(), IntervalMonth.class);
		puts(new IntervalMonthArrayConverter(
				this.getConverter(IntervalMonth.class)), IntervalMonth[].class);
		//
		puts(new IntervalDayConverter(), IntervalDay.class);
		puts(new IntervalDayArrayConverter(this.getConverter(IntervalDay.class)),
				IntervalDay[].class);
		//
		puts(new IntervalHourConverter(), IntervalHour.class);
		puts(new IntervalHourArrayConverter(
				this.getConverter(IntervalHour.class)), IntervalHour[].class);
		//
		puts(new IntervalMinuteConverter(), IntervalMinute.class);
		puts(new IntervalMinuteArrayConverter(
				this.getConverter(IntervalMinute.class)),
				IntervalMinute[].class);
		//
		puts(new IntervalSecondConverter(), IntervalSecond.class);
		puts(new IntervalSecondArrayConverter(
				this.getConverter(IntervalSecond.class)),
				IntervalSecond[].class);
		//
		puts(new IntervalYearToMonthConverter(), IntervalYearToMonth.class);
		puts(new IntervalYearToMonthArrayConverter(
				this.getConverter(IntervalYearToMonth.class)),
				IntervalYearToMonth[].class);
		//
		puts(new IntervalYearToDayConverter(), IntervalYearToDay.class);
		puts(new IntervalYearToDayArrayConverter(
				this.getConverter(IntervalYearToDay.class)),
				IntervalYearToDay[].class);
		//
		puts(new IntervalDayToHourConverter(), IntervalDayToHour.class);
		puts(new IntervalDayToHourArrayConverter(
				this.getConverter(IntervalDayToHour.class)),
				IntervalDayToHour[].class);
		//
		puts(new IntervalDayToMinuteConverter(), IntervalDayToMinute.class);
		puts(new IntervalDayToMinuteArrayConverter(
				this.getConverter(IntervalDayToMinute.class)),
				IntervalDayToMinute[].class);
		//
		puts(new IntervalDayToSecondConverter(), IntervalDayToSecond.class);
		puts(new IntervalDayToSecondArrayConverter(
				this.getConverter(IntervalDayToSecond.class)),
				IntervalDayToSecond[].class);
		//
		puts(new IntervalHourToMinuteConverter(), IntervalHourToMinute.class);
		puts(new IntervalHourToMinuteArrayConverter(
				this.getConverter(IntervalHourToMinute.class)),
				IntervalHourToMinute[].class);
		//
		puts(new IntervalHourToSecondConverter(), IntervalHourToSecond.class);
		puts(new IntervalHourToSecondArrayConverter(
				this.getConverter(IntervalHourToSecond.class)),
				IntervalHourToSecond[].class);
		//
		puts(new IntervalMinuteToSecondConverter(),
				IntervalMinuteToSecond.class);
		puts(new IntervalMinuteToSecondArrayConverter(
				this.getConverter(IntervalMinuteToSecond.class)),
				IntervalMinuteToSecond[].class);
		//
	}

	protected void setGeometryConverters() {
		// Point
		puts(new PointConverter(), Point.class);
		puts(new PointArrayConverter(this.getConverter(Point.class)),
				Point[].class);
		// Circle
		puts(new CircleConverter(), Circle.class);
		puts(new CircleArrayConverter(this.getConverter(Circle.class)),
				Circle[].class);
		// Box
		puts(new BoxConverter(), Box.class);
		puts(new BoxArrayConverter(this.getConverter(Box.class)),
				Box[].class);
		// Line
		puts(new LineConverter(), Line.class);
		puts(new LineArrayConverter(this.getConverter(Line.class)),
				Line[].class);
		// Lseg
		puts(new LsegConverter(), Lseg.class);
		puts(new LsegArrayConverter(this.getConverter(Lseg.class)),
				Lseg[].class);
		// Path
		puts(new PathConverter(), Path.class);
		puts(new PathArrayConverter(this.getConverter(Path.class)),
				Path[].class);
		// Polygon
		puts(new PolygonConverter(), Polygon.class);
		puts(new PolygonArrayConverter(this.getConverter(Polygon.class)),
				Polygon[].class);
	}

	/**
	 * 
	 * @param converters
	 */
	private void setDateConverter(final Converters converters) {
		final ZonedDateTimeConverter zonedDateTimeConverter=setJava8DateConverter(converters);
		//
		final CalendarConverter calendarConverter = new CalendarConverter();
		calendarConverter.setZonedDateTimeConverter(zonedDateTimeConverter);
		put(Calendar.class, calendarConverter);
		puts(new CalendarArrayConverter(this.getConverter(Calendar.class)),
				Calendar[].class);
		//
		put(java.util.Date.class, DateConverter.newInstance().setZonedDateTimeConverter(zonedDateTimeConverter.clone().setFormat("uuuu-MM-dd HH:mm:ss")));
		puts(new DateArrayConverter(this.getConverter(java.util.Date.class)),
				java.util.Date[].class);
		//
		final SqlDateConverter sqlDateConverter = SqlDateConverter.newInstance().setZonedDateTimeConverter(ZonedDateTimeConverter.newInstance()
				.setParseFormats(""
				,"uuuu-M-d"
				,"uuuu-M-d H:m:s"
				,"uuuu-M-d H:m"
				, DateTimeFormatter.RFC_1123_DATE_TIME
				,"uuuu-M-d H:m:s.SSS Z"
				,"uuuu-M-d H:m:s.SSS"
				,"uuuu-M-d H:m:s Z"
				,"uuuu-M-d H:m Z").setFormat("uuuu-MM-dd"));
		put(java.sql.Date.class, sqlDateConverter);
		puts(new SqlDateArrayConverter(this.getConverter(java.sql.Date.class)),
				java.sql.Date[].class);
		//
		final TimeConverter timeConverter = new TimeConverter();
		timeConverter.setZonedDateTimeConverter(ZonedDateTimeConverter.newInstance().setParseFormats(""
				,DateTimeFormatter.ISO_OFFSET_DATE_TIME //2011-12-03T10:15:30+01:00
				,DateTimeFormatter.ISO_OFFSET_TIME //10:15:30+01:00
				,"H:m:s.SSS xxxx"
				,"H:m:s.SSS"
				,"H:m:s xxxx"
				,"H:m:s"
				,"H:m"
				, DateTimeFormatter.RFC_1123_DATE_TIME));
		put(java.sql.Time.class, timeConverter);
		puts(new TimeArrayConverter(this.getConverter(java.sql.Time.class)),
				java.sql.Time[].class);
		//
		final TimestampConverter timestampConverter = new TimestampConverter();
		timestampConverter.setZonedDateTimeConverter(zonedDateTimeConverter.clone()
				.addParseFormat(0, "uuuu-M-d H:m:s.nnnnnnnnn")
				.addParseFormat(0, "uuuu-M-d H:m:s.SSS")
				.setFormat("uuuu-MM-dd HH:mm:ss.nnnnnnnnn"));
		put(Timestamp.class, timestampConverter);
		puts(new TimestampArrayConverter(this.getConverter(Timestamp.class)),
				Timestamp[].class);
		//
		final PeriodConverter periodConverter = new PeriodConverter();
		put(Period.class, periodConverter);
		puts(new PeriodArrayConverter(this.getConverter(Period.class)),
				Period[].class);
		//
		final DurationConverter durationConverter = new DurationConverter();
		put(Duration.class, durationConverter);
		puts(new DurationArrayConverter(this.getConverter(Duration.class)),
				Duration[].class);
		//
		final JapaneseEraConverter japaneseEraConverter = new JapaneseEraConverter();
		put(JapaneseEra.class, japaneseEraConverter);
		puts(new JapaneseEraArrayConverter(this.getConverter(JapaneseEra.class)),
				JapaneseEra[].class);
		//
		final JapaneseDateConverter japaneseDateConverter = new JapaneseDateConverter();
		put(JapaneseDate.class, japaneseDateConverter);
		puts(new JapaneseDateArrayConverter(this.getConverter(JapaneseDate.class)),
				JapaneseDate[].class);
	}
	
	public static ZonedDateTimeConverter createDefaultZonedDateTimeConverter(){
		//ZonedDateTime
		final ZonedDateTimeConverter zonedDateTimeConverter=ZonedDateTimeConverter.newInstance().setParseFormats(""
				, DateTimeFormatter.ISO_ZONED_DATE_TIME  //'2011-12-03T10:15:30+01:00[Europe/Paris]'
				, DateTimeFormatter.ISO_OFFSET_DATE_TIME //2011-12-03T10:15:30+01:00'
				, DateTimeFormatter.RFC_1123_DATE_TIME
				,"uuuu-M-d'T'H:m:s.SSSXXXX"
				,"uuuu-M-d'T'H:m:s.SSSzzz"
				,"uuuu-M-d'T'H:m:sXXXX"
				,"uuuu-M-d'T'H:m:szzz"
				,"uuuu-M-d'T'H:m:s.SSS"
				,"uuuu-M-d'T'H:m:s"
				,"uuuu-M-d'T'H:m"
				,"uuuu-M-d H:m:s.SSS XXXX"
				,"uuuu-M-d H:m:s.SSS zzz"
				,"uuuu-M-d H:m:s.nnnnnnnnn"
				,"uuuu-M-d H:m:s.SSS"
				,"uuuu-M-d H:m:s VV"
				,"uuuu-M-d H:m:s zzz"
				,"uuuu-M-d H:m:s XXXX"
				,"uuuu-M-d H:m:s"
				,"uuuu-M-d H:m"
				,"uuuu-M-d"
				).setFormat("uuuu-MM-dd HH:mm:ss xxxxx'['zzz']'");
		return zonedDateTimeConverter;
	}
	
	/**
	 * @return this
	 */
	public Converters toIsoDateFormat(){
		final ZonedDateTimeConverter zonedDateTimeConverter=this.getConverter(java.time.ZonedDateTime.class);
		zonedDateTimeConverter.setFormat(DateTimeFormatter.ISO_ZONED_DATE_TIME);
		final OffsetDateTimeConverter offsetDateTimeConverter=this.getConverter(java.time.OffsetDateTime.class);
		offsetDateTimeConverter.setFormat(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
		final InstantConverter instantConverter=this.getConverter(java.time.Instant.class);
		instantConverter.setFormat(DateTimeFormatter.ISO_INSTANT);
		final OffsetTimeConverter offsetTimeConverter=this.getConverter(java.time.OffsetTime.class);
		offsetTimeConverter.setFormat(DateTimeFormatter.ISO_OFFSET_TIME);
		//
		final DateConverter dateConverter=this.getConverter(java.util.Date.class);
		dateConverter.getZonedDateTimeConverter().setFormat(DateTimeFormatter.ISO_INSTANT);
		final LocalDateTimeConverter localDateTimeConverter=this.getConverter(LocalDateTime.class);
		localDateTimeConverter.setFormat(DateTimeFormatter.ISO_INSTANT);
		//
		final SqlDateConverter sqlDateConverter=this.getConverter(java.sql.Date.class);
		sqlDateConverter.getZonedDateTimeConverter().setFormat(DateTimeFormatter.ISO_DATE);
		final LocalDateConverter localDateConverter=this.getConverter(LocalDate.class);
		localDateConverter.setFormat(DateTimeFormatter.ISO_DATE);
		//
		final TimeConverter timeConverter=this.getConverter(java.sql.Time.class);
		timeConverter.getZonedDateTimeConverter().setFormat(DateTimeFormatter.ISO_TIME);
		final LocalTimeConverter LocalTimeConverter=this.getConverter(LocalTime.class);
		LocalTimeConverter.setFormat(DateTimeFormatter.ISO_TIME);
		final TimestampConverter timestampConverter=this.getConverter(java.sql.Timestamp.class);
		timestampConverter.getZonedDateTimeConverter().setFormat(DateTimeFormatter.ISO_INSTANT);
		return this;
	}

	private ZonedDateTimeConverter setJava8DateConverter(final Converters converters) {
		//ZonedDateTime
		final ZonedDateTimeConverter zonedDateTimeConverter=createDefaultZonedDateTimeConverter();
		put(java.time.ZonedDateTime.class, zonedDateTimeConverter);
		puts(new ZonedDateTimeArrayConverter(this.getConverter(java.time.ZonedDateTime.class)),
				java.time.ZonedDateTime[].class);
		//OffsetDateTime
		put(java.time.OffsetDateTime.class, OffsetDateTimeConverter.newInstance()
				.setParseFormats(""
				, DateTimeFormatter.ISO_OFFSET_DATE_TIME //2011-12-03T10:15:30+01:00
				, DateTimeFormatter.RFC_1123_DATE_TIME
				,"uuuu-M-d'T'H:m:s.SSSXXXX"
				,"uuuu-M-d'T'H:m:sXXXX"
				,"uuuu-M-d'T'H:m:s.SSS"
				,"uuuu-M-d'T'H:m:s"
				,"uuuu-M-d'T'H:m"
				,"uuuu-M-d H:m:s.SSS XXXX"
				,"uuuu-M-d H:m:s.SSS zzz"
				,"uuuu-M-d H:m:s.SSS"
				,"uuuu-M-d H:m:s VV"
				,"uuuu-M-d H:m:s zzz"
				,"uuuu-M-d H:m:s XXXX"
				,"uuuu-M-d H:m:s"
				,"uuuu-M-d H:m"
				,"uuuu-M-d"
				).setFormat("uuuu-MM-dd HH:mm:ss xxxxx"));
		puts(new OffsetDateTimeArrayConverter(this.getConverter(java.time.OffsetDateTime.class)),
				java.time.OffsetDateTime[].class);
		//Instant
		put(java.time.Instant.class, InstantConverter.newInstance().setParseFormats(""
				,DateTimeFormatter.ISO_OFFSET_DATE_TIME //2011-12-03T10:15:30+01:00
				, DateTimeFormatter.RFC_1123_DATE_TIME
				,"uuuu-M-d'T'H:m:s.SSSXXXX"
				,"uuuu-M-d H:m:s.SSS XXXX"
				,"uuuu-M-d'T'H:m:s.SSS"
				,"uuuu-M-d'T'H:m:s"
				,"uuuu-M-d'T'H:m"
				,"uuuu-M-d H:m:s.SSS"
				,"uuuu-M-d H:m:s"
				,"uuuu-M-d H:m"
				,"uuuu-M-d"
				).setFormat("yyyy-MM-dd HH:mm:ss")
			);
		puts(new InstantArrayConverter(this.getConverter(java.time.Instant.class)),
				java.time.Instant[].class);
		//LocalDate
		put(java.time.LocalDate.class, LocalDateConverter.newInstance().setParseFormats(""
				,DateTimeFormatter.ISO_OFFSET_DATE_TIME //2011-12-03T10:15:30+01:00
				, DateTimeFormatter.RFC_1123_DATE_TIME
				,"uuuu-M-d'T'H:m:s.SSSXXXX"
				,"uuuu-M-d H:m:s.SSS XXXX"
				,"uuuu-M-d"
				,"uuuu-M-d'T'H:m:s.SSS"
				,"uuuu-M-d'T'H:m:s"
				,"uuuu-M-d'T'H:m"
				,"uuuu-M-d H:m:s.SSS"
				,"uuuu-M-d H:m:s"
				,"uuuu-M-d H:m"
				).setFormat("yyyy-MM-dd"));
		puts(new LocalDateArrayConverter(this.getConverter(java.time.LocalDate.class)),
				java.time.LocalDate[].class);
		//LocalDateTime
		put(java.time.LocalDateTime.class, LocalDateTimeConverter.newInstance()
				.setParseFormats(""
				,DateTimeFormatter.ISO_OFFSET_DATE_TIME //2011-12-03T10:15:30+01:00
				, DateTimeFormatter.RFC_1123_DATE_TIME
				,"uuuu-M-d'T'H:m:s.SSSXXXX"
				,"uuuu-M-d H:m:s.SSS xxxx"
				,"uuuu-M-d'T'H:m:s.SSS"
				,"uuuu-M-d'T'H:m:s"
				,"uuuu-M-d'T'H:m"
				,"uuuu-M-d H:m:s.SSS"
				,"uuuu-M-d H:m:s"
				,"uuuu-M-d H:m"
				,"uuuu-M-d"
				).setFormat("uuuu-MM-dd HH:mm:ss"));
		puts(new LocalDateTimeArrayConverter(this.getConverter(java.time.LocalDateTime.class)),
				java.time.LocalDateTime[].class);
		//LocalTime
		put(java.time.LocalTime.class, LocalTimeConverter.newInstance().setParseFormats(""
				,DateTimeFormatter.ISO_OFFSET_DATE_TIME //2011-12-03T10:15:30+01:00
				,DateTimeFormatter.ISO_OFFSET_TIME //10:15:30+01:00
				,"H:m:s.SSS xxxx"
				,"H:m:s.SSS"
				,"H:m:s xxxx"
				,"H:m:s"
				,"H:m"
				).setFormat("HH:mm:ss"));
		puts(new LocalTimeArrayConverter(this.getConverter(java.time.LocalTime.class)),
				java.time.LocalTime[].class);
		//OffsetTime
		put(java.time.OffsetTime.class, OffsetTimeConverter.newInstance().setParseFormats(""
				,DateTimeFormatter.ISO_OFFSET_DATE_TIME //2011-12-03T10:15:30+01:00
				,DateTimeFormatter.ISO_OFFSET_TIME //10:15:30+01:00
				,"H:m:s.SSS xxxx"
				,"H:m:s.SSS"
				,"H:m:s xxxx"
				,"H:m:s"
				,"H:m"
				).setFormat("HH:mm:ss xxxxx"));
		puts(new OffsetTimeArrayConverter(this.getConverter(java.time.OffsetTime.class)),
				java.time.OffsetTime[].class);
		//YearMonth
		put(java.time.YearMonth.class, YearMonthConverter.newInstance().setParseFormats(""
				,DateTimeFormatter.ISO_OFFSET_DATE_TIME //2011-12-03T10:15:30+01:00
				, DateTimeFormatter.RFC_1123_DATE_TIME
				,"uuuu-M-d'T'H:m:s.SSSXXXX"
				,"uuuu-M-d H:m:s.SSS XXXX"
				,"uuuu-M-d"
				,"uuuu-M-d'T'H:m:s.SSS"
				,"uuuu-M-d'T'H:m:s"
				,"uuuu-M-d'T'H:m"
				,"uuuu-M-d H:m:s.SSS"
				,"uuuu-M-d H:m:s"
				,"uuuu-M-d H:m"
				,"uuuu-M"
				).setFormat("yyyy-MM"));
		puts(new YearMonthArrayConverter(this.getConverter(java.time.YearMonth.class)),
				java.time.YearMonth[].class);
		//Year
		put(java.time.Year.class, YearConverter.newInstance().setParseFormats(""
				,DateTimeFormatter.ISO_OFFSET_DATE_TIME //2011-12-03T10:15:30+01:00
				, DateTimeFormatter.RFC_1123_DATE_TIME
				,"uuuu-M-d'T'H:m:s.SSSXXXX"
				,"uuuu-M-d H:m:s.SSS XXXX"
				,"uuuu-M-d"
				,"uuuu-M-d'T'H:m:s.SSS"
				,"uuuu-M-d'T'H:m:s"
				,"uuuu-M-d'T'H:m"
				,"uuuu-M-d H:m:s.SSS"
				,"uuuu-M-d H:m:s"
				,"uuuu-M-d H:m"
				,"uuuu-M"
				,"uuuu"
				).setFormat("yyyy"));
		puts(new YearArrayConverter(this.getConverter(java.time.Year.class)),
				java.time.Year[].class);
		return zonedDateTimeConverter;
	}

	public boolean isConvertable(final Class<?> clazz) {
		if (clazz.isEnum()) {
			return true;
		}
		final Converter<?> converter = getConverter(clazz);
		if (converter instanceof DefaultConverter) {
			return false;
		}
		return converter != null;
	}

	@SuppressWarnings("unchecked")
	public <T> T convertObject(final Object src, final Class<T> clazz) {
		if (src != null) {
			if (src.getClass().isEnum()) {
				if (clazz.equals(src.getClass())) {
					return (T) src;
				} else if (src instanceof EnumConvertable) {
					@SuppressWarnings("rawtypes")
					final
					Object value = ((EnumConvertable) src).getValue();
					final Converter<?> converter = getConverter(clazz);
					return (T) converter.convertObject(value);
				}
			} else {
				if (!clazz.isEnum() && String.class.equals(clazz)) {
					@SuppressWarnings("rawtypes")
					final
					Converter converter = getConverter(src.getClass());
					return (T) converter.convertString(src);
				}
			}
		}
		final Converter<?> converter = getConverter(clazz);
		if (src instanceof Optional) {
			final Optional<?> op=(Optional<?>)src;
			return convertInternal(converter, op.orElse(null), clazz);
		}
		return convertInternal(converter, src, clazz);
	}

	@SuppressWarnings("unchecked")
	private <T> T convertInternal(final Converter<?> converter, final Object src, final Class<T> clazz) {
		if (clazz.isPrimitive()) {
			if (src==null) {
				return getPrimitiveDefaultValue(clazz);
			}
			final Object val= converter.convertObject(src);
			if (val==null) {
				return getPrimitiveDefaultValue(clazz);
			}
			return (T)val;
		}
		return (T) converter.convertObject(src);
	}

	@SuppressWarnings("unchecked")
	private <T> T getPrimitiveDefaultValue(final Class<T> clazz){
		if (clazz==boolean.class) {
			return (T)Boolean.FALSE;
		}else if (clazz==int.class) {
			return (T)IntegerConverter.ZERO;
		}else if (clazz==byte.class) {
			return (T)ByteConverter.ZERO;
		}else if (clazz==long.class) {
			return (T)LongConverter.ZERO;
		}else if (clazz==short.class) {
			return (T)ShortConverter.ZERO;
		}else if (clazz==float.class) {
			return (T)FloatConverter.ZERO;
		}else if (clazz==double.class) {
			return (T)DoubleConverter.ZERO;
		}
		return null;
	}
	
	@SuppressWarnings("unchecked")
	public String convertString(final Object src, final Class<?> clazz) {
		@SuppressWarnings("rawtypes")
		Converter converter = getConverter(clazz);
		if (converter == null) {
			converter = this.defaultConveter;
		}
		if (src!=null && !(src instanceof String)) {
			if (converter instanceof StringConverter) {
				return ((StringConverter)converter).convertObject(src);
			}
		}
		return converter.convertString(src);
	}

	public String convertString(final Object src) {
		if (src == null) {
			return null;
		}
		return convertString(src, src.getClass());
	}

	public Object copy(final Object src) {
		if (src == null) {
			return src;
		}
		final Converter<?> converter = getConverter(src.getClass());
		return converter.copy(src);
	}

	/**
	 * 
	 * @param clazz
	 *            コンバーターを取得したいクラス  
	 * @return 指定したクラスに対応したコンバーター
	 */
	@SuppressWarnings({ "unchecked" })
	public <S extends Converter<T>, T> S getConverter(final Class<T> clazz) {
		if (clazz == null) {
			return (S) defaultConveter;
		}
		final Converter<?> converter = getConverterInternal(clazz);
		if (converter == null) {
			return (S) defaultConveter;
		}
		return (S) converter;
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	protected <S extends Converter<T>, T> S getConverterInternal(final Class<T> clazz) {
		Converter<?> converter = converterMap.get(clazz);
		if (converter == null) {
			if (clazz.isArray()) {
				final Class<?> componentType = clazz.getComponentType();
				if (componentType.isEnum()) {
					final EnumConverter enumConverter = new EnumConverter(
							componentType);
					enumConverter.setEmptyToNull(this.isEnumEmptyToNull());
					final EnumArrayConverter arrayConverter = new EnumArrayConverter(
							clazz, enumConverter);
					this.put(clazz, arrayConverter);
					return (S) arrayConverter;
				}
			} else if (clazz.isEnum()) {
				final EnumConverter enumConverter = new EnumConverter(clazz);
				enumConverter.setEmptyToNull(this.isEnumEmptyToNull());
				this.put(clazz, enumConverter);
				return (S) enumConverter;
			} else {
				converter = findConverter(clazz);
			}
		}
		return (S) converter;
	}

	private final Set<Class<?>> unsupportedClasses = CommonUtils.set();

	@SuppressWarnings("unchecked")
	protected <T> Converter<T> findConverter(final Class<T> clazz) {
		Converter<T> converter = null;
		if (unsupportedClasses.contains(clazz)) {
			return null;
		}
		for (final Map.Entry<Class<?>, Converter<?>> entry : converterMap.entrySet()) {
			if (entry.getKey().isAssignableFrom(clazz)) {
				converter = (Converter<T>) entry.getValue();
				break;
			}
		}
		if (converter != null) {
			converterMap.put(clazz, converter);
		} else {
			unsupportedClasses.add(clazz);
		}
		return converter;
	}

	public Map<Class<?>, Converter<?>> getConverterMap() {
		return converterMap;
	}

	public void setConverterMap(final Map<Class<?>, Converter<?>> converterMap) {
		this.converterMap = converterMap;
	}

	/**
	 * 
	 * @param clazz
	 * @param converter
	 */
	public Converters put(final Class<?> clazz, final Converter<?> converter) {
		getConverterMap().put(clazz, converter);
		unsupportedClasses.remove(clazz);
		return this;
	}

	private void puts(final Converter<?> converter, final Class<?>... clazzs) {
		for (final Class<?> clazz : clazzs) {
			put(clazz, converter);
		}
	}

	/**
	 * 
	 * @param name
	 * @param converter
	 */
	public Converters put(final String name, final Converter<?> converter) {
		try {
			put(Class.forName(name), converter);
		} catch (final ClassNotFoundException e) {
			throw new RuntimeException(e);
		}
		return this;
	}

	public static Converters getDefault() {
		return DEFAULT_CONVERTER;
	}

	public static Converters getNewBooleanTrueInstance() {
		final Converters converters = new Converters();
		final BooleanConverter converter = (BooleanConverter) converters
				.getConverter(Boolean.class);
		converter.setTrueString("true").setFalseString("false");
		converters.puts(converter, Boolean.class, boolean.class);
		return converters;
	}

	/**
	 * @return the enumEmptyToNull
	 */
	public boolean isEnumEmptyToNull() {
		return enumEmptyToNull;
	}

	/**
	 * @param enumEmptyToNull
	 *            the enumEmptyToNull to set
	 */
	public void setEnumEmptyToNull(final boolean enumEmptyToNull) {
		this.enumEmptyToNull = enumEmptyToNull;
	}
}
