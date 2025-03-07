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

import static com.sqlapp.util.CommonUtils.cast;
import static com.sqlapp.util.CommonUtils.eq;
import static com.sqlapp.util.CommonUtils.isEmpty;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Row;
import com.sqlapp.data.schemas.Table;

/**
 * ファイル関連のユーティリティメソッド
 * 
 * @author SATOH
 * 
 */
public final class FileUtils {

	private static Logger log = LogManager.getLogger(FileUtils.class);

	/**
	 * 条件(ファイル名の正規表現)で指定したファイルパス一覧の取得
	 * 
	 * @param rootPath        操作対象のルートパス
	 * @param fileFilter      条件
	 * @param caseInsensitive 大文字小文字の区別
	 * @param recursive       再帰操作
	 * @return ファイルパス一覧
	 */
	public static List<String> getFileList(final String rootPath, final String fileFilter,
			final boolean caseInsensitive, final boolean recursive) {
		final List<String> fileList = new ArrayList<String>();
		File dir = null;
		if (rootPath == null) {
			dir = new File("");
		} else {
			dir = new File(rootPath);
		}
		Pattern pattern = null;
		if (fileFilter != null) {
			if (caseInsensitive) {
				pattern = Pattern.compile(fileFilter, Pattern.CASE_INSENSITIVE);
			} else {
				pattern = Pattern.compile(fileFilter);
			}
		}
		searchFileList(dir, pattern, recursive, fileList);
		return fileList;
	}

	private static void searchFileList(final File targetFile, final Pattern pattern, final boolean recursive,
			final List<String> fileList) {
		if (targetFile.isFile()) {
			if (isTargetFile(targetFile, pattern)) {
				fileList.add(targetFile.getAbsolutePath());
			}
			return;
		}
		if (!recursive) {
			return;
		}
		final File[] files = targetFile.listFiles();
		if (files != null) {
			for (final File file : files) {
				if (file.isFile()) {
					if (isTargetFile(file, pattern)) {
						fileList.add(file.getAbsolutePath());
					}
				} else {
					searchFileList(file, pattern, recursive, fileList);
				}
			}
		}
	}

	private static boolean isTargetFile(final File file, final Pattern pattern) {
		if (pattern == null) {
			return true;
		}
		final Matcher matcher = pattern.matcher(file.getName());
		return matcher.matches();
	}

	/**
	 * 条件(ファイル名の正規表現)で指定したファイル一覧の取得(DataTable形式)
	 * 
	 * @param rootPath        操作対象のルートパス
	 * @param fileFilter      条件
	 * @param caseInsensitive 大文字小文字の区別
	 * @param recursive       再帰操作
	 * @return ファイルパス一覧
	 */
	public static Table getFileTable(final String rootPath, final String fileFilter, final boolean caseInsensitive,
			final boolean recursive) {
		final Table table = getEmptyFileDataTable();
		File dir = null;
		if (rootPath == null) {
			dir = new File("");
		} else {
			dir = new File(rootPath);
		}
		Pattern pattern = null;
		if (fileFilter != null) {
			if (caseInsensitive) {
				pattern = Pattern.compile(fileFilter, Pattern.CASE_INSENSITIVE);
			} else {
				pattern = Pattern.compile(fileFilter);
			}
		}
		searchFileTable(dir, pattern, recursive, table);
		return table;
	}

	private static void searchFileTable(final File targetFile, final Pattern pattern, final boolean recursive,
			final Table table) {
		if (targetFile.isFile()) {
			if (isTargetFile(targetFile, pattern)) {
				final Row row = getFileRow(table, targetFile);
				table.getRows().add(row);
			}
			return;
		}
		if (!recursive) {
			return;
		}
		final File[] files = targetFile.listFiles();
		if (files != null) {
			for (final File file : files) {
				if (file.isFile()) {
					if (isTargetFile(file, pattern)) {
						final Row row = getFileRow(table, file);
						table.getRows().add(row);
					}
				} else {
					searchFileTable(file, pattern, recursive, table);
				}
			}
		}
	}

	/**
	 * Fileに対応したRowの取得
	 * 
	 * @param table
	 * @param file
	 */
	public static Row getFileRow(final Table table, final File file) {
		final Row row = table.newRow();
		row.put("name", file.getName());
		row.put("absolutePath", file.getAbsolutePath());
		if (file.isFile()) {
			row.put("length", file.length());
			row.put("extension", getExtension(file.getAbsolutePath()));
		}
		row.put("parent", file.getParent());
		final Calendar cal = Calendar.getInstance();
		cal.setTimeInMillis(file.lastModified());
		row.put("lastModified", cal.getTime());
		row.put("canExecute", file.canExecute());
		row.put("canRead", file.canRead());
		row.put("canWrite", file.canWrite());
		return row;
	}

	/**
	 * ファイルデータ格納用の空のデータテーブルの取得
	 * 
	 */
	public static Table getEmptyFileDataTable() {
		final Table table = new Table();
		Column column = new Column("name");
		column.setDataType(DataType.VARCHAR).setLength(255);
		table.getColumns().add(column);
		column = new Column("extension");
		column.setDataType(DataType.VARCHAR).setLength(255);
		table.getColumns().add(column);
		column = new Column("absolutePath");
		column.setDataType(DataType.VARCHAR).setLength(1023);
		table.getColumns().add(column);
		column = new Column("parent");
		column.setDataType(DataType.VARCHAR).setLength(1023);
		table.getColumns().add(column);
		column = new Column("length");
		column.setDataType(DataType.BIGINT);
		table.getColumns().add(column);
		column = new Column("lastModified");
		column.setDataType(DataType.DATETIME);
		table.getColumns().add(column);
		column = new Column("canExecute");
		column.setDataType(DataType.BOOLEAN);
		table.getColumns().add(column);
		column = new Column("canRead");
		column.setDataType(DataType.BOOLEAN);
		table.getColumns().add(column);
		column = new Column("canWrite");
		column.setDataType(DataType.BOOLEAN);
		table.getColumns().add(column);
		return table;
	}

	/**
	 * ストリームのClose
	 * 
	 * @param stream
	 */
	public static void close(final Closeable stream) {
		if (stream == null) {
			return;
		}
		try {
			stream.close();
		} catch (final IOException e) {
		}
	}

	/**
	 * ストリームのClose
	 * 
	 * @param stream
	 */
	public static void close(final AutoCloseable stream) {
		if (stream == null) {
			return;
		}
		try {
			stream.close();
		} catch (final Exception e) {
		}
	}

	/**
	 * ファイルの存在チェック
	 * 
	 * filePath
	 */
	public static boolean exists(final String filePath) {
		final File file = new File(filePath);
		return file.exists();
	}

	/**
	 * 拡張子除いたファイル名を取得します。
	 * 
	 * @param filePath ファイルパス
	 * @return 拡張子を除いたファイル名
	 */
	public static String getFileNameWithoutExtension(final String filePath) {
		final String fileName = getFileName(filePath);
		final int pos = fileName.lastIndexOf('.');
		if (pos > 0) {
			return fileName.substring(0, pos);
		}
		return fileName;
	}

	/**
	 * 拡張子除いたファイル名を取得します。
	 * 
	 * @param file ファイルパス
	 * @return 拡張子を除いたファイル名
	 */
	public static String getFileNameWithoutExtension(final File file) {
		return getFileNameWithoutExtension(file.getName());
	}

	/**
	 * ファイル名を取得します。
	 * 
	 * @param filePath ファイルパス
	 * @return ファイル名
	 */
	public static String getFileName(final String filePath) {
		final File file = new File(filePath);
		final String fileName = file.getName();
		return fileName;
	}

	/**
	 * ファイルの拡張子を取得します。
	 * 
	 * @param filePath ファイルパス
	 * @return ファイルの拡張子
	 */
	public static String getExtension(final String filePath) {
		final String fileName = getFileName(filePath);
		final int pos = fileName.lastIndexOf('.');
		if (pos > 0) {
			return fileName.substring(pos + 1);
		}
		return "";
	}

	/**
	 * ファイルの拡張子を取得します。
	 * 
	 * @param file ファイル
	 * @return ファイルの拡張子
	 */
	public static String getExtension(final File file) {
		return getExtension(file.getName());
	}

	/**
	 * ファイルの拡張子を取得します。
	 * 
	 * @param path ファイル
	 * @return ファイルの拡張子
	 */
	public static String getExtension(final Path path) {
		if (path == null) {
			return null;
		}
		if (path.getFileName() == null) {
			return null;
		}
		return getExtension(path.getFileName().toString());
	}

	/**
	 * オブジェクトの読み込み
	 * 
	 * @param filePath 読み込むファイルのパス
	 */
	@SuppressWarnings("unchecked")
	public static <T> T readObject(final String filePath) {
		FileInputStream inFile = null;
		ObjectInputStream inObject = null;
		try {
			inFile = new FileInputStream(filePath);
		} catch (final FileNotFoundException e) {
			throw new RuntimeException(e);
		}
		try {
			inObject = new ObjectInputStream(inFile);
			return (T) inObject.readObject();
		} catch (final IOException e) {
			throw new RuntimeException(e);
		} catch (final ClassNotFoundException e) {
			throw new RuntimeException(e);
		} finally {
			close(inObject);
			close(inFile);
		}
	}

	/**
	 * テキストファイルの書き込み
	 * 
	 * @param filePath 読み込むファイルのパス
	 * @param encoding ファイルエンコーディング
	 * @param texts    テキストファイルの文字列(複数行)
	 */
	public static void writeText(final String filePath, final String encoding, final String... texts) {
		writeText(new File(filePath), encoding != null ? Charset.forName(encoding) : null, texts);
	}

	/**
	 * テキストファイルの書き込み
	 * 
	 * @param path    読み込むファイルのパス
	 * @param charset ファイルエンコーディング
	 * @param texts   テキストファイルの文字列(複数行)
	 */
	public static void writeText(final File path, final String charset, final String... texts) {
		writeText(path, charset != null ? Charset.forName(charset) : null, texts);
	}

	/**
	 * テキストファイルの書き込み
	 * 
	 * @param path    読み込むファイルのパス
	 * @param charset ファイルエンコーディング
	 * @param texts   テキストファイルの文字列(複数行)
	 */
	public static void writeText(final File path, final Charset charset, final String... texts) {
		BufferedWriter bw = null;
		try {
			mkDirs(path.getParentFile());
			if (isEmpty(charset)) {
				bw = new BufferedWriter(new FileWriter(path));
			} else {
				bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(path), charset));
			}
			boolean first = true;
			for (final String text : texts) {
				if (!first) {
					bw.newLine();
				} else {
					first = false;
				}
				bw.write(text);
			}
		} catch (final IOException e) {
			throw new RuntimeException(e);
		} finally {
			close(bw);
		}
	}

	/**
	 * テキストファイルの書き込み
	 * 
	 * @param filePath 読み込むファイルのパス
	 * @param obj      書き込オブジェクト
	 */
	public static void writeObject(final String filePath, final Serializable obj) {
		FileOutputStream outFile = null;
		try {
			outFile = new FileOutputStream(filePath);
		} catch (final FileNotFoundException e) {
			throw new RuntimeException(e);
		}
		ObjectOutputStream outObject = null;
		try {
			outObject = new ObjectOutputStream(outFile);
			outObject.writeObject(obj);
		} catch (final IOException e) {
			throw new RuntimeException(e);
		} finally {
			close(outObject);
			close(outFile);
		}
	}

	/**
	 * ディレクトリの作成
	 * 
	 * @param file
	 */
	public static void mkDirs(final File file) {
		if (file != null && file.mkdirs()) {
			log.trace("File#mkdirs():" + file.getPath());
		}
	}

	/**
	 * テキストファイルの読み込み(リストで返却)
	 * 
	 * @param filePath 読み込むファイルのパス
	 * @param encoding ファイルエンコーディング
	 */
	public static List<String> readTextList(final String filePath, final String encoding) {
		FileInputStream is = null;
		try {
			is = new FileInputStream(filePath);
			return readTextList(is, encoding);
		} catch (final IOException e) {
			throw new RuntimeException(e);
		} finally {
			close(is);
		}
	}

	/**
	 * テキストファイルの読み込み(リストで返却)
	 * 
	 * @param file     読み込むファイルのパス
	 * @param encoding ファイルエンコーディング
	 */
	public static List<String> readTextList(final File file, final String encoding) {
		FileInputStream is = null;
		try {
			is = new FileInputStream(file);
			return readTextList(is, encoding);
		} catch (final IOException e) {
			throw new RuntimeException(e);
		} finally {
			close(is);
		}
	}

	/**
	 * テキストファイルの読み込み(リストで返却)
	 * 
	 * @param is       読み込むストリーム
	 * @param encoding ファイルエンコーディング
	 */
	public static List<String> readTextList(final InputStream is, final String encoding) {
		BufferedReader br = null;
		InputStreamReader in = null;
		try {
			if (isEmpty(encoding)) {
				in = new InputStreamReader(is);
			} else {
				in = new InputStreamReader(is, encoding);
			}
			br = new BufferedReader(in);
			final List<String> list = new ArrayList<String>();
			String line;
			while (br.ready()) {
				line = br.readLine();
				list.add(line);
			}
			return list;
		} catch (final IOException e) {
			throw new RuntimeException(e);
		} finally {
			close(br);
			close(in);
			close(is);
		}
	}

	/**
	 * テキストファイルの読み込み(リストで返却)
	 * 
	 * @param path    読み込むストリーム
	 * @param charset ファイルエンコーディング
	 */
	public static List<String> readTextList(final Path path, final Charset charset) {
		try {
			List<String> list;
			if (charset == null) {
				list = Files.readAllLines(path);
			} else {
				list = Files.readAllLines(path, charset);
			}
			return list;
		} catch (final IOException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * テキストファイルの読み込み(リストで返却)
	 * 
	 * @param is       読み込むストリーム
	 * @param encoding ファイルエンコーディング
	 */
	public static String readText(final InputStream is, final String encoding) {
		return readText(is, Charset.forName(encoding));
	}

	/**
	 * テキストファイルの読み込み(リストで返却)
	 * 
	 * @param is       読み込むストリーム
	 * @param encoding ファイルエンコーディング
	 */
	public static String readText(final InputStream is, final Charset encoding) {
		BufferedReader br = null;
		InputStreamReader in = null;
		try {
			if (isEmpty(encoding)) {
				in = new InputStreamReader(is);
			} else {
				in = new InputStreamReader(is, encoding);
			}
			br = new BufferedReader(in);
			final StringBuilder builder = new StringBuilder();
			String line;
			if (br.ready()) {
				line = br.readLine();
				builder.append(line);
			}
			while (br.ready()) {
				line = br.readLine();
				builder.append('\n');
				builder.append(line);
			}
			return builder.toString();
		} catch (final IOException e) {
			throw new RuntimeException(e);
		} finally {
			close(br);
			close(in);
			close(is);
		}
	}

	/**
	 * テキストファイルを読み込みます
	 * 
	 * @param filePath 読み込むファイルのパス
	 * @param encoding ファイルエンコーディング
	 * @return テキストファイルの文字列
	 */
	public static String readText(final String filePath, final String encoding) {
		InputStream is = null;
		try {
			is = getInputStream(filePath);
			return readText(is, encoding);
		} finally {
			close(is);
		}
	}

	/**
	 * テキストファイルを読み込みます
	 * 
	 * @param file     読み込むファイルのパス
	 * @param encoding ファイルエンコーディング
	 * @return テキストファイルの文字列
	 */
	public static String readText(final File file, final String encoding) {
		InputStream is = null;
		try {
			is = getInputStream(file);
			return readText(is, encoding);
		} finally {
			close(is);
		}
	}

	/**
	 * テキストファイルを読み込みます
	 * 
	 * @param file     読み込むファイルのパス
	 * @param encoding ファイルエンコーディング
	 * @return テキストファイルの文字列
	 */
	public static String readText(final File file, final Charset encoding) {
		InputStream is = null;
		try {
			is = getInputStream(file);
			return readText(is, encoding);
		} finally {
			close(is);
		}
	}

	/**
	 * テキストファイルを読み込みます
	 * 
	 * @param path     読み込むファイルのパス
	 * @param encoding ファイルエンコーディング
	 * @return テキストファイルの文字列
	 */
	public static String readText(final Path path, final String encoding) {
		return readText(path.toFile(), encoding);
	}

	/**
	 * ファイル・ディレクトリの削除
	 * 
	 * @param path 対象のパス
	 */
	public static boolean remove(final String path) {
		final File file = new File(path);
		return remove(file);
	}

	/**
	 * ファイル・ディレクトリの削除
	 * 
	 * @param file 対象のファイルオブジェクト
	 */
	public static boolean remove(final File file) {
		if (!file.isDirectory()) {
			return file.delete();
		}
		final File[] files = file.listFiles();
		if (files != null) {
			for (final File child : files) {
				remove(child);
			}
		}
		return file.delete();
	}

	/**
	 * コピー元のパスから、コピー先のパスへファイルのコピー
	 * 
	 * @param srcPath  コピー元のパス
	 * @param destPath コピー先のパス
	 */
	public static void copyFile(final String srcPath, final String destPath) {
		FileInputStream is = null;
		FileOutputStream os = null;
		FileChannel srcChannel = null;
		FileChannel destChannel = null;
		try {
			final File file = new File(destPath);
			mkDirs(file);
			is = new FileInputStream(srcPath);
			os = new FileOutputStream(destPath);
			srcChannel = is.getChannel();
			destChannel = os.getChannel();
			srcChannel.transferTo(0, srcChannel.size(), destChannel);
		} catch (final Exception e) {
			throw new RuntimeException(e);
		} finally {
			close(srcChannel);
			close(destChannel);
			close(is);
			close(os);
		}
	}

	/**
	 * ファイル・ディレクトリのリネーム
	 * 
	 * @param srcPath
	 * @param destPath
	 */
	public static boolean rename(final String srcPath, final String destPath) {
		if (eq(srcPath, destPath)) {
			return false;
		}
		final File srcFile = new File(srcPath);
		if (!srcFile.exists()) {
			return false;
		}
		final File destFile = new File(destPath);
		return srcFile.renameTo(destFile);
	}

	/**
	 * readerの読み込み
	 * 
	 * @param reader
	 */
	public static String read(final Reader reader) {
		BufferedReader br = null;
		if (reader instanceof BufferedReader) {
			br = cast(reader);
		} else {
			br = new BufferedReader(reader);
		}
		final StringBuilder builder = new StringBuilder();
		String line = null;
		try {
			while ((line = br.readLine()) != null) {
				builder.append(line).append('\n');
			}
		} catch (final IOException e) {
			throw new RuntimeException(e);
		} finally {
			close(br);
			close(reader);
		}
		return builder.toString();
	}

	/**
	 * パス文字列を結合します
	 * 
	 * @param paths
	 */
	public static String combinePath(final String... paths) {
		char separator = '/';
		for (final String path : paths) {
			if (path != null) {
				if (path.contains("\\")) {
					separator = '\\';
					break;
				}
				if (path.contains("/")) {
					separator = '/';
					break;
				}
			}
		}
		final StringBuilder result = new StringBuilder(128);
		for (int i = 0; i < paths.length; i++) {
			final String path = paths[i];
			if (!isEmpty(path)) {
				if (result.length() > 0) {
					if (result.charAt(result.length() - 1) == separator) {
						if (path.charAt(0) == separator) {
							result.append(path.substring(1));
						} else {
							result.append(path);
						}
					} else {
						if (path.charAt(0) == separator) {
							result.append(path);
						} else {
							result.append(separator);
							result.append(path);
						}
					}
				} else {
					result.append(path);
				}
			}
		}
		return result.toString();
	}

	/**
	 * パス文字列を結合します
	 * 
	 * @param args
	 */
	public static String combinePath(final Object... args) {
		final List<String> pathList = CommonUtils.list();
		for (final Object arg : args) {
			if (arg == null) {
				continue;
			} else if (arg instanceof String) {
				pathList.add((String) arg);
			} else if (arg instanceof File) {
				pathList.add(((File) arg).getAbsolutePath());
			} else {
				pathList.add(arg.toString());
			}
		}
		return combinePath(pathList.toArray(new String[0]));
	}

	/**
	 * 親ディレクトリの作成
	 * 
	 * @param filePath 親ディレクトリを作成するファイル、ディレクトリのパス
	 */
	public static void createParentDirectory(final String filePath) {
		final File file = new File(filePath);
		createParentDirectory(file);
	}

	/**
	 * 親ディレクトリの作成
	 * 
	 * @param file 親ディレクトリを作成するファイル、ディレクトリのパスのファイルオブジェクト
	 */
	public static void createParentDirectory(final File file) {
		if (file.exists()) {
			return;
		}
		if (file.getPath() == null) {
			return;
		}
		final File parentFile = file.getParentFile();
		if (parentFile != null) {
			mkDirs(parentFile);
		}
	}

	/**
	 * 親ディレクトリの作成
	 * 
	 * @param path 親ディレクトリを作成するファイル、ディレクトリのパスのファイルオブジェクト
	 */
	public static void createParentDirectory(final Path path) {
		if (!Files.exists(path)) {
			return;
		}
		try {
			Files.createDirectories(path);
		} catch (final IOException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * クラスのリソースもしくはパスから適切なInputStreamを取得します
	 * 
	 * @param clazz
	 * @param path
	 */
	public static InputStream getInputStream(final Class<?> clazz, final String path) {
		final InputStream stream = clazz.getResourceAsStream(path);
		if (stream != null) {
			return stream;
		}
		return getInputStream(path);
	}

	/**
	 * パスから適切なInputStreamを取得します
	 * 
	 * @param path
	 */
	public static InputStream getInputStream(final String path) {
		InputStream stream = Thread.currentThread().getContextClassLoader().getResourceAsStream(path);
		if (stream == null) {
			final File file = new File(path);
			try {
				return new FileInputStream(file);
			} catch (final Exception ex) {
				//
			}
			try {
				final URL sourceUrl = new URL(path);
				if (sourceUrl != null) {
					stream = sourceUrl.openStream();
				}
			} catch (final Exception e) {
				return null;
			}
		}
		return stream;
	}

	/**
	 * パスから適切なInputStreamを取得します
	 * 
	 * @param file
	 */
	public static InputStream getInputStream(final File file) {
		try {
			return new FileInputStream(file);
		} catch (final IOException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * 指定したオブジェクトのパッケージ内のリソースをUTF8の文字列として取得します。
	 * 
	 * @param obj      対象のパッケージの基準になるオブジェクト
	 * @param fileName ファイル名
	 * @return ファイル名のUTF8の文字列
	 */
	public static String getResource(Object obj, final String fileName) {
		InputStream is = getResourceAsStream(obj, fileName);
		final String sql = FileUtils.readText(is, "utf8");
		return sql;
	}

	/**
	 * 指定したオブジェクトのパッケージ内のリソースを取得します。
	 * 
	 * @param obj      対象のパッケージの基準になるオブジェクト
	 * @param fileName ファイル名
	 * @return リソース
	 */
	public static InputStream getResourceAsStream(Object obj, final String fileName) {
		InputStream is = getInputStream(obj.getClass(), fileName);
		if (is != null) {
			return is;
		}
		String path;
		if (obj instanceof Class) {
			path = ((Class<?>) obj).getPackage().getName().replace(".", "/") + "/" + fileName;
		} else {
			path = obj.getClass().getPackage().getName().replace(".", "/") + "/" + fileName;
		}
		is = ClassLoader.getSystemResourceAsStream(path);
		if (is != null) {
			return is;
		} else {
			is = ClassLoader.getPlatformClassLoader().getResourceAsStream(path);
		}
		if (is != null) {
			return is;
		} else {
			try {
				is = ModuleHelper.getInstance().getResourceAsStream(path);
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}
		return is;
	}

	/**
	 * URLからファイルパスに変換します。
	 * 
	 * @param url URL
	 * @return ファイルパス
	 */
	public static String toPath(URL url) {
		String file = url.getFile();
		try {
			return URLDecoder.decode(file, "UTF-8");
		} catch (UnsupportedEncodingException e) {
			throw new RuntimeException(e);
		}
	}
}
