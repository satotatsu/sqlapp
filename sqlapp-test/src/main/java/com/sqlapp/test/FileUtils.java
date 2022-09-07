/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-test.
 *
 * sqlapp-test is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-test is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-test.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.sqlapp.test;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.Reader;
import java.net.URL;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ファイル関連のユーティリティメソッド
 * 
 * @author SATOH
 * 
 */
public final class FileUtils {

	/**
	 * 条件(ファイル名の正規表現)で指定したファイルパス一覧の取得
	 * 
	 * @param rootPath
	 *            操作対象のルートパス
	 * @param fileFilter
	 *            条件
	 * @param caseInsensitive
	 *            大文字小文字の区別
	 * @param recursive
	 *            再帰操作
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

	private static void searchFileList(final File targetFile, final Pattern pattern,
			final boolean recursive, final List<String> fileList) {
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
		if (files!=null){
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
	 * @param filePath
	 *            ファイルパス
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
	 * @param file
	 *            ファイルパス
	 * @return 拡張子を除いたファイル名
	 */
	public static String getFileNameWithoutExtension(final File file) {
		return getFileNameWithoutExtension(file.getName());
	}
	
	/**
	 * ファイル名を取得します。
	 * 
	 * @param filePath
	 *            ファイルパス
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
	 * @param filePath
	 *            ファイルパス
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
		if (path==null) {
			return null;
		}
		if (path.getFileName()==null) {
			return null;
		}
		return getExtension(path.getFileName().toString());
	}

	/**
	 * オブジェクトの読み込み
	 * 
	 * @param filePath
	 *            読み込むファイルのパス
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
	 * ディレクトリの作成
	 * 
	 * @param file
	 */
	public static void mkDirs(final File file) {
		if (file!=null&&file.mkdirs()) {
		}
	}

	/**
	 * テキストファイルの読み込み(リストで返却)
	 * 
	 * @param filePath
	 *            読み込むファイルのパス
	 * @param encoding
	 *            ファイルエンコーディング
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
	 * @param file
	 *            読み込むファイルのパス
	 * @param encoding
	 *            ファイルエンコーディング
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
	 * @param is
	 *            読み込むストリーム
	 * @param encoding
	 *            ファイルエンコーディング
	 */
	public static List<String> readTextList(final InputStream is, final String encoding) {
		BufferedReader br = null;
		InputStreamReader in = null;
		try {
			if (encoding==null||encoding.isEmpty()) {
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
	 * @param path
	 *            読み込むストリーム
	 * @param charset
	 *            ファイルエンコーディング
	 */
	public static List<String> readTextList(final Path path, final Charset charset) {
		try {
			List<String> list;
			if (charset==null){
				list=Files.readAllLines(path);
			} else{
				list=Files.readAllLines(path, charset);
			}
			return list;
		} catch (final IOException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * テキストファイルの読み込み(リストで返却)
	 * 
	 * @param is
	 *            読み込むストリーム
	 * @param encoding
	 *            ファイルエンコーディング
	 */
	public static String readText(final InputStream is, final String encoding) {
		return readText(is, Charset.forName(encoding));
	}
	
	/**
	 * テキストファイルの読み込み(リストで返却)
	 * 
	 * @param is
	 *            読み込むストリーム
	 * @param encoding
	 *            ファイルエンコーディング
	 */
	public static String readText(final InputStream is, final Charset encoding) {
		BufferedReader br = null;
		InputStreamReader in = null;
		try {
			if (encoding==null) {
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
	 * @param filePath
	 *            読み込むファイルのパス
	 * @param encoding
	 *            ファイルエンコーディング
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
	 * @param file
	 *            読み込むファイルのパス
	 * @param encoding
	 *            ファイルエンコーディング
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
	 * @param file
	 *            読み込むファイルのパス
	 * @param encoding
	 *            ファイルエンコーディング
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
	 * @param path
	 *            読み込むファイルのパス
	 * @param encoding
	 *            ファイルエンコーディング
	 * @return テキストファイルの文字列
	 */
	public static String readText(final Path path, final String encoding) {
		return readText(path.toFile(), encoding);
	}

	/**
	 * ファイル・ディレクトリの削除
	 * 
	 * @param path
	 *            対象のパス
	 */
	public static boolean remove(final String path) {
		final File file = new File(path);
		return remove(file);
	}

	/**
	 * ファイル・ディレクトリの削除
	 * 
	 * @param file
	 *            対象のファイルオブジェクト
	 */
	public static boolean remove(final File file) {
		if (!file.isDirectory()) {
			return file.delete();
		}
		final File[] files=file.listFiles();
		if (files!=null){
			for (final File child : files) {
				remove(child);
			}
		}
		return file.delete();
	}

	/**
	 * コピー元のパスから、コピー先のパスへファイルのコピー
	 * 
	 * @param srcPath
	 *            コピー元のパス
	 * @param destPath
	 *            コピー先のパス
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
		if (Objects.equals(srcPath, destPath)) {
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
			br = (BufferedReader)(reader);
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
			if (path!=null&&!path.isEmpty()) {
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
		final List<String> pathList=new ArrayList<>();
		for(final Object arg:args){
			if (arg==null){
				continue;
			} else if (arg instanceof String){
				pathList.add((String)arg);
			}else if (arg instanceof File){
				pathList.add(((File)arg).getAbsolutePath());
			} else{
				pathList.add(arg.toString());
			}
		}
		return combinePath(pathList.toArray(new String[0]));
	}
	
	/**
	 * 親ディレクトリの作成
	 * 
	 * @param filePath
	 *            親ディレクトリを作成するファイル、ディレクトリのパス
	 */
	public static void createParentDirectory(final String filePath) {
		final File file = new File(filePath);
		createParentDirectory(file);
	}

	/**
	 * 親ディレクトリの作成
	 * 
	 * @param file
	 *            親ディレクトリを作成するファイル、ディレクトリのパスのファイルオブジェクト
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
	 * @param path
	 *            親ディレクトリを作成するファイル、ディレクトリのパスのファイルオブジェクト
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
		InputStream stream = Thread.currentThread().getContextClassLoader()
				.getResourceAsStream(path);
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

}
