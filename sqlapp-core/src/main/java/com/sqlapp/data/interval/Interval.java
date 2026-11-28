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

package com.sqlapp.data.interval;

import java.io.Serializable;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Duration;
import java.time.chrono.ChronoLocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Calendar;
import java.util.Date;
import java.util.Objects;

/**
 * Mutable compatibility facade for SQL intervals. Computation delegates to
 * IntervalValue; the original four fields and serialVersionUID remain the wire
 * format. Nonzero constructor components must share one sign. Component setters
 * accept magnitudes and preserve the explicitly selected sign.
 */
public class Interval implements Serializable, Cloneable, Comparable<Interval> {
	private static final long serialVersionUID = -5789019381885490073L;
	protected static final DecimalFormat secondsFormat;
	static {
		secondsFormat = new DecimalFormat("0.#########");
		final DecimalFormatSymbols symbols = secondsFormat.getDecimalFormatSymbols();
		symbols.setDecimalSeparator('.');
		secondsFormat.setDecimalFormatSymbols(symbols);
	}
	// Stored totals are reconstructed as an IntervalValue for arithmetic.
	private boolean positive = true;
	private int months;
	private int seconds;
	private int nanos;
	protected static final int NANO_LIMIT = 1000000000;
	protected static final int SECOND_LIMIT = 60;
	protected static final int MINUTE_LIMIT = 60;
	protected static final int HOUR_LIMIT = 24;
	protected static final int MONTH_LIMIT = 12;
	protected static final int MINUTE_SECONDS = SECOND_LIMIT;
	protected static final int HOUR_SECONDS = MINUTE_LIMIT * SECOND_LIMIT;
	protected static final int HOUR_MINUTES = MINUTE_LIMIT;
	protected static final int DAY_SECONDS = HOUR_LIMIT * HOUR_SECONDS;
	protected static final String TO = "TO";
	protected static final String YEAR = "YEAR";
	protected static final String MONTH = "MONTH";
	protected static final String DAY = "DAY";
	protected static final String HOUR = "HOUR";
	protected static final String MINUTE = "MINUTE";
	protected static final String SECOND = "SECOND";
	protected static final String YEAR_TO_MONTH = getTypeName(YEAR, MONTH);
	protected static final String MONTH_TO_DAY = getTypeName(MONTH, DAY);
	protected static final String YEAR_TO_DAY = getTypeName(YEAR, DAY);
	protected static final String DAY_TO_SECOND = getTypeName(DAY, SECOND);
	protected static final String HOUR_TO_MINUTE = getTypeName(HOUR, MINUTE);
	protected static final String HOUR_TO_SECOND = getTypeName(HOUR, SECOND);
	protected static final String MINUTE_TO_SECOND = getTypeName(MINUTE, SECOND);

	protected static String getTypeName(final String first, final String last) {
		return first + " " + TO + " " + last;
	}

	public Interval() {
	}

	public Interval(final int years, final int months, final int days, final int hours,
			final int minutes, final int seconds, final long nanos) {
		store(LegacyIntervalSupport.components(years, months, days, hours, minutes, BigDecimal.valueOf(seconds), nanos));
	}

	public Interval(final int years, final int months, final int days, final int hours,
			final int minutes, final double seconds) {
		store(LegacyIntervalSupport.components(years, months, days, hours, minutes, LegacyIntervalSupport.decimal(seconds), 0));
	}

	public Interval(final int years, final int months, final int days, final int hours,
			final int minutes, final int seconds) {
		this(years, months, days, hours, minutes, seconds, 0);
	}

	/** Reconstructs the common value without relying on overridden component getters. */
	public IntervalValue toIntervalValue() {
		final int sign = positive ? 1 : -1;
		return new IntervalValue((long) months * sign, (long) (seconds / DAY_SECONDS) * sign,
				Duration.ofSeconds(seconds % DAY_SECONDS, nanos).multipliedBy(sign));
	}

	/** Preserves the established type restrictions of legacy subclasses. */
	protected IntervalValue constrainValue(final IntervalValue value) {
		return value;
	}

	private void store(final IntervalValue input) {
		final IntervalValue value = constrainValue(input);
		final Duration time = Duration.ofDays(value.days()).plus(value.timePart());
		final int monthSign = Long.signum(value.months());
		final int timeSign = time.isZero() ? 0 : time.isNegative() ? -1 : 1;
		if (monthSign != 0 && timeSign != 0 && monthSign != timeSign) {
			throw new IllegalArgumentException("Legacy Interval cannot retain mixed month/time signs; use IntervalValue");
		}
		final int newMonths = Math.toIntExact(Math.absExact(value.months()));
		final Duration magnitude = time.isNegative() ? time.negated() : time;
		final int newSeconds = Math.toIntExact(magnitude.getSeconds());
		// Validate everything before committing a mutation.
		this.months = newMonths;
		this.seconds = newSeconds;
		this.nanos = magnitude.getNano();
		this.positive = monthSign >= 0 && timeSign >= 0;
	}

	public Interval scale(final int scale) {
		final boolean negativeZero = !positive && months == 0 && seconds == 0 && nanos == 0;
		store(toIntervalValue().multipliedBy(scale));
		if (negativeZero && scale > 0) {
			positive = false;
		}
		return this;
	}

	protected void setValue(final int months, final double seconds) {
		setMagnitude(Math.absExact((long) months), LegacyIntervalSupport.duration(LegacyIntervalSupport.decimal(seconds).abs()));
	}

	protected void setValue(final int months, final long seconds, final long nanos) {
		setMagnitude(Math.absExact((long) months), Duration.ofSeconds(Math.absExact(seconds), Math.absExact(nanos)));
	}

	private void setMagnitude(final long months, final Duration time) {
		final boolean sign = positive;
		store(new IntervalValue(months, 0, time));
		positive = sign;
	}

	public Timestamp add(final Timestamp timestamp) {
		return add(timestamp, 1);
	}

	/** Adds total months, then days, then exact time; never changes the input. */
	public Timestamp add(final Timestamp timestamp, final int multiplicity) {
		Objects.requireNonNull(timestamp, "timestamp");
		final IntervalValue value = toIntervalValue().multipliedBy(multiplicity);
		final Calendar calendar = Calendar.getInstance();
		calendar.setTimeInMillis(timestamp.getTime());
		calendar.set(Calendar.MILLISECOND, 0);
		addDateParts(calendar, value);
		return Timestamp.from(calendar.toInstant().plusNanos(timestamp.getNanos()).plus(value.timePart()));
	}

	public Calendar add(final Calendar calendar) {
		return add(calendar, 1);
	}

	/** Adds total months, days, then time at Calendar's millisecond precision. */
	public Calendar add(final Calendar calendar, final int multiplicity) {
		Objects.requireNonNull(calendar, "calendar");
		final IntervalValue value = toIntervalValue().multipliedBy(multiplicity);
		final Calendar result = (Calendar) calendar.clone();
		addDateParts(result, value);
		final long millis = value.timePart().toMillis();
		result.add(Calendar.SECOND, Math.toIntExact(millis / 1000));
		result.add(Calendar.MILLISECOND, (int) (millis % 1000));
		return result;
	}

	private static void addDateParts(final Calendar calendar, final IntervalValue value) {
		calendar.add(Calendar.MONTH, Math.toIntExact(value.months()));
		calendar.add(Calendar.DATE, Math.toIntExact(value.days()));
	}

	public Date add(final Date date) {
		return add(date, 1);
	}

	public Date add(final Date date, final int multiplicity) {
		final Calendar calendar = Calendar.getInstance();
		calendar.setTime(date);
		return add(calendar, multiplicity).getTime();
	}

	public java.sql.Date add(final java.sql.Date date) {
		return add(date, 1);
	}

	public java.sql.Date add(final java.sql.Date date, final int multiplicity) {
		return new java.sql.Date(add((Date) date, multiplicity).getTime());
	}

	public java.sql.Time add(final java.sql.Time time, final int multiplicity) {
		return new java.sql.Time(add((Date) time, multiplicity).getTime());
	}

	public java.sql.Time add(final java.sql.Time time) {
		return add(time, 1);
	}

	@SuppressWarnings("unchecked")
	public <T extends java.time.temporal.Temporal> T add(final T temporal, final int multiplicity) {
		Objects.requireNonNull(temporal, "temporal");
		final IntervalValue value = toIntervalValue().multipliedBy(multiplicity);
		java.time.temporal.Temporal result = temporal;
		if (value.months() != 0) {
			result = result.plus(value.months(), ChronoUnit.MONTHS);
		}
		if (value.days() != 0 && result.isSupported(ChronoUnit.DAYS)) {
			result = result.plus(value.days(), ChronoUnit.DAYS);
		}
		if (result instanceof ChronoLocalDate) {
			// Retain the legacy date-only projection: sub-day time is ignored.
			result = result.plus(value.timePart().dividedBy(Duration.ofDays(1)), ChronoUnit.DAYS);
		} else {
			final Duration time = result.isSupported(ChronoUnit.DAYS) ? value.timePart()
					: Duration.ofDays(value.days()).plus(value.timePart());
			if (!time.isZero()) {
				result = result.plus(time);
			}
		}
		return (T) result;
	}

	public java.time.Month add(final java.time.Month temporal, final int multiplicity) {
		return temporal.plus(toIntervalValue().multipliedBy(multiplicity).months());
	}

	/** MonthDay arithmetic uses leap year 2000 as the explicit reference calendar. */
	public java.time.MonthDay add(final java.time.MonthDay temporal, final int multiplicity) {
		final IntervalValue value = toIntervalValue().multipliedBy(multiplicity);
		return java.time.MonthDay.from(temporal.atYear(2000).plusMonths(value.months())
				.plusDays(Math.addExact(value.days(), value.timePart().dividedBy(Duration.ofDays(1)))));
	}

	public java.time.YearMonth add(final java.time.YearMonth temporal, final int multiplicity) {
		return temporal.plusMonths(toIntervalValue().multipliedBy(multiplicity).months());
	}

	public <T extends java.time.temporal.Temporal> T add(final T temporal) {
		return add(temporal, 1);
	}

	public java.time.Month add(final java.time.Month temporal) {
		return add(temporal, 1);
	}

	public java.time.YearMonth add(final java.time.YearMonth temporal) {
		return add(temporal, 1);
	}

	/** Adds this interval to the argument, preserving the original mutating API. */
	public void add(final Interval interval) {
		Objects.requireNonNull(interval, "interval");
		interval.store(interval.toIntervalValue().plus(toIntervalValue()));
	}

	public static Interval parse(final String text) {
		if (text == null || text.trim().isEmpty()) {
			return null;
		}
		final Interval result = parseDetail(text);
		if (result == null) {
			throw new IllegalArgumentException("Interval units are required; use a typed interval parser for a bare value");
		}
		return result;
	}

	public static boolean isParsable(final String text) {
		if (text == null || text.trim().isEmpty()) {
			return false;
		}
		try {
			return parseDetail(text) != null;
		} catch (IllegalArgumentException | ArithmeticException | java.time.DateTimeException exception) {
			return false;
		}
	}

	protected static Interval parseDetail(final String text) {
		IntervalValue value = LegacyIntervalSupport.qualified(text);
		if (value == null) {
			String bare = text.trim();
			if (bare.startsWith("'") && bare.endsWith("'") && bare.length() >= 2) {
				bare = bare.substring(1, bare.length() - 1).trim();
			}
			if (!LegacyIntervalSupport.complete(bare)) {
				return null;
			}
			value = LegacyIntervalSupport.parse(bare, YEAR, SECOND);
		}
		final Interval result = new Interval();
		result.store(value);
		return result;
	}

	protected static Interval parse(final String text, final String firstUnit, final String lastUnit) {
		final Interval result = new Interval();
		result.store(LegacyIntervalSupport.parse(text, firstUnit, lastUnit));
		return result;
	}

	protected static void setValue(final Interval interval, final String unit, final String value) {
		final double number = new BigDecimal(value).doubleValue();
		if (YEAR.equalsIgnoreCase(unit)) interval.setYears(number);
		else if (MONTH.equalsIgnoreCase(unit)) interval.setMonths(new BigDecimal(value).intValueExact());
		else if (DAY.equalsIgnoreCase(unit)) interval.setDays(number);
		else if (HOUR.equalsIgnoreCase(unit)) interval.setHours(number);
		else if (MINUTE.equalsIgnoreCase(unit)) interval.setMinutes(number);
		else if (SECOND.equalsIgnoreCase(unit)) {
			final Duration time = LegacyIntervalSupport.duration(new BigDecimal(value).abs());
			interval.setSeconds(Math.toIntExact(time.getSeconds()));
			interval.setNanos(time.getNano());
		} else throw new IllegalArgumentException("Unknown interval unit: " + unit);
	}

	protected static void setValue(final Interval interval, final String unit, final int value) {
		if (YEAR.equalsIgnoreCase(unit)) interval.setYears(value);
		else if (MONTH.equalsIgnoreCase(unit)) interval.setMonths(value);
		else if (DAY.equalsIgnoreCase(unit)) interval.setDays(value);
		else if (HOUR.equalsIgnoreCase(unit)) interval.setHours(value);
		else if (MINUTE.equalsIgnoreCase(unit)) interval.setMinutes(value);
		else if (SECOND.equalsIgnoreCase(unit)) interval.setSeconds(value);
		else interval.setNanos(value);
	}

	@Override
	public boolean equals(final Object object) {
		return object instanceof Interval other && positive == other.positive && months == other.months
				&& seconds == other.seconds && nanos == other.nanos;
	}

	@Override
	public int hashCode() {
		return Objects.hash(positive, months, seconds, nanos);
	}

	@Override
	public Interval clone() {
		try {
			return (Interval) super.clone();
		} catch (CloneNotSupportedException exception) {
			throw new AssertionError(exception);
		}
	}

	public int getYears() {
		return months / MONTH_LIMIT;
	}
	public int getMonths() {
		return months % MONTH_LIMIT;
	}
	public int getMonthsFull() {
		return months;
	}
	public int getDays() {
		return seconds / DAY_SECONDS;
	}
	public int getHours() {
		return seconds % DAY_SECONDS / HOUR_SECONDS;
	}
	protected int getHoursFull() {
		return seconds / HOUR_SECONDS;
	}
	public int getMinutes() {
		return seconds % HOUR_SECONDS / MINUTE_SECONDS;
	}
	protected int getMinutesFull() {
		return seconds / MINUTE_SECONDS;
	}
	public int getSeconds() {
		return seconds % SECOND_LIMIT;
	}
	public int getSecondsFull() {
		return seconds;
	}
	public int getMilliseconds() {
		return nanos / 1_000_000;
	}
	public int getNanos() {
		return nanos;
	}
	public boolean isPositive() {
		return positive;
	}
	public void setNegative() {
		positive = false;
	}
	public void setPositive() {
		positive = true;
	}

	public void setYears(final int years) {
		setMagnitude(Math.addExact(Math.multiplyExact(Math.absExact((long) years), 12), months % 12),
				Duration.ofSeconds(seconds, nanos));
	}

	public void setYears(final double years) {
		setMagnitude(LegacyIntervalSupport.decimal(years).abs().multiply(BigDecimal.valueOf(12)).toBigInteger().longValueExact(),
				Duration.ofSeconds(seconds, nanos));
	}

	/** Values below 12 replace the month component; values >=12 replace total months. */
	public void setMonths(final int months) {
		final long value = Math.absExact((long) months);
		setMagnitude(value >= 12 ? value : this.months / 12 * 12L + value, Duration.ofSeconds(seconds, nanos));
	}

	public void setDays(final int days) {
		replaceTime(BigDecimal.valueOf(days).abs(), DAY_SECONDS, false);
	}
	public void setDays(final double days) {
		replaceTime(LegacyIntervalSupport.decimal(days).abs(), DAY_SECONDS, true);
	}
	public void setHours(final int hours) {
		replaceTime(BigDecimal.valueOf(hours).abs(), HOUR_SECONDS, false);
	}
	public void setHours(final double hours) {
		replaceTime(LegacyIntervalSupport.decimal(hours).abs(), HOUR_SECONDS, true);
	}
	public void setMinutes(final int minutes) {
		replaceTime(BigDecimal.valueOf(minutes).abs(), MINUTE_SECONDS, false);
	}
	public void setMinutes(final double minutes) {
		replaceTime(LegacyIntervalSupport.decimal(minutes).abs(), MINUTE_SECONDS, true);
	}
	public void setSeconds(final int seconds) {
		replaceTime(BigDecimal.valueOf(seconds).abs(), 1, false);
	}
	public void setSeconds(final double seconds) {
		replaceTime(LegacyIntervalSupport.decimal(seconds).abs(), 1, true);
	}

	private void replaceTime(final BigDecimal component, final int unitSeconds, final boolean decimal) {
		final Duration replacement = LegacyIntervalSupport.duration(component.multiply(BigDecimal.valueOf(unitSeconds)));
		final long whole = replacement.getSeconds();
		long boundary = unitSeconds == DAY_SECONDS ? Long.MAX_VALUE : unitSeconds == HOUR_SECONDS ? DAY_SECONDS
				: unitSeconds == MINUTE_SECONDS ? HOUR_SECONDS : MINUTE_SECONDS;
		while (boundary <= whole && boundary < DAY_SECONDS) {
			boundary = boundary == MINUTE_SECONDS ? HOUR_SECONDS : DAY_SECONDS;
		}
		final long higher = boundary == Long.MAX_VALUE || whole >= DAY_SECONDS ? 0 : seconds / boundary * boundary;
		final boolean replaceLower = decimal && (unitSeconds == 1 || component.remainder(BigDecimal.ONE).signum() != 0);
		final long lower = replaceLower ? 0 : seconds % unitSeconds;
		final Duration result = Duration.ofSeconds(Math.addExact(higher, Math.addExact(whole, lower)),
				replaceLower ? replacement.getNano() : nanos);
		setMagnitude(months, result);
	}

	public double getSecondsAsDouble() {
		return getSeconds() + (double) nanos / NANO_LIMIT;
	}
	public void setNanos(final int nanos) {
		setNanos((long) nanos);
	}
	public void setNanos(final long nanos) {
		setMagnitude(months, Duration.ofSeconds(seconds, Math.absExact(nanos)));
	}

	/** Legacy signed lexicographic order (months, seconds, nanos), not SQL ordering. */
	@Override
	public int compareTo(final Interval other) {
		if (other == null) return 1;
		if (positive != other.positive) return Boolean.compare(positive, other.positive);
		int result = Integer.compare(months, other.months);
		if (result == 0) result = Integer.compare(seconds, other.seconds);
		if (result == 0) result = Integer.compare(nanos, other.nanos);
		return positive ? result : -result;
	}

	public Interval toInterval() {
		final Interval copy = new Interval();
		cloneProperties(copy);
		return copy;
	}

	protected void cloneProperties(final Interval clone) {
		clone.months = months;
		clone.seconds = seconds;
		clone.nanos = nanos;
		clone.positive = positive;
	}

	/** Exact locale-independent legacy seconds text, shared by time facades. */
	protected final String formatSeconds(final long wholeSeconds) {
		return BigDecimal.valueOf(wholeSeconds).add(BigDecimal.valueOf(nanos, 9)).stripTrailingZeros().toPlainString();
	}

	@Override
	public String toString() {
		return (positive ? "" : "-") + getYears() + "-" + getMonths() + "-" + getDays() + " "
				+ getHours() + ":" + getMinutes() + ":" + formatSeconds(getSeconds());
	}

	protected int truncate(final int value, final int sub) {
		return value / sub * sub;
	}
	protected long truncate(final long value, final long sub) {
		return value / sub * sub;
	}
}
