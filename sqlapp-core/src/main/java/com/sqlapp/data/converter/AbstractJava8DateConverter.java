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

import static com.sqlapp.util.CommonUtils.cast;
import static com.sqlapp.util.CommonUtils.eq;
import static com.sqlapp.util.CommonUtils.isEmpty;
import static com.sqlapp.util.CommonUtils.linkedSet;
import static com.sqlapp.util.DateUtils.DEFAULT_DATETIME_FORMAT;

import java.text.ParsePosition;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.Year;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.chrono.ChronoLocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoField;
import java.time.temporal.Temporal;
import java.time.temporal.TemporalAccessor;
import java.time.temporal.TemporalQueries;
import java.util.Arrays;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.Java8DateUtils;

/**
 */
public abstract class AbstractJava8DateConverter<T extends Temporal,S> extends AbstractConverter<T> implements NewValue<T>,Cloneable{

	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = -8249231108644220313L;
	/**
	 * date parse format
	 */
	private DateTimeFormatter[] parseFormats=Java8DateUtils.getDateTimeFormatters(DEFAULT_DATETIME_FORMAT);
	/**
	 * date format
	 */
	private DateTimeFormatter format=null;
	
	protected static final Pattern NUMBER_PATTERN = Pattern.compile("\\+?[0-9]+");
	
	protected static final ZoneId INSTANT_ZONE_ID=ZoneId.of("Z");
	
	protected static final ZoneOffset INSTANT_ZONE_OFFSET=INSTANT_ZONE_ID.getRules().getOffset(Instant.now());

	protected static final LocalDate EPOC_DAY=LocalDate.ofEpochDay(0);

	protected static final LocalTime EPOC_TIME=LocalTime.of(0, 0);

	protected static final LocalDateTime EPOC_DATE=LocalDateTime.of(EPOC_DAY, EPOC_TIME);
	
	private boolean useSystemZone=true;
	
	protected T parseDate(final String value) {
		final int size=parseFormats.length;
		DateTimeParseException ext=null;
		for(int i=0;i<size;i++){
			try {
				final DateTimeFormatter dateTimeFormatter=parseFormats[i];
				final T dateTime= parse(value, dateTimeFormatter);
				if (dateTime!=null){
					return dateTime;
				}
			} catch (final DateTimeParseException e) {
				if (ext==null){
					ext=e;
				}
			}
		}
		final String message=this.getClass().getSimpleName()+"#parseDate Unparseable date: \"" + value + "\"";
		throw new UnsupportedOperationException(message, ext);
	}
	
	protected abstract T parse(String value, DateTimeFormatter dateTimeFormatter);
	
	public DateTimeFormatter[] getParseFormats() {
		return parseFormats;
	}

	public S setParseFormats(final String... parseFormats) {
		return setParseFormats(Java8DateUtils.getDateTimeFormatters(parseFormats));
	}

	protected abstract String format(T temporal, DateTimeFormatter formatter);

	@Override
	public String convertString(final T value) {
		if (value ==null){
			return null;
		}
		if (this.getFormat() ==null){
			return value.toString();
		}
		final String result=format(value, this.getFormat());
		return result;
	}
	
	/**
	 * @return the useSystemZone
	 */
	protected boolean isUseSystemZone() {
		return useSystemZone;
	}

	/**
	 * @param useSystemZone the useSystemZone to set
	 */
	protected S setUseSystemZone(final boolean useSystemZone) {
		this.useSystemZone = useSystemZone;
		return instance();
	}

	public S setParseFormats(final Object... parseFormats) {
		if (CommonUtils.isEmpty(parseFormats)){
			return instance();
		}
		final List<DateTimeFormatter> formatters=CommonUtils.list();
		for(final Object parseFormat:parseFormats){
			if (CommonUtils.isEmpty(parseFormat)){
				continue;
			}
			if (parseFormat instanceof DateTimeFormatter){
				formatters.add((DateTimeFormatter)parseFormat);
			} else if (parseFormat instanceof String){
				formatters.add(Java8DateUtils.getDateTimeFormatter((String)parseFormat));
			} else{
				throw new IllegalArgumentException("parseFormats must String or DateTimeFormatter.["+Arrays.toString(parseFormats)+"]");
			}
		}
		return setParseFormats(formatters.toArray(new DateTimeFormatter[0]));
	}

	public S addParseFormat(final Object parseFormat) {
		return addParseFormat(CommonUtils.size(this.parseFormats), parseFormat);
	}

	public S addParseFormat(final int index,final Object parseFormat) {
		if (CommonUtils.isEmpty(parseFormat)){
			return instance();
		}
		List<DateTimeFormatter> formatters;
		if (this.parseFormats!=null){
			formatters=CommonUtils.list(this.parseFormats);
		} else{
			formatters=CommonUtils.list();
		}
		if (parseFormat instanceof DateTimeFormatter){
			formatters.add(index, (DateTimeFormatter)parseFormat);
		} else if (parseFormat instanceof String){
			formatters.add(index, Java8DateUtils.getDateTimeFormatter((String)parseFormat));
		} else{
			throw new IllegalArgumentException("parseFormats must String or DateTimeFormatter.["+Arrays.toString(parseFormats)+"]");
		}
		return setParseFormats(formatters.toArray(new DateTimeFormatter[0]));
	}
	
	public S setParseFormats(final DateTimeFormatter... parseFormats) {
		if (!isEmpty(parseFormats)){
			this.parseFormats = linkedSet(parseFormats).toArray(new DateTimeFormatter[0]);
			if (this.getFormat()!=null){
				this.setFormat(CommonUtils.first(parseFormats));
			}
		} else{
			this.parseFormats = parseFormats;
		}
		return instance();
	}
	
	protected static String convertUtcFormat(final String text) {
		if (text.endsWith("+00:00")) {
			return text.substring(0, text.length() - 6) + "Z";
		}
		if (text.endsWith("+0000")) {
			return text.substring(0, text.length() - 5) + "Z";
		}
		return text;
	}
	
	@SuppressWarnings("unchecked")
	protected S instance(){
		return (S)this;
	}

	/**
	 * @return the format
	 */
	public DateTimeFormatter getFormat() {
		return format;
	}

	/**
	 * @param format the format to set
	 */
	public S setFormat(final DateTimeFormatter format) {
		this.format = format;
		return instance();
	}

	/**
	 * @param format the format to set
	 */
	public S setFormat(final String format) {
		this.format = Java8DateUtils.getDateTimeFormatter(format);
		if (this.format!=null&&CommonUtils.isEmpty(this.getParseFormats())){
			this.setParseFormats(this.format);
		}
		return instance();
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(final Object obj){
		if (!super.equals(obj)){
			return false;
		}
		if (!(obj instanceof AbstractJava8DateConverter)){
			return false;
		}
		final AbstractJava8DateConverter<T,S> con=cast(obj);
		if (!eq(this.getParseFormats(), con.getParseFormats())){
			return false;
		}
		if (!eq(this.getFormat(), con.getFormat())){
			return false;
		}
		return true;
	}
	
	/* (non-Javadoc)
	 * @see java.lang.Object#hashCode()
	 */
	@Override
	public int hashCode(){
		return this.getClass().getName().hashCode();
	}

	protected int getChronoField(final TemporalAccessor temporalAccessor, final ChronoField chronoField){
		if (temporalAccessor.isSupported(chronoField)){
			return temporalAccessor.get(chronoField);
		}
		return 0;
	}

	protected boolean hasYearToDayPart(final TemporalAccessor temporalAccessor){
		return hasYear(temporalAccessor)||hasMonth(temporalAccessor)||hasDayOfMonth(temporalAccessor);
	}

	protected boolean hasTimePart(final TemporalAccessor temporalAccessor){
		return hasHour(temporalAccessor)||hasMinute(temporalAccessor)||hasSecond(temporalAccessor)||hasNanoSecond(temporalAccessor);
	}
	
	protected boolean hasYear(final TemporalAccessor temporalAccessor){
		if (temporalAccessor.isSupported(ChronoField.YEAR_OF_ERA)){
			return true;
		}
		return temporalAccessor.isSupported(ChronoField.YEAR);
	}

	protected boolean hasMonth(final TemporalAccessor temporalAccessor){
		return temporalAccessor.isSupported(ChronoField.MONTH_OF_YEAR);
	}

	protected boolean hasDayOfMonth(final TemporalAccessor temporalAccessor){
		return temporalAccessor.isSupported(ChronoField.DAY_OF_MONTH);
	}

	protected boolean hasHour(final TemporalAccessor temporalAccessor){
		return temporalAccessor.isSupported(ChronoField.HOUR_OF_DAY);
	}

	protected boolean hasMinute(final TemporalAccessor temporalAccessor){
		return temporalAccessor.isSupported(ChronoField.MINUTE_OF_HOUR);
	}

	protected boolean hasSecond(final TemporalAccessor temporalAccessor){
		return temporalAccessor.isSupported(ChronoField.SECOND_OF_MINUTE);
	}

	protected boolean hasNanoSecond(final TemporalAccessor temporalAccessor){
		return temporalAccessor.isSupported(ChronoField.NANO_OF_SECOND);
	}

	protected boolean hasOffsetSeconds(final TemporalAccessor temporalAccessor){
		return temporalAccessor.isSupported(ChronoField.OFFSET_SECONDS);
	}
	
	protected int getYear(final TemporalAccessor temporalAccessor){
		if (temporalAccessor.isSupported(ChronoField.YEAR_OF_ERA)){
			return temporalAccessor.get(ChronoField.YEAR_OF_ERA);
		}
		return getChronoField(temporalAccessor, ChronoField.YEAR);
	}

	protected int getMonth(final TemporalAccessor temporalAccessor){
		return getChronoField(temporalAccessor, ChronoField.MONTH_OF_YEAR);
	}

	protected int getDayOfMonth(final TemporalAccessor temporalAccessor){
		return getChronoField(temporalAccessor, ChronoField.DAY_OF_MONTH);
	}

	protected int getHour(final TemporalAccessor temporalAccessor){
		return getChronoField(temporalAccessor, ChronoField.HOUR_OF_DAY);
	}

	protected int getMinute(final TemporalAccessor temporalAccessor){
		return getChronoField(temporalAccessor, ChronoField.MINUTE_OF_HOUR);
	}

	protected int getSecond(final TemporalAccessor temporalAccessor){
		return getChronoField(temporalAccessor, ChronoField.SECOND_OF_MINUTE);
	}

	protected int getNanoSecond(final TemporalAccessor temporalAccessor){
		return getChronoField(temporalAccessor, ChronoField.NANO_OF_SECOND);
	}

	protected Temporal parseTemporal(final String value, final DateTimeFormatter dateTimeFormatter) {
		final ParsePosition position = new ParsePosition(0);
		final TemporalAccessor temporalAccessor = dateTimeFormatter.parseUnresolved(value, position);
		if (position.getErrorIndex()<0){
			final int year=getYear(temporalAccessor);
			final int month=getMonth(temporalAccessor);
			final int dayOfMonth=getDayOfMonth(temporalAccessor);
			final int hour=getHour(temporalAccessor);
			final int minute=getMinute(temporalAccessor);
			final int second=getSecond(temporalAccessor);
			final int nanoOfSecond=getNanoSecond(temporalAccessor);
			final int offset=getChronoField(temporalAccessor, ChronoField.OFFSET_SECONDS);
			final ZoneId zoneId=temporalAccessor.query(TemporalQueries.zoneId());
			final ZoneOffset zoneOffset=ZoneOffset.ofTotalSeconds(offset);
			if (!hasYearToDayPart(temporalAccessor)){
				final LocalTime localTime= LocalTime.of(hour, minute, second, nanoOfSecond);
				if (zoneId==null){
					if (hasOffsetSeconds(temporalAccessor)){
						return OffsetTime.of(localTime, zoneOffset);
					} else{
						return localTime;
					}
				} else{
					if (hasOffsetSeconds(temporalAccessor)){
						return OffsetTime.of(localTime, zoneOffset);
					} else{
						return OffsetTime.of(localTime, zoneId.getRules().getOffset(Instant.now()));
					}
				}
			}
			if (!hasTimePart(temporalAccessor)){
				if (hasYear(temporalAccessor)) {
					if (hasMonth(temporalAccessor)) {
						if (hasDayOfMonth(temporalAccessor)) {
							return LocalDate.of(year, month, dayOfMonth);
						} else {
							return YearMonth.of(year, month);
						}
					} else {
						return Year.of(year);
					}
				}
			}
			if (zoneId==null&&!hasOffsetSeconds(temporalAccessor)){
				return LocalDateTime.of(year, month, dayOfMonth, hour, minute, second, nanoOfSecond);
			}
			if (hasOffsetSeconds(temporalAccessor)){
				if (zoneId==null){
					return OffsetDateTime.of(year, month, dayOfMonth, hour, minute, second, nanoOfSecond, zoneOffset);
				} else{
					final LocalDateTime localDateTime=LocalDateTime.of(year, month, dayOfMonth, hour, minute, second, nanoOfSecond);
					return ZonedDateTime.ofStrict(localDateTime, zoneOffset, zoneId);
				}
			}
			if (zoneId==null){
				return LocalDateTime.of(year, month, dayOfMonth, hour, minute, second);
			} else{
				return ZonedDateTime.of(year, month, dayOfMonth, hour, minute, second, nanoOfSecond, zoneId);
			}
		} else{
			return null;
		}
	}
	
	protected ZoneId getDefaultZoneId(){
		if (isUseSystemZone()){
			return ZoneId.systemDefault();
		} else{
			return INSTANT_ZONE_ID;
		}
	}
	
	protected ZoneOffset getDefaultZoneOffset(){
		if (isUseSystemZone()){
			return ZoneId.systemDefault().getRules().getOffset(Instant.now());
		} else{
			return INSTANT_ZONE_OFFSET;
		}
	}

	protected ZonedDateTime toZonedDateTime(final Instant date){
		return ZonedDateTime.ofInstant(date, getDefaultZoneId());
	}

	protected ZonedDateTime toZonedDateTime(final Number value){
		final Instant ins= Instant.ofEpochMilli(value.longValue());
		return toZonedDateTime(ins);
	}
	
	protected ZonedDateTime toZonedDateTime(final OffsetDateTime date){
		return ZonedDateTime.of(date.toLocalDateTime(), date.getOffset());
	}

	protected ZonedDateTime toZonedDateTime(final LocalDateTime date){
		return ZonedDateTime.of(date, getDefaultZoneId());
	}
	
	protected ZonedDateTime toZonedDateTime(final ChronoLocalDate date){
		if (date instanceof LocalDate){
			return toZonedDateTime((LocalDate)date);
		}
		return toZonedDateTime(LocalDateTime.of(getYear(date), getMonth(date), getDayOfMonth(date)
				, 0, 0));
	}

	protected ZonedDateTime toZonedDateTime(final LocalTime time){
		return ZonedDateTime.of(LocalDateTime.of(EPOC_DAY, time), getDefaultZoneId());
	}

	protected ZonedDateTime toZonedDateTime(final OffsetTime time){
		return ZonedDateTime.of(LocalDateTime.of(EPOC_DAY, time.toLocalTime()), time.getOffset());
	}
	
	protected ZonedDateTime toZonedDateTime(final Calendar cal){
		if (cal instanceof GregorianCalendar){
			return ((GregorianCalendar)cal).toZonedDateTime();
		}
		final ZoneId zoneId=cal.getTimeZone().toZoneId();
		return ZonedDateTime.ofInstant(cal.toInstant(), zoneId);
	}

	protected ZonedDateTime toZonedDateTime(final LocalDate date){
		return ZonedDateTime.of(toLocalDateTime(date), getDefaultZoneId());
	}

	protected ZonedDateTime toZonedDateTime(final YearMonth date){
		return ZonedDateTime.of(toLocalDateTime(date), getDefaultZoneId());
	}

	protected ZonedDateTime toZonedDateTime(final Year date){
		return ZonedDateTime.of(toLocalDateTime(date), getDefaultZoneId());
	}

	protected LocalDateTime toLocalDateTime(final LocalDate date){
		return LocalDateTime.of(date.getYear(), date.getMonth(), date.getDayOfMonth(), 0, 0);
	}

	protected LocalDateTime toLocalDateTime(final YearMonth date){
		return LocalDateTime.of(date.getYear(), date.getMonth(), 1, 0, 0);
	}

	protected LocalDateTime toLocalDateTime(final Year date){
		return LocalDateTime.of(date.getValue(), 1, 1, 0, 0);
	}

	protected OffsetDateTime toOffsetDateTime(final LocalDateTime date){
		return OffsetDateTime.of(date, getDefaultZoneOffset());
	}

	protected ZonedDateTime toZonedDateTime(final Temporal temporal){
		if (temporal==null){
			return null;
		}else if (temporal instanceof ZonedDateTime){
			return ZonedDateTime.class.cast(temporal);
		}else if (temporal instanceof Instant){
			return toZonedDateTime((Instant)temporal);
		} if (temporal instanceof OffsetDateTime){
			return toZonedDateTime((OffsetDateTime)temporal);
		} if (temporal instanceof LocalDateTime){
			return toZonedDateTime((LocalDateTime)temporal);
		} if (temporal instanceof ChronoLocalDate){
			return toZonedDateTime((ChronoLocalDate)temporal);
		} if (temporal instanceof LocalTime){
			return toZonedDateTime((LocalTime)temporal);
		} if (temporal instanceof OffsetTime){
			return toZonedDateTime((OffsetTime)temporal);
		}
		throw new IllegalArgumentException("temporal can not convert ZonedDateTime. temporal=["+temporal+"]");
	}

	protected OffsetTime toOffsetTime(final ZonedDateTime dateTime){
		return OffsetTime.ofInstant(dateTime.toInstant(), dateTime.getZone());
	}

	
	@Override
	@SuppressWarnings("unchecked")
	public AbstractJava8DateConverter<T,S> clone(){
		try {
			return (AbstractJava8DateConverter<T,S>)super.clone();
		} catch (final CloneNotSupportedException e) {
			throw new RuntimeException(e);
		}
	}

	protected boolean isCurrentText(final String text){
		if("now".equals(text)){
			return true;
		} else if(text.startsWith("current")){
			return true;
		} else if(text.startsWith("sys")){
			return true;
		}
		return false;
	}
	
	protected boolean isNumberPattern(final String text){
		final Matcher matcher = NUMBER_PATTERN.matcher(text);
		return matcher.matches();
	}
	
	protected Instant toInstant(final String value){
		final Instant ins = Instant.ofEpochMilli(
				Long.parseLong(value));
		return ins;
	}
	
	/* (non-Javadoc)
	 * @see com.sqlapp.data.converter.Converter#copy(java.lang.Object)
	 */
	@Override
	@SuppressWarnings("unchecked")
	public T copy(final Object obj){
		if (obj==null){
			return null;
		}
		return (T)obj;
	}
}
