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
import static com.sqlapp.util.CommonUtils.isEmpty;

import java.time.Duration;
import java.time.LocalTime;
import java.time.chrono.ChronoPeriod;
import java.time.format.DateTimeParseException;
import java.time.temporal.Temporal;
import java.util.Calendar;
import java.util.function.Supplier;

import com.sqlapp.data.interval.Interval;
import com.sqlapp.util.DateUtils;

/**
 * java.time.Duration converter 複数の日付フォーマットをサポート
 */
public class DurationConverter extends AbstractConverter<Duration> implements Supplier<Duration> {

	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = 1212274814940098554L;

	private static LocalTimeConverter LOCAL_TIME_CONVERTER = new LocalTimeConverter();

	@Override
	public Duration convertObject(final Object value) {
		if (isEmpty(value)) {
			return getDefaultValue();
		}
		if (isSupplier(value)) {
			return convertObject(getSupplierValue(value));
		} else if (value instanceof Duration) {
			return (Duration) value;
		} else if (value instanceof ChronoPeriod) {
			return fromPeriod(ChronoPeriod.class.cast(value), value);
		} else if (value instanceof Temporal) {
			// time of day as the elapsed time since midnight (01:30:15 is PT1H30M15S)
			final LocalTime localTime = LOCAL_TIME_CONVERTER.convertObject(value);
			return Duration.ofSeconds(localTime.toSecondOfDay(), localTime.getNano());
		} else if (value instanceof Calendar) {
			final Calendar cst = Calendar.class.cast(value);
			return Duration.ofSeconds(toSecond(cst), toNano(cst));
		} else if (value instanceof java.sql.Timestamp timestamp) {
			// keep the nanoseconds: the Calendar path below has only milliseconds
			final LocalTime localTime = timestamp.toLocalDateTime().toLocalTime();
			return Duration.ofSeconds(localTime.toSecondOfDay(), localTime.getNano());
		} else if (value instanceof java.util.Date) {
			// any other java.util.Date (java.sql.Date, java.sql.Time, ...): milliseconds
			final java.util.Date dt = java.util.Date.class.cast(value);
			final Calendar cst = DateUtils.toCalendar(dt);
			return Duration.ofSeconds(toSecond(cst), toNano(cst));
		} else if (value instanceof String) {
			final String lowerVal = ((String) value).toLowerCase();
			if (lowerVal.length() >= 2 && lowerVal.startsWith("'") && lowerVal.endsWith("'")) {
				final String val = cast(value);
				return parse(val.substring(1, val.length() - 1));
			}
			return parse((String) value);
		}
		return parse(value.toString());
	}

	private long toNano(final Calendar cst) {
		return cst.get(Calendar.MILLISECOND) * 1000000;
	}

	private long toSecond(final Calendar cst) {
		final long val = cst.get(Calendar.SECOND) + cst.get(Calendar.MINUTE) * 60
				+ cst.get(Calendar.HOUR_OF_DAY) * 3600;
		return val;
	}

	public static DurationConverter newInstance() {
		final DurationConverter dateConverter = new DurationConverter();
		return dateConverter;
	}

	/** [+-][days ]hours:minutes[:seconds[.fraction]] such as "01:30:00" or "1 02:00:00.5" */
	private static final java.util.regex.Pattern CLOCK_TEXT = java.util.regex.Pattern
			.compile("([+-])?(?:(\\d+)\\s+)?(\\d+):(\\d{1,2})(?::(\\d{1,2})(?:\\.(\\d{1,9}))?)?");

	private Duration parse(final String text) {
		try {
			return Duration.parse(text);
		} catch (final DateTimeParseException e) {
			final java.util.regex.Matcher clock = CLOCK_TEXT.matcher(text.trim());
			if (clock.matches()) {
				Duration duration = Duration.ofHours(Long.parseLong(clock.group(3)))
						.plusMinutes(Long.parseLong(clock.group(4)));
				if (clock.group(2) != null) {
					duration = duration.plusDays(Long.parseLong(clock.group(2)));
				}
				if (clock.group(5) != null) {
					duration = duration.plusSeconds(Long.parseLong(clock.group(5)));
				}
				if (clock.group(6) != null) {
					duration = duration.plusNanos(Long.parseLong((clock.group(6) + "00000000").substring(0, 9)));
				}
				return "-".equals(clock.group(1)) ? duration.negated() : duration;
			}
			return fromInterval(Interval.parse(text), text);
		}
	}

	/**
	 * Average lengths used for years and months, as PostgreSQL does for
	 * {@code EXTRACT(EPOCH FROM interval)}: a year is 365.25 days and a month is
	 * 30 days.
	 */
	static final Duration YEAR_LENGTH = Duration.ofSeconds(31_557_600L);

	static final Duration MONTH_LENGTH = Duration.ofDays(30);

	/**
	 * Days, hours, minutes, seconds and the sign are kept ("10 12:4:13" day to
	 * second is PT252H4M13S). Years and months have no fixed length; they are
	 * converted with {@link #YEAR_LENGTH} and {@link #MONTH_LENGTH} so that the
	 * value is converted instead of being dropped.
	 */
	private static Duration fromInterval(final Interval interval, final Object original) {
		final Duration duration = YEAR_LENGTH.multipliedBy(interval.getYears())
				.plus(MONTH_LENGTH.multipliedBy(interval.getMonths())).plusDays(interval.getDays())
				.plusHours(interval.getHours())
				.plusMinutes(interval.getMinutes()).plusSeconds(interval.getSeconds()).plusNanos(interval.getNanos());
		return interval.isPositive() ? duration : duration.negated();
	}

	/**
	 * Period.ofDays(1) is PT24H; years and months use {@link #YEAR_LENGTH} and
	 * {@link #MONTH_LENGTH}.
	 */
	private static Duration fromPeriod(final ChronoPeriod period, final Object original) {
		Duration duration = Duration.ZERO;
		for (final java.time.temporal.TemporalUnit unit : period.getUnits()) {
			final long amount = period.get(unit);
			if (amount == 0) {
				continue;
			}
			if (unit == java.time.temporal.ChronoUnit.DAYS) {
				duration = duration.plusDays(amount);
			} else if (unit == java.time.temporal.ChronoUnit.YEARS) {
				duration = duration.plus(YEAR_LENGTH.multipliedBy(amount));
			} else if (unit == java.time.temporal.ChronoUnit.MONTHS) {
				duration = duration.plus(MONTH_LENGTH.multipliedBy(amount));
			} else if (!unit.isDurationEstimated()) {
				duration = duration.plus(amount, unit);
			} else {
				throw new ConverterException("Cannot convert a period with " + unit + " to Duration: " + original);
			}
		}
		return duration;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(final Object obj) {
		if (obj == this) {
			return true;
		}
		if (!(obj instanceof DurationConverter)) {
			return false;
		}
		return true;
	}

	@Override
	public Duration get() {
		return Duration.ofNanos(0);
	}

	@Override
	public Duration copy(final Object obj) {
		if (obj == null) {
			return null;
		}
		return (Duration) obj;
	}
}
