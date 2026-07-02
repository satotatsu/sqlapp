/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-mysql.
 *
 * sqlapp-core-mysql is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-mysql is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-mysql.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.mysql.resolver;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.mysql.DialectHolder;
import com.sqlapp.data.db.dialect.resolver.ProductNameDialectResolver;
import com.sqlapp.data.db.dialect.resolver.VersionResolver;

/**
 * Dialect resolver for MySql
 * 
 * @author satoh
 * 
 */
public class MySqlDialectResolver extends ProductNameDialectResolver {

	public MySqlDialectResolver() {
		super("MySql", new MySqlVersionResolver());
	}

	/**
	 * MySql用のバージョンResolver
	 * 
	 * @author satoh
	 * 
	 */
	static class MySqlVersionResolver implements VersionResolver {

		/**
		 * serialVersionUID
		 */
		private static final long serialVersionUID = 1L;

		/**
		 * コンストラクタ
		 */
		public MySqlVersionResolver() {
		}

		@Override
		public Dialect getDialect(final int majorVersion, final int minorVersion, final Integer revision) {
			if (minorVersion >= 8) {
				if (minorVersion >= 4) {
					return DialectHolder.mysql840Dialect;
				} else {
					if (revision >= 1) {
						return DialectHolder.mysql801Dialect;
					}
					return DialectHolder.mysql800Dialect;
				}
			}
			if (majorVersion == 5) {
			} else if (minorVersion >= 7) {
				return DialectHolder.mysql570Dialect;
			} else if (minorVersion >= 6) {
				if (revision != null && revision.intValue() >= 5) {
					return DialectHolder.mysql565Dialect;
				}
				if (revision != null && revision.intValue() >= 4) {
					return DialectHolder.mysql564Dialect;
				}
			}
			return DialectHolder.defaultDialect;
		}
	}
}
