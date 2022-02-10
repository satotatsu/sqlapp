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

package com.sqlapp.util;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.net.URLDecoder;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.sqlapp.util.ResourceFinder.ResourceInfo;

/**
 * リソースの検索用クラス
 * 
 * @author tatsuo satoh
 * 
 */
public class ResourceFinder extends AbstractClassFinder<ResourceInfo> {

	private String[] extensions = new String[] { "properties" };

	private Set<String> extensionSets = CommonUtils.set("properties");

	/**
	 * コンストラクター
	 */
	public ResourceFinder() {
		super();
	}

	/**
	 * コンストラクター
	 * 
	 * @param classLoader
	 */
	public ResourceFinder(ClassLoader classLoader) {
		super(classLoader);
	}

	@Override
	protected void itialize() {
		addResourceSearcher(new VfsResourceSearcher());
		addResourceSearcher(new JarResourceSearcher());
		addResourceSearcher(new FileResourceSearcher());
	}

	/**
	 * @return the extensions
	 */
	public String[] getExtensions() {
		return extensions;
	}

	/**
	 * @param extensions
	 *            the extensions to set
	 */
	public void setExtensions(String... extensions) {
		extensionSets = CommonUtils.set(extensions);
		this.extensions = extensionSets.toArray(new String[0]);
	}

	/**
	 * 指定されたパッケージからリソースファイルを取得します
	 * 
	 * @param packageName
	 *            パッケージ名
	 */
	public List<ResourceInfo> find(String packageName) {
		ClassLoader classLoader = this.classLoader;
		List<ResourceInfo> list = findClasses(classLoader, packageName, false);
		return list;
	}

	/**
	 * 指定されたパッケージから再帰的にリソースファイルを取得します
	 * 
	 * @param packageName
	 *            パッケージ名
	 */
	public List<ResourceInfo> findRecursive(String packageName) {
		ClassLoader classLoader = this.classLoader;
		List<ResourceInfo> list = findClasses(classLoader, packageName, true);
		return list;
	}

	@Override
	protected void merge(List<ResourceInfo> resources,
			List<ResourceInfo> addResources) {
		resources.addAll(addResources);
	}

	protected static String getPath(URL url) {
		String file = url.getFile();
		try {
			return URLDecoder.decode(file, "ISO8859_1");
		} catch (UnsupportedEncodingException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	protected void initialize(Searcher<ResourceInfo> searcher) {
		((ResourceSearcher) searcher).setExtensionSets(this.extensionSets);
	}

	/**
	 * リソース情報
	 * 
	 * @author tatsuo satoh
	 * 
	 */
	public static class ResourceInfo {
		final URI uri;
		final ClassLoader classLoader;
		final String packageName;
		final String fileName;

		public ResourceInfo(URI uri, ClassLoader classLoader,
				String packageName, String fileName) {
			this.uri = uri;
			this.classLoader = classLoader;
			this.packageName = packageName;
			this.fileName = fileName;
		}

		/**
		 * テキストとして読み込んだ結果を返します
		 * 
		 * @param encoding
		 */
		public List<String> readAsText(String encoding) {
			if (CommonUtils.isEmpty(this.getFileName())) {
				return Collections.emptyList();
			}
			InputStream is = null;
			if ("file".equals(uri.getScheme())) {
				try {
					is = new FileInputStream(
							ResourceFinder.getPath(uri.toURL()));
					return FileUtils.readTextList(is, encoding);
				} catch (FileNotFoundException e) {
					throw new RuntimeException(e);
				} catch (MalformedURLException e) {
					throw new RuntimeException(e);
				} finally {
					FileUtils.close(is);
				}
			} else {
				String path = FileUtils.combinePath(
						this.packageName.replace(".", "/"), this.fileName);
				is = this.classLoader.getResourceAsStream(path);
				return FileUtils.readTextList(is, encoding);
			}
		}

		/**
		 * テキストとして読み込んだ結果を返します
		 * 
		 */
		public List<String> readAsText() {
			if (CommonUtils.isEmpty(this.getFileName())) {
				return Collections.emptyList();
			}
			InputStream is = null;
			if ("file".equals(uri.getScheme())) {
				try {
					is = new FileInputStream(
							ResourceFinder.getPath(uri.toURL()));
					return FileUtils.readTextList(is, null);
				} catch (FileNotFoundException e) {
					throw new RuntimeException(e);
				} catch (MalformedURLException e) {
					throw new RuntimeException(e);
				} finally {
					FileUtils.close(is);
				}
			} else {
				String path = FileUtils.combinePath(
						this.packageName.replace(".", "/"), this.fileName);
				is = this.classLoader.getResourceAsStream(path);
				return FileUtils.readTextList(is, null);
			}
		}

		/**
		 * @return the classLoader
		 */
		public ClassLoader getClassLoader() {
			return classLoader;
		}

		/**
		 * @return the uri
		 */
		public URI getUri() {
			return uri;
		}

		/**
		 * @return the packageName
		 */
		public String getPackageName() {
			return packageName;
		}

		/**
		 * @return the fileName
		 */
		public String getFileName() {
			return fileName;
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see java.lang.Object#toString()
		 */
		@Override
		public String toString() {
			StringBuilder builder = new StringBuilder(this.getClass()
					.getSimpleName());
			builder.append("[");
			builder.append("packageName=").append(packageName);
			builder.append(", fileName=").append(fileName);
			builder.append(", uri=").append(this.uri);
			builder.append("]");
			return builder.toString();
		}

		/**
		 * プロパティとして読み込みを行います
		 * 
		 * @return プロパティ
		 */
		public Map<String, String> readAsProperties() {
			return this.toPropertyMap(readAsText("ISO8859_1"));
		}

		/**
		 * プロパティとして読み込みを行います
		 * 
		 * @param encoding
		 *            プロパティファイルのエンコーディング
		 * @return プロパティ
		 */
		public Map<String, String> readAsProperties(String encoding) {
			return this.toPropertyMap(readAsText(encoding));
		}

		private Map<String, String> toPropertyMap(List<String> texts) {
			Map<String, String> map = CommonUtils.map();
			int i = 0;
			while (true) {
				String text = getString(texts, i);
				if (text == null) {
					break;
				}
				if (text.startsWith("#")) {
					i++;
					continue;
				}
				if (!text.contains("=")) {
					i++;
					continue;
				}
				int pos = text.indexOf('=');
				String key = text.substring(0, pos);
				StringBuilder builder = new StringBuilder();
				String value = text.substring(pos + 1);
				if (value.endsWith("\\")) {
					builder.append(convertValue(text));
					int j = i + 1;
					while (true) {
						value = getString(texts, j);
						j++;
						if (value == null) {
							break;
						}
						builder.append(convertValue(value));
						if (!value.endsWith("\\")) {
							break;
						}
					}
				} else {
					builder.append(convertValue(value));
				}
				map.put(key, builder.toString());
				i++;
			}
			return map;
		}

		private String getString(List<String> texts, int i) {
			if (i >= texts.size()) {
				return null;
			}
			return texts.get(i);
		}

		private static final Pattern UNICODE_PATTERN = Pattern
				.compile("\\\\u[0-9a-f]{4,4}");

		private String convertValue(String text) {
			Matcher matcher = UNICODE_PATTERN.matcher(text);
			List<MatchResult> matchResults = CommonUtils.list();
			while (matcher.find()) {
				MatchResult matchResult = matcher.toMatchResult();
				matchResults.add(matchResult);
			}
			for (MatchResult matchResult : matchResults) {
				String val = matchResult.group();
				String decode = convertToOiginal(val);
				text = text.replace(val, decode);
			}
			return trimLastValue(text);
		}

		private String convertToOiginal(String unicode) {
			String[] codeStrs = unicode.split("\\\\u");
			int[] codePoints = new int[codeStrs.length - 1]; // 最初が空文字なのでそれを抜かす
			for (int i = 0; i < codePoints.length; i++) {
				codePoints[i] = Integer.parseInt(codeStrs[i + 1], 16);
			}
			String encodedText = new String(codePoints, 0, codePoints.length);
			return encodedText;
		}

		private String trimLastValue(String text) {
			if (text.endsWith("\\")) {
				return text.substring(0, text.length() - 1);
			}
			return text;
		}
	}
}