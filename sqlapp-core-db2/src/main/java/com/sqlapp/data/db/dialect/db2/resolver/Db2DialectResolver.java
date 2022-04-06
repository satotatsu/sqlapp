/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
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
 * along with sqlapp-core-db2.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.sqlapp.data.db.dialect.db2.resolver;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.DialectUtils;
import com.sqlapp.data.db.dialect.db2.Db2;
import com.sqlapp.data.db.dialect.db2.Db2_1010;
import com.sqlapp.data.db.dialect.db2.Db2_1050;
import com.sqlapp.data.db.dialect.db2.Db2_1110;
import com.sqlapp.data.db.dialect.db2.Db2_950;
import com.sqlapp.data.db.dialect.db2.Db2_970;
import com.sqlapp.data.db.dialect.db2.Db2_980;
import com.sqlapp.data.db.dialect.resolver.ProductNameDialectResolver;
import com.sqlapp.data.db.dialect.resolver.VersionResolver;

public class Db2DialectResolver extends ProductNameDialectResolver {

	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = 1L;

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

		static class DialectHolder {
			final static Db2_1110 Db2_1110Dialect = DialectUtils
					.getInstance(Db2_1110.class);
			final static Db2_1050 Db2_1050Dialect = DialectUtils
					.getInstance(Db2_1050.class, ()->Db2_1110Dialect);
			final static Db2_1010 Db2_1010Dialect = DialectUtils
					.getInstance(Db2_1010.class, ()->Db2_1050Dialect);
			final static Db2_980 Db2_980Dialect = DialectUtils
					.getInstance(Db2_980.class, ()->Db2_1010Dialect);
			final static Db2_970 Db2_970Dialect = DialectUtils
					.getInstance(Db2_970.class, ()->Db2_980Dialect);
			final static Db2_950 Db2_950Dialect = DialectUtils
					.getInstance(Db2_950.class, ()->Db2_970Dialect);
			final static Db2 defaultDialect = DialectUtils
					.getInstance(Db2.class, ()->Db2_950Dialect);
		}

		/**
		 * コンストラクタ
		 */
		public Db2VersionResolver() {
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see
		 * com.sqlapp.data.db.dialect.resolver.VersionResolver#getDialect(int,
		 * int, java.lang.Integer)
		 */
		@Override
		public Dialect getDialect(final int majorVersion, final int minorVersion,
				final Integer revision) {
			if (majorVersion>=11) {
				return DialectHolder.Db2_1110Dialect;
			}else if (majorVersion>=10) {
				if (minorVersion>=5) {
					return DialectHolder.Db2_1050Dialect;
				}
				return DialectHolder.Db2_1010Dialect;
			}else if (majorVersion>=9) {
				if (minorVersion>=8) {
					return DialectHolder.Db2_980Dialect;
				}else if (minorVersion>=7) {
					return DialectHolder.Db2_970Dialect;
				}else if (minorVersion>=5) {
					return DialectHolder.Db2_950Dialect;
				}
			}
			return DialectHolder.defaultDialect;
		}

	}
}
