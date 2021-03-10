/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-hsql.
 *
 * sqlapp-core-hsql is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-hsql is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-hsql.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.sqlapp.data.db.dialect.resolver;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.DialectUtils;
import com.sqlapp.data.db.dialect.Hsql;
import com.sqlapp.data.db.dialect.Hsql2_0_0;
import com.sqlapp.data.db.dialect.Hsql2_1_0;
import com.sqlapp.data.db.dialect.Hsql2_2_0;
import com.sqlapp.data.db.dialect.Hsql2_3_0;
import com.sqlapp.data.db.dialect.Hsql2_3_4;
import com.sqlapp.data.db.dialect.Hsql2_4_0;

/**
 * Dialect resolver for HSQL
 * 
 * @author satoh
 * 
 */
public class HsqlDialectResolver extends ProductNameDialectResolver {

	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = 1L;

	public HsqlDialectResolver() {
		super("HSQL.*", new HsqlVersionResolver());
	}

	/**
	 * HSQL用のバージョンResolver
	 * 
	 * @author satoh
	 * 
	 */
	static class HsqlVersionResolver implements VersionResolver {

		/**
		 * serialVersionUID
		 */
		private static final long serialVersionUID = 1L;

		static class DialectHolder {
			final static Dialect defaultDialect2_4_0 = DialectUtils
					.getInstance(Hsql2_4_0.class);
			final static Dialect defaultDialect2_3_4 = DialectUtils
					.getInstance(Hsql2_3_4.class, ()->defaultDialect2_4_0);
			final static Dialect defaultDialect2_3_0 = DialectUtils
					.getInstance(Hsql2_3_0.class, ()->defaultDialect2_3_4);
			final static Dialect defaultDialect2_2_0 = DialectUtils
					.getInstance(Hsql2_2_0.class, ()->defaultDialect2_3_0);
			final static Dialect defaultDialect2_1_0 = DialectUtils
					.getInstance(Hsql2_1_0.class, ()->defaultDialect2_2_0);
			final static Dialect defaultDialect2_0_0 = DialectUtils
					.getInstance(Hsql2_0_0.class, ()->defaultDialect2_1_0);
			final static Dialect defaultDialect = DialectUtils.getInstance(
					Hsql.class, ()->defaultDialect2_0_0);
		}

		/**
		 * コンストラクタ
		 */
		public HsqlVersionResolver() {
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
			if (majorVersion >= 2) {
				if (minorVersion>=4) {
					return DialectHolder.defaultDialect2_4_0;
				}else if (minorVersion>=3) {
					if (revision!=null&&revision.compareTo(4)>=0) {
						return DialectHolder.defaultDialect2_3_4;
					}
					return DialectHolder.defaultDialect2_3_0;
				}else if (minorVersion>=2) {
					return DialectHolder.defaultDialect2_2_0;
				}else if (minorVersion>=1) {
					return DialectHolder.defaultDialect2_1_0;
				}
				return DialectHolder.defaultDialect2_0_0;
			}
			return DialectHolder.defaultDialect;
		}

	}

}
