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

package com.sqlapp.util.eval.mvel;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.MonthDay;
import java.time.OffsetDateTime;
import java.time.Year;
import java.time.YearMonth;
import java.time.ZonedDateTime;
import java.time.temporal.Temporal;
import java.util.Calendar;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

import com.sqlapp.data.converter.Converters;
import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.DateUtils;
import com.sqlapp.util.FileUtils;
import com.sqlapp.util.Java8DateUtils;

/**
 * MVEL式に自動登録するメソッド群
 * 
 * @author satoh
 *
 */
public final class MvelUtils {

	private MvelUtils() {
	}

	/**
	 * NULLもしくは空文字の判定
	 * 
	 * @param obj 判定対象のオブジェクト
	 * @return NULLもしくは空文字の場合、true
	 */
	public static boolean isEmpty(final Object obj) {
		return CommonUtils.isEmpty(obj);
	}

	/**
	 * NULLでも空文字でもない場合、<code>true</code>を返します。
	 * 
	 * @param obj 判定対象のオブジェクト
	 * @return NULLでも空文字でもない場合、<code>true</code>
	 */
	public static boolean isNotEmpty(final Object obj) {
		return CommonUtils.isNotEmpty(obj);
	}

	/**
	 * NULLもしくは空文字もしくはスペースかタブのみで構成されていない場合、<code>true</code>を返します
	 * 
	 * @param obj 判定対象のオブジェクト
	 * @return NULLもしくは空文字のみで構成されていない場合、<code>true</code>
	 */
	public static boolean isNotBlank(final Object obj) {
		return CommonUtils.isNotBlank(obj);
	}

	/**
	 * NULLもしくは空文字もしくはスペースかタブのみで構成されているかを判定します
	 * 
	 * @param obj 判定対象のオブジェクト
	 * @return NULLもしくは空文字の場合、true
	 */
	public static boolean isBlank(final Object obj) {
		return CommonUtils.isBlank(obj);
	}

	/**
	 * NULLでない最初の要素を返します
	 * 
	 * @param <T>
	 * @param args
	 */
	public static <T> T coalesce(final Object... args) {
		return CommonUtils.coalesce(args);
	}

	/**
	 * 文字列を反対に並べ替えた結果を取得します。
	 * 
	 * @param value
	 */
	public static String reverse(final String value) {
		return CommonUtils.reverse(value);
	}

	/**
	 * Iterableかを判定します。
	 * 
	 * @param obj 判定対象のオブジェクト
	 * @return Iterableの場合true
	 */
	public static boolean isIterable(final Object obj) {
		if (obj == null) {
			return false;
		}
		if (obj.getClass().isArray()) {
			return obj.getClass().isArray();
		} else if (obj instanceof Iterable) {
			return true;
		} else if (obj instanceof Map) {
			return true;
		} else if (obj instanceof Enumeration) {
			return true;
		}
		return false;
	}

	/**
	 * 秒の加算を実行します
	 * 
	 * @param date    日時
	 * @param seconds 加算する秒
	 * @return 秒を加算した結果
	 */
	public static Object addSeconds(final Object date, final int seconds) {
		if (date == null) {
			return null;
		}
		if (date instanceof Time) {
			return DateUtils.addSeconds((Time) date, seconds);
		} else if (date instanceof Timestamp) {
			return DateUtils.addSeconds((Timestamp) date, seconds);
		} else if (date instanceof Date) {
			return DateUtils.addSeconds((Date) date, seconds);
		} else if (date instanceof Calendar) {
			return DateUtils.addSeconds((Calendar) date, seconds);
		} else if (date instanceof Temporal) {
			return Java8DateUtils.addSeconds((Temporal) date, seconds);
		}
		throw new IllegalArgumentException("invalid type. date=" + date.getClass());
	}

	/**
	 * 分の加算を実行します
	 * 
	 * @param date    時刻
	 * @param minutes 加算する分
	 * @return 分を加算した結果
	 */
	public static Object addMinutes(final Object date, final int minutes) {
		if (date == null) {
			return null;
		}
		if (date instanceof Time) {
			return DateUtils.addMinutes((Time) date, minutes);
		} else if (date instanceof Timestamp) {
			return DateUtils.addMinutes((Timestamp) date, minutes);
		} else if (date instanceof Date) {
			return DateUtils.addMinutes((Date) date, minutes);
		} else if (date instanceof Calendar) {
			return DateUtils.addMinutes((Calendar) date, minutes);
		} else if (date instanceof Temporal) {
			return Java8DateUtils.addMinutes((Temporal) date, minutes);
		}
		throw new IllegalArgumentException("invalid type. date=" + date.getClass());
	}

	/**
	 * 時の加算を実行します
	 * 
	 * @param date  日時
	 * @param hours 加算する時
	 * @return 時を加算した結果
	 */
	public static Object addHours(final Object date, final int hours) {
		if (date == null) {
			return null;
		}
		if (date instanceof Time) {
			return DateUtils.addHours((Time) date, hours);
		} else if (date instanceof Timestamp) {
			return DateUtils.addHours((Timestamp) date, hours);
		} else if (date instanceof Date) {
			return DateUtils.addHours((Date) date, hours);
		} else if (date instanceof Calendar) {
			return DateUtils.addHours((Calendar) date, hours);
		} else if (date instanceof Temporal) {
			return Java8DateUtils.addHours((Temporal) date, hours);
		}
		throw new IllegalArgumentException("invalid type. date=" + date.getClass());
	}

	/**
	 * 日付の加算を実行します
	 * 
	 * @param date 日付型
	 * @param days 加算する日付
	 * @return 日付を加算した結果
	 */
	public static Object addDays(final Object date, final int days) {
		if (date == null) {
			return null;
		}
		if (date instanceof java.sql.Date) {
			return DateUtils.addDays((java.sql.Date) date, days);
		} else if (date instanceof Timestamp) {
			return DateUtils.addDays((Timestamp) date, days);
		} else if (date instanceof Date) {
			return DateUtils.addDays((Date) date, days);
		} else if (date instanceof Calendar) {
			return DateUtils.addDays((Calendar) date, days);
		} else if (date instanceof Temporal) {
			return Java8DateUtils.addDays((Temporal) date, days);
		} else if (date instanceof MonthDay) {
			return Java8DateUtils.addDays((MonthDay) date, days);
		}
		throw new IllegalArgumentException("invalid type. date=" + date.getClass());
	}

	/**
	 * 月の加算を実行します
	 * 
	 * @param date   日付型
	 * @param months 加算する月
	 * @return 月を加算した結果のカレンダー
	 */
	public static Object addMonths(final Object date, final int months) {
		if (date == null) {
			return null;
		}
		if (date instanceof java.sql.Date) {
			return DateUtils.addMonths((java.sql.Date) date, months);
		} else if (date instanceof Timestamp) {
			return DateUtils.addMonths((Timestamp) date, months);
		} else if (date instanceof Date) {
			return DateUtils.addMonths((Date) date, months);
		} else if (date instanceof Calendar) {
			return DateUtils.addMonths((Calendar) date, months);
		} else if (date instanceof Temporal) {
			return Java8DateUtils.addMonths((Temporal) date, months);
		} else if (date instanceof MonthDay) {
			return Java8DateUtils.addMonths((MonthDay) date, months);
		} else if (date instanceof Month) {
			return Java8DateUtils.addMonths((Month) date, months);
		}
		throw new IllegalArgumentException("invalid type. date=" + date.getClass());
	}

	/**
	 * 年の加算を実行します
	 * 
	 * @param date  日付型
	 * @param years 加算する年
	 * @return 年を加算した結果のカレンダー
	 */
	public static Object addYears(final Object date, final int years) {
		if (date == null) {
			return null;
		}
		if (date instanceof java.sql.Date) {
			return DateUtils.addYears((java.sql.Date) date, years);
		} else if (date instanceof Timestamp) {
			return DateUtils.addYears((Timestamp) date, years);
		} else if (date instanceof Date) {
			return DateUtils.addYears((Date) date, years);
		} else if (date instanceof Calendar) {
			return DateUtils.addYears((Calendar) date, years);
		} else if (date instanceof Temporal) {
			return Java8DateUtils.addYears((Temporal) date, years);
		} else if (date instanceof YearMonth) {
			return Java8DateUtils.addMonths((YearMonth) date, years);
		} else if (date instanceof Year) {
			return Java8DateUtils.addMonths((Year) date, years);
		}
		throw new IllegalArgumentException("invalid type. date=" + date.getClass());
	}

	/**
	 * 時刻情報の切り捨て
	 * 
	 * @param date カレンダー
	 * @return 時刻情報を切り捨てた日付
	 */
	public static Object truncateTime(Object date) {
		if (date == null) {
			return null;
		}
		if (date instanceof java.sql.Date) {
			return (java.sql.Date) date;
		} else if (date instanceof Timestamp) {
			return DateUtils.truncateTime((Timestamp) date);
		} else if (date instanceof Date) {
			return DateUtils.truncateTime((Date) date);
		} else if (date instanceof Calendar) {
			return DateUtils.truncateTime((Calendar) date);
		} else if (date instanceof LocalDateTime) {
			return Java8DateUtils.truncateTime((LocalDateTime) date);
		} else if (date instanceof OffsetDateTime) {
			return Java8DateUtils.truncateTime((OffsetDateTime) date);
		} else if (date instanceof ZonedDateTime) {
			return Java8DateUtils.truncateTime((ZonedDateTime) date);
		}
		throw new IllegalArgumentException("invalid type. date=" + date.getClass());
	}

	/**
	 * ミリ秒を切り捨てます
	 * 
	 * @param date 日付
	 * @return ミリ秒情報を切り捨てた日付
	 */
	public static Object truncateMilisecond(Object date) {
		if (date == null) {
			return null;
		}
		if (date instanceof java.sql.Date) {
			return (java.sql.Date) date;
		} else if (date instanceof Timestamp) {
			return DateUtils.truncateMilisecond((Timestamp) date);
		} else if (date instanceof Date) {
			return DateUtils.truncateMilisecond((Date) date);
		} else if (date instanceof Calendar) {
			return DateUtils.truncateMilisecond((Calendar) date);
		} else if (date instanceof LocalDateTime) {
			return Java8DateUtils.truncateMilisecond((LocalDateTime) date);
		} else if (date instanceof OffsetDateTime) {
			return Java8DateUtils.truncateMilisecond((OffsetDateTime) date);
		} else if (date instanceof ZonedDateTime) {
			return Java8DateUtils.truncateMilisecond((ZonedDateTime) date);
		}
		throw new IllegalArgumentException("invalid type. date=" + date.getClass());
	}

	/**
	 * 指定された日付を含む年初を取得します
	 * 
	 * @param date 対象の日付
	 * @return 年初
	 */
	public static Object beginningOfYear(Object date) {
		if (date == null) {
			return null;
		}
		if (date instanceof java.sql.Date) {
			return DateUtils.beginningOfYear((java.sql.Date) date);
		} else if (date instanceof java.sql.Timestamp) {
			return DateUtils.beginningOfYear((java.sql.Timestamp) date);
		} else if (date instanceof java.util.Date) {
			return DateUtils.beginningOfYear((java.util.Date) date);
		} else if (date instanceof Calendar) {
			return DateUtils.beginningOfYear((Calendar) date);
		} else if (date instanceof Temporal) {
			return Java8DateUtils.beginningOfYear((Temporal) date);
		}
		throw new IllegalArgumentException("date is not valid. " + date.getClass());
	}

	/**
	 * 指定された日付を含む期初を取得します
	 * 
	 * @param date             対象の日付
	 * @param accountingPeriod 決算月
	 * @return 期初
	 */
	public static Object beginningOfQuarter(Object date, int accountingPeriod) {
		if (date == null) {
			return null;
		}
		if (date instanceof java.sql.Date) {
			return DateUtils.beginningOfQuarter((java.sql.Date) date, accountingPeriod);
		} else if (date instanceof java.sql.Timestamp) {
			return DateUtils.beginningOfQuarter((java.sql.Timestamp) date, accountingPeriod);
		} else if (date instanceof java.util.Date) {
			return DateUtils.beginningOfQuarter((java.util.Date) date, accountingPeriod);
		} else if (date instanceof Calendar) {
			return DateUtils.beginningOfQuarter((Calendar) date, accountingPeriod);
		} else if (date instanceof Temporal) {
			ZonedDateTime zonedDateTime = Converters.getDefault().convertObject(date, ZonedDateTime.class);
			return Java8DateUtils.beginningOfQuarter(zonedDateTime, accountingPeriod);
		}
		throw new IllegalArgumentException("date is not valid. " + date.getClass());
	}

	/**
	 * 指定された日付を含む週の日曜日を取得します
	 * 
	 * @param date 対象の日付
	 * @return 日曜日
	 */
	public static Object sunday(Object date) {
		if (date == null) {
			return null;
		} else if (date instanceof java.sql.Date) {
			return DateUtils.sunday((java.sql.Date) date);
		} else if (date instanceof java.sql.Timestamp) {
			return DateUtils.sunday((java.sql.Timestamp) date);
		} else if (date instanceof java.util.Date) {
			return DateUtils.sunday((java.util.Date) date);
		} else if (date instanceof Calendar) {
			return DateUtils.sunday((Calendar) date);
		} else if (date instanceof Temporal) {
			return Java8DateUtils.sunday((Temporal) date);
		}
		throw new IllegalArgumentException("date is not valid. " + date.getClass());
	}

	/**
	 * 指定された日付を含む週の月曜日を取得します
	 * 
	 * @param date 対象の日付
	 * @return 月曜日
	 */
	public static Object monday(Object date) {
		if (date == null) {
			return null;
		}
		if (date instanceof java.sql.Date) {
			return DateUtils.monday((java.sql.Date) date);
		}
		if (date instanceof java.sql.Timestamp) {
			return DateUtils.monday((java.sql.Timestamp) date);
		}
		if (date instanceof java.util.Date) {
			return DateUtils.monday((java.util.Date) date);
		}
		if (date instanceof Calendar) {
			return DateUtils.monday((Calendar) date);
		}
		if (date instanceof Temporal) {
			return Java8DateUtils.monday((Temporal) date);
		}
		throw new IllegalArgumentException("date is not valid. " + date.getClass());
	}

	private static String basePath = null;

	private static String encoding = "UTF8";

	public static void setBasePath(String basePath) {
		MvelUtils.basePath = basePath;
	}

	public static void setDefaultEncoding(String encoding) {
		MvelUtils.encoding = encoding;
	}

	/**
	 * ファイルをテキストとして読み込みます。
	 * 
	 * @param filePath
	 */
	public static String readFileAsText(String filePath) {
		return readFileAsText(filePath, encoding);
	}

	/**
	 * ファイルをバイト配列として読み込みます。
	 * 
	 * @param filePath
	 * @throws IOException
	 * @throws FileNotFoundException
	 */
	public static byte[] readFileAsBytes(String filePath) throws FileNotFoundException, IOException {
		File file;
		if (basePath == null) {
			file = new File(filePath);
		} else {
			file = new File(basePath, filePath);
		}
		try (FileInputStream fs = new FileInputStream(file); BufferedInputStream bis = new BufferedInputStream(fs)) {
			byte[] buf = new byte[4096];
			ByteArrayOutputStream bos = new ByteArrayOutputStream();
			int len;
			while ((len = bis.read(buf)) != -1) {
				bos.write(buf, 0, len);
			}
			return bos.toByteArray();
		}
	}

	/**
	 * ファイルをテキストとして読み込みます。
	 * 
	 * @param filePath
	 * @param encoding
	 */
	public static String readFileAsText(String filePath, String encoding) {
		if (basePath == null) {
			return FileUtils.readText(filePath, encoding);
		}
		return FileUtils.readText(FileUtils.combinePath(basePath, filePath), encoding);
	}

	public static String writeZip(String filePath, String zipFilePath, String encoding)
			throws URISyntaxException, IOException {
		if (basePath != null) {
			filePath = FileUtils.combinePath(basePath, filePath);
			zipFilePath = FileUtils.combinePath(basePath, zipFilePath);
		}
		Path fromDir = Paths.get(filePath);
		Path zipPath = Paths.get(zipFilePath);
		Files.deleteIfExists(zipPath);
		final String zipRootDirName = fromDir.getFileName().toString();

		URI zipUri = new URI("jar", zipPath.toUri().toString(), null);
		Map<String, Object> env = new HashMap<>();
		env.put("create", "true");
		if (encoding != null) {
			env.put("encoding", encoding);

		}
		try (FileSystem fs = FileSystems.newFileSystem(zipUri, env, ClassLoader.getSystemClassLoader())) {
			Files.walkFileTree(fromDir, new SimpleFileVisitor<Path>() {
				@Override
				public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
					Path relative = fromDir.relativize(file);
					Path zipFile = fs.getPath(zipRootDirName, relative.toString());
					Path parent = zipFile.getParent();
					if (parent != null) {
						Files.createDirectories(parent);
					}
					Files.copy(file, zipFile, StandardCopyOption.COPY_ATTRIBUTES);
					return FileVisitResult.CONTINUE;
				}
			});
		}
		return zipFilePath;
	}

	public static String writeZip(String filePath, String zipFilePath) throws URISyntaxException, IOException {
		return writeZip(filePath, zipFilePath, null);
	}

	/**
	 * ファイルパスからInputStreamを取得します。
	 * 
	 * @param filePath
	 * @throws FileNotFoundException
	 */
	public static InputStream getInputStream(String filePath) throws FileNotFoundException {
		if (basePath == null) {
			return new FileInputStream(filePath);
		}
		return new FileInputStream(FileUtils.combinePath(basePath, filePath));
	}

	/**
	 * ファイルパスからReaderを取得します。
	 * 
	 * @param filePath
	 * @param encoding
	 * @throws FileNotFoundException
	 * @throws UnsupportedEncodingException
	 */
	public static Reader getReader(String filePath, String encoding)
			throws FileNotFoundException, UnsupportedEncodingException {
		return new InputStreamReader(getInputStream(filePath), encoding);
	}

	/**
	 * ファイルパスからReaderを取得します。
	 * 
	 * @param filePath
	 * @throws FileNotFoundException
	 * @throws UnsupportedEncodingException
	 */
	public static Reader getReader(String filePath) throws FileNotFoundException, UnsupportedEncodingException {
		return getReader(filePath, encoding);
	}

}
