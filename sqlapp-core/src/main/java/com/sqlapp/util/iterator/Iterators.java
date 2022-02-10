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

package com.sqlapp.util.iterator;

import java.sql.Time;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.ZonedDateTime;
import java.util.Calendar;
import java.util.Date;

import com.sqlapp.data.interval.Interval;

public class Iterators {

	/**
	 * 指定したサイズの<code>java.lang.Integer</code>のイテレータを取得します。
	 * @param size
	 */
	public static Iterable<Integer> range(final int size){
		return new ObjectIterable<Integer>(new IntegerIterator(size));
	}

	/**
	 * 指定したサイズの<code>java.lang.Long</code>のイテレータを取得します。
	 * @param size
	 */
	public static Iterable<Long> range(final long size){
		return new ObjectIterable<Long>(new LongIterator(size));
	}

	/**
	 * 指定した範囲の<code>java.lang.Integer</code>のイテレータを取得します。
	 * @param start
	 * @param end
	 */
	public static Iterable<Integer> range(final int start, final int end){
		return new ObjectIterable<Integer>(new IntegerIterator(start, end));
	}

	/**
	 * 指定した範囲の<code>java.lang.Long</code>のイテレータを取得します。
	 * @param start
	 * @param end
	 */
	public static ObjectIterable<Long> range(final long start, final long end){
		return new ObjectIterable<Long>(new LongIterator(start, end));
	}

	/**
	 * 指定した範囲の<code>java.lang.Integer</code>のイテレータを取得します。
	 * @param start 開始数
	 * @param end 終了数(含まない)
	 * @param step ステップ
	 */
	public static Iterable<Integer> range(final int start, final int end, final int step){
		return new ObjectIterable<Integer>(new IntegerIterator(start, end, step));
	}

	/**
	 * 指定した範囲の<code>java.lang.Long</code>のイテレータを取得します。
	 * @param start 開始数
	 * @param end 終了数(含まない)
	 * @param step ステップ
	 */
	public static Iterable<Long> range(final long start, final long end, final long step){
		return new ObjectIterable<Long>(new LongIterator(start, end, step));
	}

	/**
	 * 指定した範囲の<code>java.util.Calendar</code>の配列を取得します。
	 * @param start 開始日時
	 * @param end 終了日時(含まない)
	 */
	public static Iterable<Calendar> range(final Calendar start, final Calendar end){
		return new ObjectIterable<Calendar>(new CalendarIterator(start, end, 3600*24));
	}

	/**
	 * 指定した範囲の<code>java.util.Date</code>の配列を取得します。
	 * @param start 開始日時
	 * @param end 終了日時(含まない)
	 */
	public static Iterable<Date> range(final Date start, final Date end){
		return new ObjectIterable<Date>(new DateIterator(start, end, 3600*24));
	}

	/**
	 * 指定した範囲の<code>java.sql.Date</code>の配列を取得します。
	 * @param start 開始日
	 * @param end 終了日(含まない)
	 */
	public static Iterable<java.sql.Date> range(final java.sql.Date start, final java.sql.Date end){
		return new ObjectIterable<java.sql.Date>(new SqlDateIterator(start, end, 3600*24));
	}

	/**
	 * 指定した範囲の<code>java.util.Date</code>の配列を取得します。
	 * @param start 開始日付
	 * @param end 終了日付(含まない)
	 * @param step 間隔(秒単位)
	 */
	public static Iterable<Date> range(final Date start, final Date end, final int step){
		return new ObjectIterable<Date>(new DateIterator(start, end, step));
	}

	/**
	 * 指定した範囲の<code>java.sql.Date</code>の配列を取得します。
	 * @param start 開始日付
	 * @param end 終了日付(含まない)
	 * @param step 間隔(日単位)
	 */
	public static Iterable<java.sql.Date> range(final java.sql.Date start, final java.sql.Date end, final int step){
		return new ObjectIterable<java.sql.Date>(new SqlDateIterator(start, end, step));
	}

	/**
	 * 指定した範囲の<code>java.sql.Date</code>の配列を取得します。
	 * @param start 開始日付
	 * @param end 終了日付(含まない)
	 * @param step 間隔(秒単位)
	 */
	public static Iterable<Calendar> range(final Calendar start, final Calendar end, final int step){
		return new ObjectIterable<Calendar>(new CalendarIterator(start, end, step));
	}


	/**
	 * 指定した範囲の<code>java.sql.Date</code>の配列を取得します。
	 * @param start 開始日付
	 * @param end 終了日付(含まない)
	 * @param interval 間隔(秒単位)
	 */
	public static Iterable<Calendar> range(final Calendar start, final Calendar end, final Interval interval){
		return new ObjectIterable<Calendar>(new CalendarIntervalIterator(start, end, interval));
	}
	
	/**
	 * 指定した範囲の<code>java.util.Date</code>の配列を取得します。
	 * @param start 開始日付
	 * @param end 終了日付(含まない)
	 * @param interval インターバル
	 */
	public static Iterable<Date> range(final Date start, final Date end, final Interval interval){
		return new ObjectIterable<Date>(new DateIntervalIterator(start, end, interval));
	}

	/**
	 * 指定した範囲の<code> org.joda.time.DateTime</code>のイテレータを取得します。
	 * @param start 開始日付
	 * @param end 終了日付(含まない)
	 */
	public static Iterable<ZonedDateTime> range(final ZonedDateTime start, final ZonedDateTime end){
		return new ObjectIterable<ZonedDateTime>(new ZonedDateTimeIterator(start, end));
	}

	/**
	 * 指定した範囲の<code>java.time.ZonedDateTime</code>のイテレータを取得します。
	 * @param start 開始日付
	 * @param end 終了日付(含まない)
	 * @param step 間隔(秒単位)
	 */
	public static Iterable<ZonedDateTime> range(final ZonedDateTime start, final ZonedDateTime end, final int step){
		return new ObjectIterable<ZonedDateTime>(new ZonedDateTimeIterator(start, end, step));
	}

	/**
	 * 指定した範囲の<code>java.time.ZonedDateTime</code>のイテレータを取得します。
	 * @param start 開始日付
	 * @param end 終了日付(含まない)
	 * @param interval インターバル
	 */
	public static Iterable<ZonedDateTime> range(final ZonedDateTime start, final ZonedDateTime end, final Interval interval){
		return new ObjectIterable<ZonedDateTime>(new ZonedDateTimeIntervalIterator(start, end, interval));
	}

	/**
	 * 指定した範囲の<code>java.time.OffsetDateTime</code>のイテレータを取得します。
	 * @param start 開始日付
	 * @param end 終了日付(含まない)
	 */
	public static Iterable<OffsetDateTime> range(final OffsetDateTime start, final OffsetDateTime end){
		return new ObjectIterable<OffsetDateTime>(new OffsetDateTimeIterator(start, end));
	}
	
	/**
	 * 指定した範囲の<code>java.time.OffsetDateTime</code>のイテレータを取得します。
	 * @param start 開始日付
	 * @param end 終了日付(含まない)
	 * @param step 間隔(秒単位)
	 */
	public static Iterable<OffsetDateTime> range(final OffsetDateTime start, final OffsetDateTime end, final int step){
		return new ObjectIterable<OffsetDateTime>(new OffsetDateTimeIterator(start, end, step));
	}

	/**
	 * 指定した範囲の<code>java.time.OffsetDateTime</code>のイテレータを取得します。
	 * @param start 開始日付
	 * @param end 終了日付(含まない)
	 * @param interval インターバル
	 */
	public static Iterable<OffsetDateTime> range(final OffsetDateTime start, final OffsetDateTime end, final Interval interval){
		return new ObjectIterable<OffsetDateTime>(new OffsetDateTimeIntervalIterator(start, end, interval));
	}

	/**
	 * 指定した範囲の<code>java.time.OffsetTime</code>のイテレータを取得します。
	 * @param start 開始日付
	 * @param end 終了日付(含まない)
	 */
	public static Iterable<OffsetTime> range(final OffsetTime start, final OffsetTime end){
		return new ObjectIterable<OffsetTime>(new OffsetTimeIterator(start, end));
	}
	
	/**
	 * 指定した範囲の<code>java.time.OffsetTime</code>のイテレータを取得します。
	 * @param start 開始日付
	 * @param end 終了日付(含まない)
	 * @param step 間隔(秒単位)
	 */
	public static Iterable<OffsetTime> range(final OffsetTime start, final OffsetTime end, final int step){
		return new ObjectIterable<OffsetTime>(new OffsetTimeIterator(start, end, step));
	}

	/**
	 * 指定した範囲の<code>java.time.OffsetDateTime</code>のイテレータを取得します。
	 * @param start 開始日付
	 * @param end 終了日付(含まない)
	 * @param interval インターバル
	 */
	public static Iterable<OffsetTime> range(final OffsetTime start, final OffsetTime end, final Interval interval){
		return new ObjectIterable<OffsetTime>(new OffsetTimeIntervalIterator(start, end, interval));
	}
	
	/**
	 * 指定した範囲の<code>java.time.LocalDateTime</code>のイテレータを取得します。
	 * @param start 開始日付
	 * @param end 終了日付(含まない)
	 */
	public static Iterable<LocalDateTime> range(final LocalDateTime start, final LocalDateTime end){
		return new ObjectIterable<LocalDateTime>(new LocalDateTimeIterator(start, end));
	}
	
	/**
	 * 指定した範囲の<code>java.time.LocalDateTime</code>のイテレータを取得します。
	 * @param start 開始日付
	 * @param end 終了日付(含まない)
	 * @param step 間隔(秒単位)
	 */
	public static Iterable<LocalDateTime> range(final LocalDateTime start, final LocalDateTime end, final int step){
		return new ObjectIterable<LocalDateTime>(new LocalDateTimeIterator(start, end, step));
	}

	/**
	 * 指定した範囲の<code>java.time.LocalDateTime</code>のイテレータを取得します。
	 * @param start 開始日付
	 * @param end 終了日付(含まない)
	 * @param interval インターバル
	 */
	public static Iterable<LocalDateTime> range(final LocalDateTime start, final LocalDateTime end, final Interval interval){
		return new ObjectIterable<LocalDateTime>(new LocalDateTimeIntervalIterator(start, end, interval));
	}

	/**
	 * 指定した範囲の<code>java.time.Instant</code>のイテレータを取得します。
	 * @param start 開始日付
	 * @param end 終了日付(含まない)
	 */
	public static Iterable<Instant> range(final Instant start, final Instant end){
		return new ObjectIterable<Instant>(new InstantIterator(start, end));
	}
	
	/**
	 * 指定した範囲の<code>java.time.Instant</code>のイテレータを取得します。
	 * @param start 開始日付
	 * @param end 終了日付(含まない)
	 * @param step 間隔(秒単位)
	 */
	public static Iterable<Instant> range(final Instant start, final Instant end, final int step){
		return new ObjectIterable<Instant>(new InstantIterator(start, end, step));
	}

	/**
	 * 指定した範囲の<code>java.time.Instant</code>のイテレータを取得します。
	 * @param start 開始日付
	 * @param end 終了日付(含まない)
	 * @param interval インターバル
	 */
	public static Iterable<Instant> range(final Instant start, final Instant end, final Interval interval){
		return new ObjectIterable<Instant>(new InstantIntervalIterator(start, end, interval));
	}
	
	/**
	 * 指定した範囲の<code>java.time.LocalDate</code>のイテレータを取得します。
	 * @param start 開始日付
	 * @param end 終了日付(含まない)
	 */
	public static Iterable<LocalDate> range(final LocalDate start, final LocalDate end){
		return new ObjectIterable<LocalDate>(new LocalDateIterator(start, end));
	}
	
	/**
	 * 指定した範囲の<code>java.time.LocalDate</code>のイテレータを取得します。
	 * @param start 開始日付
	 * @param end 終了日付(含まない)
	 * @param step 間隔(秒単位)
	 */
	public static Iterable<LocalDate> range(final LocalDate start, final LocalDate end, final int step){
		return new ObjectIterable<LocalDate>(new LocalDateIterator(start, end, step));
	}

	/**
	 * 指定した範囲の<code>java.time.LocalDate</code>のイテレータを取得します。
	 * @param start 開始日付
	 * @param end 終了日付(含まない)
	 * @param interval インターバル
	 */
	public static Iterable<LocalDate> range(final LocalDate start, final LocalDate end, final Interval interval){
		return new ObjectIterable<LocalDate>(new LocalDateIntervalIterator(start, end, interval));
	}

	/**
	 * 指定した範囲の<code>java.time.LocalTime</code>のイテレータを取得します。
	 * @param start 開始日付
	 * @param end 終了日付(含まない)
	 */
	public static Iterable<LocalTime> range(final LocalTime start, final LocalTime end){
		return new ObjectIterable<LocalTime>(new LocalTimeIterator(start, end));
	}
	
	/**
	 * 指定した範囲の<code>java.time.LocalDateTime</code>のイテレータを取得します。
	 * @param start 開始日付
	 * @param end 終了日付(含まない)
	 * @param step 間隔(秒単位)
	 */
	public static Iterable<LocalTime> range(final LocalTime start, final LocalTime end, final int step){
		return new ObjectIterable<LocalTime>(new LocalTimeIterator(start, end, step));
	}

	/**
	 * 指定した範囲の<code>java.time.LocalDateTime</code>のイテレータを取得します。
	 * @param start 開始日付
	 * @param end 終了日付(含まない)
	 * @param interval インターバル
	 */
	public static Iterable<LocalTime> range(final LocalTime start, final LocalTime end, final Interval interval){
		return new ObjectIterable<LocalTime>(new LocalTimeIntervalIterator(start, end, interval));
	}
	
	/**
	 * 指定した範囲の<code>java.sql.Timestamp</code>のイテレータを取得します。
	 * @param start 開始日付
	 * @param end 終了日付(含まない)
	 */
	public static Iterable<Timestamp> range(final Timestamp start, final Timestamp end){
		return new ObjectIterable<Timestamp>(new TimestampIterator(start, end));
	}

	/**
	 * 指定した範囲の<code>java.sql.Timestamp</code>のイテレータを取得します。
	 * @param start 開始日付
	 * @param end 終了日付(含まない)
	 * @param step 間隔(秒単位)
	 */
	public static Iterable<Timestamp> range(final Timestamp start, final Timestamp end, final int step){
		return new ObjectIterable<Timestamp>(new TimestampIterator(start, end, step));
	}

	/**
	 * 指定した範囲の<code>java.sql.Timestamp</code>のイテレータを取得します。
	 * @param start 開始日付
	 * @param end 終了日付(含まない)
	 * @param interval インターバル
	 */
	public static Iterable<Timestamp> range(final Timestamp start, final Timestamp end, final Interval interval){
		return new ObjectIterable<Timestamp>(new TimestampIntervalIterator(start, end, interval));
	}

	/**
	 * 指定した範囲の<code>java.sql.Time</code>のイテレータを取得します。
	 * @param start 開始時刻
	 * @param end 終了時刻(含まない)
	 */
	public static Iterable<Time> range(final java.sql.Time start, final java.sql.Time end){
		return new ObjectIterable<Time>(new TimeIterator(start, end, 3600));
	}

	/**
	 * 指定した範囲の<code>java.sql.Time</code>のイテレータを取得します。
	 * @param start 開始時刻
	 * @param end 終了時刻(含まない)
	 * @param step 間隔(秒単位)
	 */
	public static Iterable<Time> range(final java.sql.Time start, final java.sql.Time end, final int step){
		return new ObjectIterable<Time>(new TimeIterator(start, end, step));
	}

	/**
	 * 指定した範囲の<code>java.sql.Time</code>のイテレータを取得します。
	 * @param start 開始時刻
	 * @param end 終了時刻(含まない)
	 * @param interval インターバル
	 */
	public static Iterable<Time> range(final java.sql.Time start, final java.sql.Time end, final Interval interval){
		return new ObjectIterable<Time>(new TimeIntervalIterator(start, end, interval));
	}

	/**
	 * 指定した範囲の文字列の配列からイテレータを取得します。
	 * @param args 文字列の配列
	 */
	public static Iterable<String> range(final String... args){
		return new ObjectIterable<String>(new StringArrayIterator(args));
	}


}
