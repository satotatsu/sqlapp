/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
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
 * along with sqlapp-core-firebird.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.sqlapp.data.db.dialect.resolver;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.DialectUtils;
import com.sqlapp.data.db.dialect.Firebird;
import com.sqlapp.data.db.dialect.Firebird20;
import com.sqlapp.data.db.dialect.Firebird25;
import com.sqlapp.data.db.dialect.Firebird30;

public class FirebirdDialectResolver extends ProductNameDialectResolver {

	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = 1L;

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
		 * @see
		 * com.sqlapp.data.db.dialect.resolver.VersionResolver#getDialect(int,
		 * int, java.lang.Integer)
		 */
		@Override
		public Dialect getDialect(int majorVersion, int minorVersion, Integer revision) {
			if (majorVersion == 2) {
				if (minorVersion < 5) {
					return DialectHolder.defaultDialect20;
				}
				return DialectHolder.defaultDialect25;
			} else if (majorVersion >= 3) {
				return DialectHolder.defaultDialect30;
			}
			return DialectHolder.defaultDialect;
		}

	}
}
