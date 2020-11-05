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
package com.sqlapp.data.db.dialect.resolver;

import java.io.Serializable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.sqlapp.data.db.dialect.Dialect;

/**
 * 
 * @author satoh
 * 
 */
public abstract class ProductNameDialectResolver implements Serializable,
		Comparable<ProductNameDialectResolver> {

	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = 1L;

	private final Pattern matchPattern;

	private final VersionResolver versionResolver;

	/**
	 * バージョンに関係ないDialect用のコンストラクタ
	 * 
	 * @param dialect DB dialect
	 */
	public ProductNameDialectResolver(Dialect dialect) {
		this.matchPattern = Pattern.compile(
				getRegexName(dialect.getProductName()),
				Pattern.CASE_INSENSITIVE);
		this.versionResolver = new VersonInSensitiveResolver(dialect);
	}

	private String getRegexName(String name) {
		return name.replaceAll("\\s*", ".*") + ".*";
	}

	/**
	 * バージョンに関係ないDialect用のコンストラクタ
	 * 
	 * @param regex
	 * @param dialect DB dialect
	 */
	public ProductNameDialectResolver(String regex, Dialect dialect) {
		this.matchPattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
		this.versionResolver = new VersonInSensitiveResolver(dialect);
	}

	public ProductNameDialectResolver(String regex,
			VersionResolver versionResolver) {
		this.matchPattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
		this.versionResolver = versionResolver;
	}

	/**
	 * DB製品名、メジャーバージョン、マイナーバージョンに一致するDialectを取得する
	 * 
	 * @param dbProductName database product name
	 * @param majorVersion major version
	 * @param minorVersion minor version
	 * @return dialect
	 */
	public Dialect getDialect(String dbProductName, int majorVersion,
			int minorVersion) {
		if (!match(dbProductName)) {
			return null;
		}
		return getDialect(majorVersion, minorVersion);
	}

	/**
	 * DB製品名、メジャーバージョン、マイナーバージョンに一致するDBダイアレクトを取得する
	 * 
	 * @param dbProductName database product name
	 * @param majorVersion major version
	 * @param minorVersion minor version
	 * @param revision revision
	 * @return dialect
	 */
	public Dialect getDialect(String dbProductName, int majorVersion,
			int minorVersion, Integer revision) {
		if (!match(dbProductName)) {
			return null;
		}
		return getDialect(majorVersion, minorVersion, revision);
	}

	protected boolean match(String dbProductName) {
		if (dbProductName==null) {
			return false;
		}
		final Matcher matcher = matchPattern.matcher(dbProductName);
		return matcher.matches();
	}

	public Dialect getDialect(int majorVersion, int minorVersion,
			Integer revision) {
		return versionResolver.getDialect(majorVersion, minorVersion, revision);
	}

	public Dialect getDialect(int majorVersion, int minorVersion) {
		return getDialect(majorVersion, minorVersion, null);
	}

	@Override
	public String toString() {
		return "DialectResolver[" + matchPattern.toString() + "]";
	}

	/**
	 * Dialect Resolver Order
	 * 
	 * @return order
	 */
	protected int order() {
		return 0;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Comparable#compareTo(java.lang.Object)
	 */
	public int compareTo(ProductNameDialectResolver args) {
		if (this.order() > args.order()) {
			return 1;
		} else if (this.order() < args.order()) {
			return -1;
		}
		return 0;
	}
}
