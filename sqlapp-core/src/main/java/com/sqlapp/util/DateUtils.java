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

import static com.sqlapp.util.CommonUtils.isEmpty;
import static com.sqlapp.util.CommonUtils.map;

import java.sql.Timestamp;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Map;

/**
 * 日付関係のユーティリティ
 * 
 * @author SATOH
 *
 */
public final class DateUtils {
	private DateUtils() {
	}

	/**
	 * 現在の日を取得します
	 * 
	 * @return 現在の日
	 */
	public static java.util.Date currentDate() {
		return truncateTime(currentDateTime());
	}

	/**
	 * 現在の日時を取得します
	 * 
	 * @return 現在の日時
	 */
	public static java.util.Date currentDateTime() {
		return new java.util.Date();
	}

	/**
	 * 現在の日時(タイムスタンプ)を取得します
	 * 
	 * @return 現在の日時
	 */
	public static java.sql.Timestamp currentTimestamp() {
		return new java.sql.Timestamp(currentDateTime().getTime());
	}

	/**
	 * 現在の時間を取得します
	 * 
	 * @return 現在の時間
	 */
	public static java.sql.Time currentTime() {
		return toTime(currentDateTime());
	}

	/**
	 * ミリ秒を切り捨てます
	 * 
	 * @param date 日付
	 * @return 時刻情報を切り捨てた日付
	 */
	public static java.util.Date truncateMilisecond(final java.util.Date date) {
		if (date == null) {
			return null;
		}
		return new java.util.Date(date.getTime() / 1000 * 1000);
	}

	/**
	 * ミリ秒を切り捨てます
	 * 
	 * @param cal カレンダー
	 * @return ミリ秒情報を切り捨てた日付
	 */
	public static Calendar truncateMilisecond(Calendar cal) {
		cal.set(Calendar.MILLISECOND, 0);
		return cal;
	}

	/**
	 * 時刻情報を切り捨てます
	 * 
	 * @param date 日付
	 * @return 時刻情報を切り捨てた日付
	 */
	public static java.util.Date truncateTime(final java.util.Date date) {
		if (date == null) {
			return null;
		}
		return truncateTime(toCalendar(date)).getTime();
	}

	/**
	 * 時刻情報の切り捨て
	 * 
	 * @param cal カレンダー
	 * @return 時刻情報を切り捨てた日付
	 */
	public static Calendar truncateTime(Calendar cal) {
		cal.set(Calendar.HOUR_OF_DAY, 0);
		cal.set(Calendar.MINUTE, 0);
		cal.set(Calendar.SECOND, 0);
		cal.set(Calendar.MILLISECOND, 0);
		return cal;
	}

	/**
	 * 時刻情報の切り捨て
	 * 
	 * @param ts タイムスタンプ
	 * @return 時刻情報を切り捨てたTimestamp
	 */
	public static Timestamp truncateTime(final Timestamp ts) {
		if (ts == null) {
			return null;
		}
		Calendar cal = toCalendar(ts);
		return new Timestamp(toDate(truncateTime(cal)).getTime());
	}

	/**
	 * ミリ秒を切り捨てます
	 * 
	 * @param ts タイムスタンプ
	 * @return 時刻情報を切り捨てた日付
	 */
	public static Timestamp truncateMilisecond(final Timestamp ts) {
		if (ts == null) {
			return null;
		}
		return new Timestamp(truncateMilisecond(toCalendar(ts)).getTime().getTime());
	}

	/**
	 * 秒の加算を実行します
	 * 
	 * @param date    時刻
	 * @param seconds 加算する秒
	 * @return 秒を加算した結果
	 */
	public static Calendar addSeconds(final Calendar date, final int seconds) {
		if (date == null) {
			return null;
		}
		Calendar cal = (Calendar) date.clone();
		cal.add(Calendar.SECOND, seconds);
		return cal;
	}

	/**
	 * ミリ秒の加算を実行します
	 * 
	 * @param time  時刻
	 * @param milis 加算するミリ秒
	 * @return ミリ秒を加算した結果
	 */
	public static Calendar addMilliSeconds(final Calendar date, final int addMilliSeconds) {
		if (date == null) {
			return null;
		}
		Calendar cal = (Calendar) date.clone();
		cal.add(Calendar.MILLISECOND, addMilliSeconds);
		return cal;
	}

	/**
	 * 秒の加算を実行します
	 * 
	 * @param time    時刻
	 * @param seconds 加算する秒
	 * @return 秒を加算した結果
	 */
	public static java.sql.Time addSeconds(final java.sql.Time time, final int seconds) {
		if (time == null) {
			return null;
		}
		Calendar cal = toCalendar(time);
		cal.add(Calendar.SECOND, seconds);
		return toTime(cal.getTime());
	}

	/**
	 * ミリ秒の加算を実行します
	 * 
	 * @param time  時刻
	 * @param milis 加算するミリ秒
	 * @return ミリ秒を加算した結果
	 */
	public static java.sql.Time addMilliSeconds(final java.sql.Time time, final int milis) {
		if (time == null) {
			return null;
		}
		Calendar cal = toCalendar(time);
		cal.add(Calendar.MILLISECOND, milis);
		return toTime(cal.getTime());
	}

	/**
	 * 秒の加算を実行します
	 * 
	 * @param date    日付型
	 * @param seconds 加算する秒
	 * @return 秒を加算した結果
	 */
	public static java.util.Date addSeconds(final java.util.Date date, final int seconds) {
		if (date == null) {
			return null;
		}
		Calendar cal = toCalendar(date);
		cal.add(Calendar.SECOND, seconds);
		return cal.getTime();
	}

	/**
	 * ミリ秒の加算を実行します
	 * 
	 * @param time  時刻
	 * @param milis 加算するミリ秒
	 * @return ミリ秒を加算した結果
	 */
	public static java.util.Date addMilliSeconds(final java.util.Date date, final int milis) {
		if (date == null) {
			return null;
		}
		Calendar cal = toCalendar(date);
		cal.add(Calendar.MILLISECOND, milis);
		return cal.getTime();
	}

	/**
	 * 分の加算を実行します
	 * 
	 * @param date    時刻
	 * @param minutes 加算する分
	 * @return 分を加算した結果
	 */
	public static Calendar addMinutes(final Calendar date, final int minutes) {
		if (date == null) {
			return null;
		}
		Calendar cal = (Calendar) date.clone();
		cal.add(Calendar.MINUTE, minutes);
		return cal;
	}

	/**
	 * 分の加算を実行します
	 * 
	 * @param time    時刻
	 * @param minutes 加算する分
	 * @return 分を加算した結果
	 */
	public static java.sql.Time addMinutes(final java.sql.Time time, final int minutes) {
		if (time == null) {
			return null;
		}
		Calendar cal = toCalendar(time);
		cal.add(Calendar.MINUTE, minutes);
		return toTime(cal.getTime());
	}

	/**
	 * 分の加算を実行します
	 * 
	 * @param date    日付型
	 * @param minutes 加算する分
	 * @return 分を加算した結果
	 */
	public static java.util.Date addMinutes(final java.util.Date date, final int minutes) {
		if (date == null) {
			return null;
		}
		Calendar cal = toCalendar(date);
		cal.add(Calendar.MINUTE, minutes);
		return cal.getTime();
	}

	/**
	 * 時の加算を実行します
	 * 
	 * @param date  時刻
	 * @param hours 加算する時
	 * @return 時を加算した結果
	 */
	public static Calendar addHours(final Calendar date, final int hours) {
		if (date == null) {
			return null;
		}
		Calendar cal = (Calendar) date.clone();
		cal.add(Calendar.HOUR_OF_DAY, hours);
		return cal;
	}

	/**
	 * 時の加算を実行します
	 * 
	 * @param time  時刻
	 * @param hours 加算する時
	 * @return 時を加算した結果
	 */
	public static java.sql.Time addHours(final java.sql.Time time, final int hours) {
		if (time == null) {
			return null;
		}
		Calendar cal = toCalendar(time);
		cal.add(Calendar.HOUR_OF_DAY, hours);
		return toTime(cal.getTime());
	}

	/**
	 * 時の加算を実行します
	 * 
	 * @param date  日付型
	 * @param hours 加算する時
	 * @return 時を加算した結果
	 */
	public static java.util.Date addHours(final java.util.Date date, final int hours) {
		if (date == null) {
			return null;
		}
		Calendar cal = toCalendar(date);
		cal.add(Calendar.HOUR_OF_DAY, hours);
		return cal.getTime();
	}

	/**
	 * 日付の加算を実行します
	 * 
	 * @param date 日付型
	 * @param days 加算する日付
	 * @return 日付を加算した結果
	 */
	public static Calendar addDays(final Calendar date, final int days) {
		if (date == null) {
			return null;
		}
		Calendar cal = (Calendar) date.clone();
		cal.add(Calendar.DATE, days);
		return cal;
	}

	/**
	 * 日付の加算を実行します
	 * 
	 * @param date 日付型
	 * @param days 加算する日付
	 * @return 日付を加算した結果
	 */
	public static java.util.Date addDays(final java.util.Date date, final int days) {
		if (date == null) {
			return null;
		}
		Calendar cal = toCalendar(date);
		cal.add(Calendar.DATE, days);
		return cal.getTime();
	}

	/**
	 * 日付の加算を実行します
	 * 
	 * @param date 日付型
	 * @param days 加算する日付
	 * @return 日付を加算した結果
	 */
	public static java.sql.Time addDays(final java.sql.Time date, final int days) {
		if (date == null) {
			return null;
		}
		Calendar cal = toCalendar(date);
		cal.add(Calendar.DATE, days);
		return new java.sql.Time(cal.getTimeInMillis());
	}

	/**
	 * 日付の加算を実行します
	 * 
	 * @param date 日付型
	 * @param days 加算する日付
	 * @return 日付を加算した結果
	 */
	public static java.sql.Date addDays(final java.sql.Date date, final int days) {
		if (date == null) {
			return null;
		}
		Calendar cal = toCalendar(date);
		cal.add(Calendar.DATE, days);
		return toSqlDate(cal.getTime());
	}

	/**
	 * 月の加算を実行します
	 * 
	 * @param date   日付型
	 * @param months 加算する月
	 * @return 月を加算した結果のカレンダー
	 */
	public static java.util.Date addMonths(final java.util.Date date, final int months) {
		if (date == null) {
			return null;
		}
		Calendar cal = toCalendar(date);
		cal.add(Calendar.MONTH, months);
		return cal.getTime();
	}

	/**
	 * 月の加算を実行します
	 * 
	 * @param date   日付型
	 * @param months 加算する月
	 * @return 月を加算した結果のカレンダー
	 */
	public static Calendar addMonths(final Calendar date, final int months) {
		if (date == null) {
			return null;
		}
		Calendar cal = (Calendar) date.clone();
		cal.add(Calendar.MONTH, months);
		return cal;
	}

	/**
	 * 月の加算を実行します
	 * 
	 * @param date   日付型
	 * @param months 加算する月
	 * @return 月を加算した結果のカレンダー
	 */
	public static java.sql.Date addMonths(final java.sql.Date date, final int months) {
		if (date == null) {
			return null;
		}
		Calendar cal = toCalendar(date);
		cal.add(Calendar.MONTH, months);
		return toSqlDate(cal.getTime());
	}

	/**
	 * 年の加算を実行します
	 * 
	 * @param date 日付型
	 * @param days 加算する年
	 * @return 年を加算した結果のカレンダー
	 */
	public static Calendar addYears(final Calendar date, final int days) {
		if (date == null) {
			return null;
		}
		Calendar cal = (Calendar) date.clone();
		cal.add(Calendar.YEAR, days);
		return cal;
	}

	/**
	 * 年の加算を実行します
	 * 
	 * @param date  日付型
	 * @param years 加算する年
	 * @return 年を加算した結果のカレンダー
	 */
	public static java.sql.Date addYears(final java.sql.Date date, final int years) {
		if (date == null) {
			return null;
		}
		Calendar cal = toCalendar(date);
		cal.add(Calendar.YEAR, years);
		return toSqlDate(cal.getTime());
	}

	/**
	 * 年の加算を実行します
	 * 
	 * @param date  日付型
	 * @param years 加算する年
	 * @return 年を加算した結果のカレンダー
	 */
	public static java.util.Date addYears(final java.util.Date date, final int years) {
		if (date == null) {
			return null;
		}
		Calendar cal = toCalendar(date);
		cal.add(Calendar.YEAR, years);
		return cal.getTime();
	}

	/**
	 * 秒の加算を実行します
	 * 
	 * @param ts      Timestamp
	 * @param seconds 加算する秒
	 * @return 秒を加算した結果
	 */
	public static Timestamp addSeconds(final Timestamp ts, final int seconds) {
		if (ts == null) {
			return null;
		}
		int nanos = ts.getNanos();
		Calendar cal = toCalendar(ts);
		cal.add(Calendar.SECOND, seconds);
		Timestamp result = new Timestamp(cal.getTimeInMillis());
		result.setNanos(nanos);
		return result;
	}

	/**
	 * 分の加算を実行します
	 * 
	 * @param ts      Timestamp
	 * @param minutes 加算する分
	 * @return 分を加算した結果
	 */
	public static Timestamp addMinutes(final Timestamp ts, final int minutes) {
		if (ts == null) {
			return null;
		}
		int nanos = ts.getNanos();
		Calendar cal = toCalendar(ts);
		cal.add(Calendar.MINUTE, minutes);
		Timestamp result = new Timestamp(cal.getTimeInMillis());
		result.setNanos(nanos);
		return result;
	}

	/**
	 * 時の加算を実行します
	 * 
	 * @param ts    Timestamp
	 * @param hours 加算する時
	 * @return 時を加算した結果
	 */
	public static Timestamp addHours(final Timestamp ts, final int hours) {
		if (ts == null) {
			return null;
		}
		int nanos = ts.getNanos();
		Calendar cal = toCalendar(ts);
		cal.add(Calendar.HOUR_OF_DAY, hours);
		Timestamp result = new Timestamp(cal.getTimeInMillis());
		result.setNanos(nanos);
		return result;
	}

	/**
	 * 日付の加算を実行します
	 * 
	 * @param ts   Timestamp
	 * @param days 加算する日付
	 * @return 日付を加算した結果
	 */
	public static Timestamp addDays(final Timestamp ts, final int days) {
		if (ts == null) {
			return null;
		}
		int nanos = ts.getNanos();
		Calendar cal = toCalendar(ts);
		cal.add(Calendar.DATE, days);
		Timestamp result = new Timestamp(cal.getTimeInMillis());
		result.setNanos(nanos);
		return result;
	}

	/**
	 * 月の加算を実行します
	 * 
	 * @param ts     Timestamp
	 * @param months 加算する月
	 * @return 月を加算した結果のカレンダー
	 */
	public static Timestamp addMonths(final Timestamp ts, final int months) {
		if (ts == null) {
			return null;
		}
		int nanos = ts.getNanos();
		Calendar cal = toCalendar(ts);
		cal.add(Calendar.MONTH, months);
		Timestamp result = new Timestamp(cal.getTimeInMillis());
		result.setNanos(nanos);
		return result;
	}

	/**
	 * 年の加算を実行します
	 * 
	 * @param ts    Timestamp
	 * @param years 加算する年
	 * @return 年を加算した結果のカレンダー
	 */
	public static java.util.Date addYears(final Timestamp ts, final int years) {
		if (ts == null) {
			return null;
		}
		int nanos = ts.getNanos();
		Calendar cal = toCalendar(ts);
		cal.add(Calendar.YEAR, years);
		Timestamp result = new Timestamp(cal.getTimeInMillis());
		result.setNanos(nanos);
		return result;
	}

	/**
	 * 日付を設定します
	 * 
	 * @param date 日付型
	 * @param dt   設定する日付
	 * @return 日付を設定した結果
	 */
	public static java.util.Date setDate(final java.util.Date date, final int dt) {
		if (date == null) {
			return null;
		}
		Calendar cal = toCalendar(date);
		cal.set(Calendar.DATE, dt);
		return cal.getTime();
	}

	/**
	 * 日付を設定します
	 * 
	 * @param date 日付型
	 * @param dt   設定する日付
	 * @return 日付を設定した結果
	 */
	public static java.sql.Date setDate(final java.sql.Date date, final int dt) {
		if (date == null) {
			return null;
		}
		Calendar cal = toCalendar(date);
		cal.set(Calendar.DATE, dt);
		return toSqlDate(cal.getTime());
	}

	/**
	 * 日付を設定します
	 * 
	 * @param ts 日付型
	 * @param dt 設定する日付
	 * @return 日付を設定した結果
	 */
	public static java.sql.Timestamp setDate(final java.sql.Timestamp ts, final int dt) {
		if (ts == null) {
			return null;
		}
		int nanos = ts.getNanos();
		Calendar cal = toCalendar(ts);
		cal.set(Calendar.DATE, dt);
		Timestamp result = new Timestamp(cal.getTime().getTime());
		result.setNanos(nanos);
		return result;
	}

	/**
	 * 月を設定します
	 * 
	 * @param date  日付型
	 * @param month 設定する月
	 * @return 月を設定した結果
	 */
	public static java.util.Date setMonth(final java.util.Date date, final int month) {
		if (date == null) {
			return null;
		}
		Calendar cal = toCalendar(date);
		cal.set(Calendar.MONTH, month);
		return cal.getTime();
	}

	/**
	 * 月を設定します
	 * 
	 * @param date  日付型
	 * @param month 設定する月
	 * @return 月を設定した結果
	 */
	public static java.sql.Date setMonth(final java.sql.Date date, final int month) {
		if (date == null) {
			return null;
		}
		Calendar cal = toCalendar(date);
		cal.set(Calendar.MONTH, month);
		return toSqlDate(cal.getTime());
	}

	/**
	 * 月を設定します
	 * 
	 * @param ts    日付型
	 * @param month 設定する月
	 * @return 月を設定した結果
	 */
	public static java.sql.Timestamp setMonth(final java.sql.Timestamp ts, final int month) {
		if (ts == null) {
			return null;
		}
		int nanos = ts.getNanos();
		Calendar cal = toCalendar(ts);
		cal.set(Calendar.MONTH, month);
		Timestamp result = new Timestamp(cal.getTime().getTime());
		result.setNanos(nanos);
		return result;
	}

	/**
	 * 年を設定します
	 * 
	 * @param date 日付型
	 * @param year 設定する年
	 * @return 年を設定した結果
	 */
	public static java.util.Date setYear(final java.util.Date date, final int year) {
		if (date == null) {
			return null;
		}
		Calendar cal = toCalendar(date);
		cal.set(Calendar.YEAR, year);
		return cal.getTime();
	}

	/**
	 * 年を設定します
	 * 
	 * @param date 日付型
	 * @param year 設定する年
	 * @return 年を設定した結果
	 */
	public static java.sql.Date setYear(final java.sql.Date date, final int year) {
		if (date == null) {
			return null;
		}
		Calendar cal = toCalendar(date);
		cal.set(Calendar.YEAR, year);
		return toSqlDate(cal.getTime());
	}

	/**
	 * 年を設定します
	 * 
	 * @param ts   日付型
	 * @param year 設定する年
	 * @return 年を設定した結果
	 */
	public static java.sql.Timestamp setYear(final java.sql.Timestamp ts, final int year) {
		if (ts == null) {
			return null;
		}
		int nanos = ts.getNanos();
		Calendar cal = toCalendar(ts);
		cal.set(Calendar.YEAR, year);
		Timestamp result = new Timestamp(cal.getTime().getTime());
		result.setNanos(nanos);
		return result;
	}

	/**
	 * 日付型からカレンダー型に変換
	 * 
	 * @param date 日付型
	 * @return カレンダー型
	 */
	public static Calendar toCalendar(final java.util.Date date) {
		Calendar cal = Calendar.getInstance();
		cal.setTime(date);
		return cal;
	}

	/**
	 * Long型からカレンダー型に変換
	 * 
	 * @param timeInMills 1970-1-1からのミリ秒
	 * @return カレンダー型
	 */
	public static Calendar toCalendar(final long timeInMills) {
		Calendar cal = Calendar.getInstance();
		cal.setTimeInMillis(timeInMills);
		return cal;
	}

	/**
	 * カレンダーから時刻型に変換します
	 * 
	 * @param cal カレンダー型
	 * @return 時刻型
	 */
	public static java.sql.Time toTime(final Calendar cal) {
		cal.set(Calendar.YEAR, 1970);
		cal.set(Calendar.MONTH, Calendar.JANUARY);
		cal.set(Calendar.DATE, 1);
		java.sql.Time t = new java.sql.Time(cal.getTimeInMillis());
		return t;
	}

	/**
	 * 日付型から時刻型に変換
	 * 
	 * @param date 日付型
	 * @return 時刻型
	 */
	public static java.sql.Time toTime(final java.util.Date date) {
		if (date == null) {
			return null;
		}
		return toTime(toCalendar(date));
	}

	/**
	 * long型から時刻型に変換
	 * 
	 * @param timeInMillis 1970-1-1からのミリ秒
	 * @return 時刻型
	 */
	public static java.sql.Time toTime(long timeInMillis) {
		java.sql.Time t = new java.sql.Time(timeInMillis);
		return t;
	}

	/**
	 * カレンダーからSQL日付型に変換
	 * 
	 * @param cal カレンダー型
	 * @return SQL日付型
	 */
	public static java.sql.Date toSqlDate(Calendar cal) {
		if (cal == null) {
			return null;
		}
		return toSqlDate(cal.getTime());
	}

	/**
	 * 日付型からSQL日付型に変換
	 * 
	 * @param date 日付型
	 * @return SQL日付型
	 */
	public static java.sql.Date toSqlDate(java.util.Date date) {
		if (date == null) {
			return null;
		}
		return new java.sql.Date(truncateTime(date).getTime());
	}

	/**
	 * java.sql.Dateへ変換します
	 * 
	 * @param value  日付の文字列
	 * @param format 日付のフォーマット
	 * @return java.sql.Date
	 * @throws ParseException
	 */
	public static java.sql.Date toSqlDate(String value, String format) throws ParseException {
		if (isEmpty(value)) {
			return null;
		}
		return toSqlDate(parse(value, format));
	}

	/**
	 * Timestamp型から日付型に変換
	 * 
	 * @param ts Timestamp型
	 * @return 日付型
	 */
	public static java.util.Date toDate(Timestamp ts) {
		if (ts == null) {
			return null;
		}
		return new java.util.Date(ts.getTime());
	}

	/**
	 * カレンダー型から日付型に変換
	 * 
	 * @param cal カレンダー型
	 * @return 日付型
	 */
	public static java.util.Date toDate(Calendar cal) {
		if (cal == null) {
			return null;
		}
		return cal.getTime();
	}

	/**
	 * long型から日付型に変換
	 * 
	 * @param timeInMills 1970-1-1からのミリ秒
	 * @return 日付型
	 */
	public static java.util.Date toDate(long timeInMills) {
		Calendar cal = Calendar.getInstance();
		cal.setTimeInMillis(timeInMills);
		return cal.getTime();
	}

	/**
	 * java.util.Dateへ変換します
	 * 
	 * @param value  日付の文字列
	 * @param format 日付のフォーマット
	 * @return java.util.Date
	 * @throws ParseException
	 */
	public static java.util.Date toDate(String value, String format) throws ParseException {
		if (isEmpty(value)) {
			return null;
		}
		return parse(value, format);
	}

	/**
	 * <code>java.sql.Time</code>へ変換します
	 * 
	 * @param value 日付の文字列
	 * @return <code>java.sql.Time</code>
	 * @throws ParseException
	 */
	public static java.sql.Time toTime(String value) throws ParseException {
		if (isEmpty(value)) {
			return null;
		}
		return toTime(parse(value, DEFAULT_TIME_FORMAT));
	}

	/**
	 * <code>java.sql.Time</code>へ変換します
	 * 
	 * @param value  日付の文字列
	 * @param format 日付のフォーマット
	 * @return <code>java.sql.Time</code>
	 * @throws ParseException
	 */
	public static java.sql.Time toTime(String value, String format) throws ParseException {
		if (isEmpty(value)) {
			return null;
		}
		if (isEmpty(format)) {
			return toTime(parse(value));
		}
		return toTime(parse(value, format));
	}

	/**
	 * long型から日付型に変換
	 * 
	 * @param timeInMills 1970-1-1からのミリ秒
	 * @return 日付型
	 */
	public static java.sql.Date toSqlDate(long timeInMills) {
		Calendar cal = Calendar.getInstance();
		cal.setTimeInMillis(timeInMills);
		return toSqlDate(cal);
	}

	/**
	 * Timestampへ変換します
	 * 
	 * @param date 日付
	 * @return Timestamp
	 */
	public static Timestamp toTimestamp(final java.util.Date date) {
		if (date == null) {
			return null;
		}
		return new Timestamp(date.getTime());
	}

	/**
	 * Timestampへの変換
	 * 
	 * @param date 日付
	 * @return Timestamp
	 */
	public static Timestamp toTimestamp(final Calendar date) {
		if (date == null) {
			return null;
		}
		return new Timestamp(date.getTimeInMillis());
	}

	/**
	 * Timestampへの変換
	 * 
	 * @param timeInMills 日付
	 * @return Timestamp
	 */
	public static Timestamp toTimestamp(long timeInMills) {
		return new Timestamp(timeInMills);
	}

	/**
	 * Timestampへの変換
	 * 
	 * @param value  日付の文字列
	 * @param format 日付のフォーマット
	 * @return Timestamp
	 * @throws ParseException
	 */
	public static Timestamp toTimestamp(String value, String format) throws ParseException {
		if (isEmpty(value)) {
			return null;
		}
		return new Timestamp(parse(value, format).getTime());
	}

	/**
	 * 指定された日付を含む週の日曜日を取得します
	 * 
	 * @param date 対象の日付
	 * @return 日曜日
	 */
	public static Date sunday(Date date) {
		return addDays(monday(date), -1);
	}

	/**
	 * 指定された日付を含む週の日曜日を取得します
	 * 
	 * @param date 対象の日付
	 * @return 日曜日
	 */
	public static Timestamp sunday(Timestamp date) {
		return addDays(monday(date), -1);
	}

	/**
	 * 指定された日付を含む週の日曜日を取得します
	 * 
	 * @param date 対象の日付
	 * @return 日曜日
	 */
	public static Calendar sunday(Calendar date) {
		return addDays(monday(date), -1);
	}

	/**
	 * 指定された日付を含む週の月曜日を取得します
	 * 
	 * @param date 対象の日付
	 * @return 月曜日
	 */
	public static Date monday(Date date) {
		return setWeek(date, Calendar.MONDAY);
	}

	/**
	 * 指定された日付を含む週の月曜日を取得します
	 * 
	 * @param date 対象の日付
	 * @return 月曜日
	 */
	public static Timestamp monday(Timestamp date) {
		return setWeek(date, Calendar.MONDAY);
	}

	/**
	 * 指定された日付を含む月初を取得します
	 * 
	 * @param date 対象の日付
	 * @return 月初
	 */
	public static Date beginningOfMonth(Date date) {
		return setDate(date, 1);
	}

	/**
	 * 指定された日付を含む月初を取得します
	 * 
	 * @param date 対象の日付
	 * @return 月初
	 */
	public static Timestamp beginningOfMonth(Timestamp date) {
		return setDate(date, 1);
	}

	/**
	 * 指定された日付を含む月初を取得します
	 * 
	 * @param date 対象の日付
	 * @return 月初
	 */
	public static Calendar beginningOfMonth(Calendar date) {
		date.set(Calendar.DATE, 1);
		return date;
	}

	/**
	 * 指定された日付を含む年初を取得します
	 * 
	 * @param date 対象の日付
	 * @return 年初
	 */
	public static Date beginningOfYear(Date date) {
		return setMonth(setDate(date, 1), 0);
	}

	/**
	 * 指定された日付を含む年初を取得します
	 * 
	 * @param date 対象の日付
	 * @return 年初
	 */
	public static Timestamp beginningOfYear(Timestamp date) {
		return setMonth(setDate(date, 1), 0);
	}

	/**
	 * 指定された日付を含む年初を取得します
	 * 
	 * @param date 対象の日付
	 * @return 年初
	 */
	public static Calendar beginningOfYear(Calendar date) {
		date.set(Calendar.DATE, 1);
		date.set(Calendar.MONTH, 0);
		return date;
	}

	/**
	 * 指定された日付を含む期初を取得します
	 * 
	 * @param date             対象の日付
	 * @param accountingPeriod 決算月
	 * @return 期初
	 */
	public static Date beginningOfQuarter(Date date, int accountingPeriod) {
		Calendar cal = toCalendar(date);
		cal.set(Calendar.MONTH, accountingPeriod);
		cal.set(Calendar.DATE, 1);
		cal = truncateTime(cal);
		Date acStartDate = toDate(cal);
		if (acStartDate.compareTo(date) == 0) {
			return date;
		} else if (acStartDate.compareTo(date) < 0) {
			cal.add(Calendar.YEAR, 1);
			acStartDate = toDate(cal);
		}
		for (int i = 0; i < 8; i++) {
			acStartDate = toDate(cal);
			if (cal.getTime().compareTo(date) <= 0) {
				return cal.getTime();
			}
			cal.add(Calendar.MONTH, -3);
		}
		return null;
	}

	/**
	 * 指定された日付を含む期初を取得します
	 * 
	 * @param date             対象の日付
	 * @param accountingPeriod 決算月
	 * @return 期初
	 */
	public static Timestamp beginningOfQuarter(Timestamp date, int accountingPeriod) {
		return toTimestamp(beginningOfQuarter((java.util.Date) date, accountingPeriod));
	}

	/**
	 * 指定された日付を含む期初を取得します
	 * 
	 * @param date             対象の日付
	 * @param accountingPeriod 決算月
	 * @return 期初
	 */
	public static Calendar beginningOfQuarter(Calendar date, int accountingPeriod) {
		Calendar cal = (Calendar) date.clone();
		cal.set(Calendar.MONTH, accountingPeriod);
		cal.set(Calendar.DATE, 1);
		cal = truncateTime(cal);
		if (cal.getTime().compareTo(date.getTime()) == 0) {
			return date;
		} else if (cal.getTime().compareTo(date.getTime()) < 0) {
			cal.add(Calendar.YEAR, 1);
		}
		for (int i = 0; i < 8; i++) {
			if (cal.getTime().compareTo(date.getTime()) <= 0) {
				return cal;
			}
			cal.add(Calendar.MONTH, -3);
		}
		return null;
	}

	/**
	 * 週を設定します
	 * 
	 * @param ts   日付型
	 * @param week 曜日
	 * @return 曜日を設定した結果
	 */
	public static java.sql.Timestamp setWeek(final java.sql.Timestamp ts, final int week) {
		if (ts == null) {
			return null;
		}
		int nanos = ts.getNanos();
		Calendar cal = toCalendar(ts);
		cal.set(Calendar.DAY_OF_WEEK, week);
		Timestamp result = new Timestamp(cal.getTime().getTime());
		result.setNanos(nanos);
		return result;
	}

	/**
	 * 週を設定します
	 * 
	 * @param date 日付型
	 * @param week 曜日
	 * @return 曜日を設定した結果
	 */
	public static Date setWeek(final Date date, final int week) {
		if (date == null) {
			return null;
		}
		Calendar cal = toCalendar(date);
		cal.set(Calendar.DAY_OF_WEEK, week);
		return cal.getTime();
	}

	/**
	 * 週を設定します
	 * 
	 * @param date 日付型
	 * @param week 曜日
	 * @return 曜日を設定した結果
	 */
	public static java.sql.Date setWeek(final java.sql.Date date, final int week) {
		if (date == null) {
			return null;
		}
		Calendar cal = toCalendar(date);
		cal.set(Calendar.DAY_OF_WEEK, week);
		return toSqlDate(cal.getTime());
	}

	/**
	 * 指定された日付を含む週の月曜日を取得します
	 * 
	 * @param date 対象の日付
	 * @return 月曜日
	 */
	public static Calendar monday(Calendar date) {
		date.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY);
		return date;
	}

	/**
	 * 日付の文字列変換
	 * 
	 * @param date
	 * @param format
	 */
	public static String format(java.util.Date date, String format) {
		DateFormat dateFormat = getDateFormat(format);
		return dateFormat.format(date);
	}

	/**
	 * 日付の文字列変換
	 * 
	 * @param date
	 * @param format
	 */
	public static String format(Calendar date, String format) {
		DateFormat dateFormat = getDateFormat(format);
		return dateFormat.format(date);
	}

	/**
	 * 文字列を指定したフォーマットで日付に変換
	 * 
	 * @param dateString
	 * @param format
	 * @throws ParseException
	 */
	public static java.util.Date parse(String dateString, String format) throws ParseException {
		DateFormat dateFormat = getDateFormat(format);
		return dateFormat.parse(dateString);
	}

	/**
	 * 文字列をyyyy-MM-dd HH:mm:ssフォーマットで日付に変換
	 * 
	 * @param dateString
	 * @throws ParseException
	 */
	public static java.util.Date parse(String dateString) throws ParseException {
		if (isEmpty(dateString)) {
			return null;
		}
		if (dateString.length() < 15) {
			return parse(dateString, DEFAULT_DATE_FORMAT);
		}
		return parse(dateString, DEFAULT_DATETIME_FORMAT);
	}

	/**
	 * DateFormatのスレッドローカルでのキャッシュ
	 */
	private static final ThreadLocal<Map<String, DateFormat>> threadLocalDateFormat = new ThreadLocal<Map<String, DateFormat>>() {
		protected Map<String, DateFormat> initialValue() {
			return map();
		}
	};

	public static final String DEFAULT_DATETIME_FORMAT = "yyyy-MM-dd HH:mm:ss";
	public static final String DEFAULT_TIME_FORMAT = "HH:mm:ss";
	public static final String DEFAULT_DATE_FORMAT = "yyyy-MM-dd";

	/**
	 * スレッドローカルから指定したフォーマットのDateFormatを取得する
	 * 
	 * @param format
	 */
	private static DateFormat getDateFormat(String format) {
		Map<String, DateFormat> map = threadLocalDateFormat.get();
		DateFormat dateFormat = map.get(format);
		if (dateFormat == null) {
			dateFormat = new SimpleDateFormat(format);
			map.put(format, dateFormat);
		}
		return dateFormat;
	}

	public static String format(java.util.Date date) {
		return format(date, DEFAULT_DATETIME_FORMAT);
	}

	public static String format(Calendar date) {
		return format(date, DEFAULT_DATETIME_FORMAT);
	}

	public static String format(java.sql.Time date) {
		return format(date, "HH:mm:ss");
	}

	/**
	 * スレッドローカルをクリアします
	 */
	public static final void clear() {
		threadLocalDateFormat.remove();
	}
}
