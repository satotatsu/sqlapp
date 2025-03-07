/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-firebird.
 *
 * sqlapp-core-firebird is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-firebird is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-firebird.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.firebird.resolver;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.DialectUtils;
import com.sqlapp.data.db.dialect.firebird.Firebird;
import com.sqlapp.data.db.dialect.firebird.Firebird20;
import com.sqlapp.data.db.dialect.firebird.Firebird25;
import com.sqlapp.data.db.dialect.firebird.Firebird30;
import com.sqlapp.data.db.dialect.resolver.ProductNameDialectResolver;
import com.sqlapp.data.db.dialect.resolver.VersionResolver;

public class FirebirdDialectResolver extends ProductNameDialectResolver {

	public FirebirdDialectResolver() {
		super("Firebird.*", new FirebirdVersionResolver());
	}

	/**
	 * Firebird用のバージョンResolver
	 * 
	 * @author satoh
	 * 
	 */
	public static class FirebirdVersionResolver implements VersionResolver {

		/**
		 * serialVersionUID
		 */
		private static final long serialVersionUID = 1L;

		public static class DialectHolder {
			final static Dialect defaultDialect30 = DialectUtils.getInstance(Firebird30.class);
			final static Dialect defaultDialect25 = DialectUtils.getInstance(Firebird25.class, () -> defaultDialect30);
			final static Dialect defaultDialect20 = DialectUtils.getInstance(Firebird20.class, () -> defaultDialect25);
			final static Dialect defaultDialect = DialectUtils.getInstance(Firebird.class, () -> defaultDialect20);
		}

		/**
		 * コンストラクタ
		 */
		public FirebirdVersionResolver() {
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see com.sqlapp.data.db.dialect.resolver.VersionResolver#getDialect(int, int,
		 * java.lang.Integer)
		 */
		@Override
		public Dialect getDialect(final int majorVersion, final int minorVersion, final Integer revision) {
			if (majorVersion >= 3) {
				return DialectHolder.defaultDialect30;
			} else if (majorVersion >= 2) {
				if (minorVersion < 5) {
					return DialectHolder.defaultDialect20;
				}
				return DialectHolder.defaultDialect25;
			}
			return DialectHolder.defaultDialect;
		}

	}
}
