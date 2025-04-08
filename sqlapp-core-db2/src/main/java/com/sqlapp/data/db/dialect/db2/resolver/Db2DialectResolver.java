/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-db2.
 *
 * sqlapp-core-db2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-db2 is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-db2.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.db2.resolver;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.db2.DialectHolder;
import com.sqlapp.data.db.dialect.resolver.ProductNameDialectResolver;
import com.sqlapp.data.db.dialect.resolver.VersionResolver;

public class Db2DialectResolver extends ProductNameDialectResolver {

	public Db2DialectResolver() {
		super("DB2.*", new Db2VersionResolver());
	}

	/**
	 * DB2用のバージョンResolver
	 * 
	 * @author satoh
	 * 
	 */
	static class Db2VersionResolver implements VersionResolver {

		/**
		 * serialVersionUID
		 */
		private static final long serialVersionUID = 1L;

		/**
		 * コンストラクタ
		 */
		public Db2VersionResolver() {
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see com.sqlapp.data.db.dialect.resolver.VersionResolver#getDialect(int, int,
		 * java.lang.Integer)
		 */
		@Override
		public Dialect getDialect(final int majorVersion, final int minorVersion, final Integer revision) {
			if (majorVersion >= 11) {
				return DialectHolder.Db2_1110Dialect;
			} else if (majorVersion >= 10) {
				if (minorVersion >= 5) {
					return DialectHolder.Db2_1050Dialect;
				}
				return DialectHolder.Db2_1010Dialect;
			} else if (majorVersion >= 9) {
				if (minorVersion >= 8) {
					return DialectHolder.Db2_980Dialect;
				} else if (minorVersion >= 7) {
					return DialectHolder.Db2_970Dialect;
				} else if (minorVersion >= 5) {
					return DialectHolder.Db2_950Dialect;
				}
			}
			return DialectHolder.defaultDialect;
		}

	}
}
