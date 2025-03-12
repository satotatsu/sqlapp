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

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Month;
import java.time.MonthDay;
import java.time.OffsetDateTime;
import java.time.Year;
import java.time.YearMonth;
import java.time.ZonedDateTime;
import java.time.chrono.ChronoLocalDate;
import java.time.chrono.HijrahDate;
import java.time.chrono.JapaneseDate;
import java.time.chrono.MinguoDate;
import java.time.chrono.ThaiBuddhistDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoField;
import java.time.temporal.ChronoUnit;
import java.time.temporal.Temporal;
import java.util.List;
import java.util.Map;

/**
 * 日付関係のユーティリティ
 * 
 * @author SATOH
 *
 */
public final class Java8DateUtils {
	private Java8DateUtils() {
	}

	/**
	 * 現在の日を取得します
	 * 
	 * @return 現在の日
	 */
	public static LocalDate currentDate() {
		return LocalDate.now();
	}

	/**
	 * 現在の日時を取得します
	 * 
	 * @return 現在の日時
	 */
	public static LocalDateTime currentDateTime() {
		return LocalDateTime.now();
	}

	/**
	 * 現在の日時(タイムスタンプ)を取得します
	 * 
	 * @return 現在の日時
	 */
	public static LocalDateTime currentTimestamp() {
		return LocalDateTime.now();
	}

	/**
	 * 現在の時間を取得します
	 * 
	 * @return 現在の時間
	 */
	public static LocalTime currentTime() {
		return LocalTime.now();
	}

	/**
	 * ミリ秒以下を切り捨てます
	 * 
	 * @param date 日付
	 * @return 時刻情報を切り捨てた日付
	 */
	public static LocalTime truncateMilisecond(final LocalTime date) {
		if (date == null) {
			return null;
		}
		return date.truncatedTo(ChronoUnit.SECONDS);
	}

	/**
	 * ミリ秒以下を切り捨てます
	 * 
	 * @param date 日付
	 * @return 時刻情報を切り捨てた日付
	 */
	public static LocalDateTime truncateMilisecond(final LocalDateTime date) {
		if (date == null) {
			return null;
		}
		return date.truncatedTo(ChronoUnit.SECONDS);
	}

	/**
	 * ミリ秒以下を切り捨てます
	 * 
	 * @param date 日付
	 * @return 時刻情報を切り捨てた日付
	 */
	public static OffsetDateTime truncateMilisecond(final OffsetDateTime date) {
		if (date == null) {
			return null;
		}
		return date.truncatedTo(ChronoUnit.SECONDS);
	}

	/**
	 * ミリ秒を切り捨てます
	 * 
	 * @param date 日付
	 * @return 時刻情報を切り捨てた日付
	 */
	public static ZonedDateTime truncateMilisecond(final ZonedDateTime date) {
		if (date == null) {
			return null;
		}
		return date.truncatedTo(ChronoUnit.SECONDS);
	}

	/**
	 * 時刻情報を切り捨てます
	 * 
	 * @param date 日付
	 * @return 時刻情報を切り捨てた日付
	 */
	public static OffsetDateTime truncateTime(final OffsetDateTime date) {
		if (date == null) {
			return null;
		}
		return date.truncatedTo(ChronoUnit.DAYS);
	}

	/**
	 * 時刻情報を切り捨てます
	 * 
	 * @param date 日付
	 * @return 時刻情報を切り捨てた日付
	 */
	public static ZonedDateTime truncateTime(final ZonedDateTime date) {
		if (date == null) {
			return null;
		}
		return date.truncatedTo(ChronoUnit.DAYS);
	}

	/**
	 * 時刻情報を切り捨てます
	 * 
	 * @param date 日付
	 * @return 時刻情報を切り捨てた日付
	 */
	public static LocalDateTime truncateTime(final LocalDateTime date) {
		if (date == null) {
			return null;
		}
		return date.truncatedTo(ChronoUnit.DAYS);
	}

	/**
	 * ナノ秒の加算を実行します
	 * 
	 * @param date   時刻
	 * @param millis ナノ加算する秒
	 * @return ナノ秒を加算した結果
	 */
	@SuppressWarnings("unchecked")
	public static <T extends Temporal> T addNanoSeconds(final T date, final int nanos) {
		if (date == null) {
			return null;
		}
		return (T) date.plus(Duration.ofNanos(nanos));
	}

	/**
	 * ミリ秒の加算を実行します
	 * 
	 * @param date   時刻
	 * @param millis ミリ加算する秒
	 * @return ミリ秒を加算した結果
	 */
	@SuppressWarnings("unchecked")
	public static <T extends Temporal> T addMilliSeconds(final T date, final int millis) {
		if (date == null) {
			return null;
		}
		return (T) date.plus(Duration.ofMillis(millis));
	}

	/**
	 * マイクロ秒の加算を実行します
	 * 
	 * @param date   時刻
	 * @param micros マイクロ秒加算する秒
	 * @return 加算した結果
	 */
	@SuppressWarnings("unchecked")
	public static <T extends Temporal> T addMicroSeconds(final T date, final int micros) {
		if (date == null) {
			return null;
		}
		return (T) date.plus(Duration.ofNanos(micros * 1000));
	}

	/**
	 * 秒の加算を実行します
	 * 
	 * @param date    時刻
	 * @param seconds 加算する秒
	 * @return 秒を加算した結果
	 */
	@SuppressWarnings("unchecked")
	public static <T extends Temporal> T addSeconds(final T date, final int seconds) {
		if (date == null) {
			return null;
		}
		return (T) date.plus(Duration.ofSeconds(seconds));
	}

	/**
	 * 分の加算を実行します
	 * 
	 * @param date    時刻
	 * @param minutes 加算する分
	 * @return 分を加算した結果
	 */
	@SuppressWarnings("unchecked")
	public static <T extends Temporal> T addMinutes(final T date, final int minutes) {
		if (date == null) {
			return null;
		}
		return (T) date.plus(Duration.ofMinutes(minutes));
	}

	/**
	 * 時の加算を実行します
	 * 
	 * @param date  時刻
	 * @param hours 加算する時
	 * @return 時を加算した結果
	 */
	@SuppressWarnings("unchecked")
	public static <T extends Temporal> T addHours(final T date, final int hours) {
		if (date == null) {
			return null;
		}
		return (T) date.plus(Duration.ofHours(hours));
	}

	/**
	 * 日付の加算を実行します
	 * 
	 * @param date 日付型
	 * @param days 加算する日付
	 * @return 日付を加算した結果
	 */
	@SuppressWarnings("unchecked")
	public static <T extends Temporal> T addDays(final T date, final int days) {
		if (date == null) {
			return null;
		}
		if (date instanceof ChronoLocalDate) {
			if (date instanceof LocalDate) {
				final LocalDate localDate = (LocalDate) date;
				return (T) localDate.plus(days, ChronoUnit.DAYS);
			} else if (date instanceof HijrahDate) {
				final HijrahDate localDate = (HijrahDate) date;
				return (T) localDate.plus(days, ChronoUnit.DAYS);
			} else if (date instanceof JapaneseDate) {
				final JapaneseDate localDate = (JapaneseDate) date;
				return (T) localDate.plus(days, ChronoUnit.DAYS);
			} else if (date instanceof MinguoDate) {
				final MinguoDate localDate = (MinguoDate) date;
				return (T) localDate.plus(days, ChronoUnit.DAYS);
			} else if (date instanceof ThaiBuddhistDate) {
				final ThaiBuddhistDate localDate = (ThaiBuddhistDate) date;
				return (T) localDate.plus(days, ChronoUnit.DAYS);
			}
		}
		return (T) date.plus(Duration.ofDays(days));
	}

	/**
	 * 日付の加算を実行します
	 * 
	 * @param date 日付型
	 * @param days 加算する日付
	 * @return 日付を加算した結果
	 */
	public static MonthDay addDays(final MonthDay date, final int days) {
		if (date == null) {
			return null;
		}
		return MonthDay.of(date.getMonthValue(), date.getDayOfMonth() + days);
	}

	/**
	 * 月の加算を実行します
	 * 
	 * @param date   日付型
	 * @param months 加算する月
	 * @return 月を加算した結果のカレンダー
	 */
	@SuppressWarnings("unchecked")
	public static <T extends Temporal> T addMonths(final T date, final int months) {
		if (date == null) {
			return null;
		}
		if (date instanceof ChronoLocalDate) {
			if (date instanceof LocalDate) {
				return (T) ((LocalDate) date).plusMonths(months);
			} else if (date instanceof HijrahDate) {
				final HijrahDate localDate = (HijrahDate) date;
				return (T) localDate.plus(months, ChronoUnit.MONTHS);
			} else if (date instanceof JapaneseDate) {
				final JapaneseDate localDate = (JapaneseDate) date;
				return (T) localDate.plus(months, ChronoUnit.MONTHS);
			} else if (date instanceof MinguoDate) {
				final MinguoDate localDate = (MinguoDate) date;
				return (T) localDate.plus(months, ChronoUnit.MONTHS);
			} else if (date instanceof ThaiBuddhistDate) {
				final ThaiBuddhistDate localDate = (ThaiBuddhistDate) date;
				return (T) localDate.plus(months, ChronoUnit.MONTHS);
			}
		} else if (date instanceof LocalDateTime) {
			return (T) ((LocalDateTime) date).plusMonths(months);
		} else if (date instanceof OffsetDateTime) {
			return (T) ((OffsetDateTime) date).plusMonths(months);
		} else if (date instanceof ZonedDateTime) {
			return (T) ((ZonedDateTime) date).plusMonths(months);
		} else if (date instanceof YearMonth) {
			return (T) ((YearMonth) date).plusMonths(months);
		}
		return (T) date.plus(Duration.of(months, ChronoUnit.MONTHS));
	}

	/**
	 * 月の加算を実行します
	 * 
	 * @param date   日付型
	 * @param months 加算する月
	 * @return 月を加算した結果のカレンダー
	 */
	public static YearMonth addMonths(final YearMonth date, final int months) {
		if (date == null) {
			return null;
		}
		return date.plusMonths(months);
	}

	/**
	 * 月の加算を実行します
	 * 
	 * @param date   日付型
	 * @param months 加算する月
	 * @return 月を加算した結果のカレンダー
	 */
	public static Month addMonths(final Month date, final int months) {
		if (date == null) {
			return null;
		}
		return date.plus(months);
	}

	/**
	 * 月の加算を実行します
	 * 
	 * @param date   日付型
	 * @param months 加算する月
	 * @return 月を加算した結果のMonthDay
	 */
	public static MonthDay addMonths(final MonthDay date, final int months) {
		if (date == null) {
			return null;
		}
		return MonthDay.of(date.getMonthValue() + months, date.getDayOfMonth());
	}

	/**
	 * 年の加算を実行します
	 * 
	 * @param date  日付型
	 * @param years 加算する年
	 * @return 年を加算した結果のカレンダー
	 */
	@SuppressWarnings("unchecked")
	public static <T extends Temporal> T addYears(final T date, final int years) {
		if (date == null) {
			return null;
		}
		if (date instanceof LocalDate) {
			return (T) ((LocalDate) date).plusYears(years);
		} else if (date instanceof LocalDateTime) {
			return (T) ((LocalDateTime) date).plusYears(years);
		} else if (date instanceof OffsetDateTime) {
			return (T) ((OffsetDateTime) date).plusYears(years);
		} else if (date instanceof ZonedDateTime) {
			return (T) ((ZonedDateTime) date).plusYears(years);
		} else if (date instanceof YearMonth) {
			return (T) ((YearMonth) date).plusYears(years);
		}
		return (T) date.plus(Duration.of(years, ChronoUnit.YEARS));
	}

	/**
	 * 年の加算を実行します
	 * 
	 * @param date  日付型
	 * @param years 加算する年
	 * @return 年を加算した結果のカレンダー
	 */
	public static YearMonth addYears(final YearMonth date, final int years) {
		if (date == null) {
			return null;
		}
		return date.plusYears(years);
	}

	/**
	 * 年の加算を実行します
	 * 
	 * @param date  日付型
	 * @param years 加算する年
	 * @return 年を加算した結果のカレンダー
	 */
	public static Year addYears(final Year date, final int years) {
		if (date == null) {
			return null;
		}
		return date.plusYears(years);
	}

	/**
	 * 日付の文字列変換
	 * 
	 * @param date
	 * @param format
	 */
	public static <T extends Temporal> String format(final T date, final String format) {
		final DateTimeFormatter dateFormat = getDateTimeFormatter(format);
		return dateFormat.format(date);
	}

	public static final String DEFAULT_DATETIME_FORMAT = "yyyy-MM-dd HH:mm:ss";
	public static final String DEFAULT_TIME_FORMAT = "HH:mm:ss";
	public static final String DEFAULT_DATE_FORMAT = "yyyy-MM-dd";

	private static Map<String, DateTimeFormatter> formatters = CommonUtils.map();

	/**
	 * 指定したフォーマットのDateTimeFormatterを取得する
	 * 
	 * @param format
	 */
	public static DateTimeFormatter getDateTimeFormatter(final String format) {
		DateTimeFormatter dateFormat = formatters.get(format);
		if (dateFormat == null) {
			dateFormat = DateTimeFormatter.ofPattern(format);
			formatters.put(format, dateFormat);
		}
		return dateFormat;
	}

	/**
	 * 指定したフォーマットのDateTimeFormatterを取得する
	 * 
	 * @param formats
	 */
	public static DateTimeFormatter[] getDateTimeFormatters(final String... formats) {
		if (CommonUtils.isEmpty(formats)) {
			return new DateTimeFormatter[0];
		}
		final List<DateTimeFormatter> results = CommonUtils.list();
		for (final String format : CommonUtils.linkedSet(formats)) {
			final DateTimeFormatter formatter = getDateTimeFormatter(format);
			results.add(formatter);
		}
		return results.toArray(new DateTimeFormatter[0]);
	}

	/**
	 * 指定された日付を含む期初を取得します
	 * 
	 * @param date             対象の日付
	 * @param accountingPeriod 決算月
	 * @return 期初
	 */
	public static ZonedDateTime beginningOfQuarter(final ZonedDateTime date, final int accountingPeriod) {
		ZonedDateTime cal = date.withMonth(accountingPeriod);
		cal = cal.plusMonths(1);
		cal = cal.withDayOfMonth(1);
		cal = truncateTime(cal);
		if (cal.compareTo(date) == 0) {
			return date;
		} else if (cal.compareTo(date) < 0) {
			cal = cal.plusYears(1);
		}
		for (int i = 0; i < 8; i++) {
			if (cal.compareTo(date) <= 0) {
				return cal;
			}
			cal = cal.plusMonths(-3);
		}
		return null;
	}

	/**
	 * 指定された日付を含む年初を取得します
	 * 
	 * @param date 対象の日付
	 * @return 年初
	 */
	@SuppressWarnings("unchecked")
	public static <T extends Temporal> T beginningOfYear(final T date) {
		return (T) date.with(ChronoField.DAY_OF_MONTH, 1).with(ChronoField.MONTH_OF_YEAR, 1);
	}

	/**
	 * 指定された日付を含む年初を取得します
	 * 
	 * @param date 対象の日付
	 * @return 年初
	 */
	public static ZonedDateTime beginningOfYear(final ZonedDateTime date) {
		return date.withDayOfMonth(1).withMonth(1);
	}

	/**
	 * 指定された日付を含む月初を取得します
	 * 
	 * @param date 対象の日付
	 * @return 年初
	 */
	public static ZonedDateTime beginningOfMonth(final ZonedDateTime date) {
		return date.withDayOfMonth(1);
	}

	/**
	 * 指定された日付を含む週の日曜日を取得します
	 * 
	 * @param date 対象の日付
	 * @return 日曜日
	 */
	@SuppressWarnings("unchecked")
	public static <T extends Temporal> T sunday(final T date) {
		return (T) date.with(ChronoField.DAY_OF_WEEK, DayOfWeek.SUNDAY.getValue());
	}

	/**
	 * 指定された日付を含む週の月曜日を取得します
	 * 
	 * @param date 対象の日付
	 * @return 月曜日
	 */
	@SuppressWarnings("unchecked")
	public static <T extends Temporal> T monday(final T date) {
		return (T) date.with(ChronoField.DAY_OF_WEEK, DayOfWeek.MONDAY.getValue());
	}
}
