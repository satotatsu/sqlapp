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

package com.sqlapp.data.db.dialect.resolver;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.sqlapp.data.db.dialect.AbstractDialectResolver;
import com.sqlapp.data.db.dialect.Dialect;

/**
 * 製品名毎のDialectResolver
 * 
 * @author satoh
 * 
 */
public abstract class ProductNameDialectResolver extends AbstractDialectResolver
		implements Comparable<ProductNameDialectResolver> {

	private final Pattern matchPattern;

	private final VersionResolver versionResolver;

	/**
	 * バージョンに関係ないDialect用のコンストラクタ
	 * 
	 * @param dialect DB dialect
	 */
	public ProductNameDialectResolver(Dialect dialect) {
		this.matchPattern = Pattern.compile(getRegexName(dialect.getProductName()), Pattern.CASE_INSENSITIVE);
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

	public ProductNameDialectResolver(String regex, VersionResolver versionResolver) {
		this.matchPattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
		this.versionResolver = versionResolver;
	}

	@Override
	public Dialect getDialect(String productName, int majorVersion, int minorVersion) {
		if (!match(productName)) {
			return null;
		}
		return getDialect(majorVersion, minorVersion);
	}

	@Override
	public Dialect getDialect(String productName, int majorVersion, int minorVersion, Integer revision) {
		if (!match(productName)) {
			return null;
		}
		return getDialect(majorVersion, minorVersion, revision);
	}

	protected boolean match(String dbProductName) {
		if (dbProductName == null) {
			return false;
		}
		final Matcher matcher = matchPattern.matcher(dbProductName);
		return matcher.matches();
	}

	public Dialect getDialect(int majorVersion, int minorVersion, Integer revision) {
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
