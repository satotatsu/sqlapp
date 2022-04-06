/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-mariadb.
 *
 * sqlapp-core-mariadb is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-mariadb is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-mariadb.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.sqlapp.data.db.dialect.mariadb.resolver;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.DialectUtils;
import com.sqlapp.data.db.dialect.mariadb.Mariadb;
import com.sqlapp.data.db.dialect.mariadb.Mariadb10_00;
import com.sqlapp.data.db.dialect.mariadb.Mariadb10_25;
import com.sqlapp.data.db.dialect.mariadb.Mariadb10_27;
import com.sqlapp.data.db.dialect.resolver.ProductNameDialectResolver;
import com.sqlapp.data.db.dialect.resolver.VersionResolver;

/**
 * Dialect resolver for MySql
 * 
 * @author satoh
 * 
 */
public class MariadbDialectResolver extends ProductNameDialectResolver {

	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = 1L;

	public MariadbDialectResolver() {
		super("MariaDB", new MariadbVersionResolver());
	}

	/**
	 * MySql用のバージョンResolver
	 * 
	 * @author satoh
	 * 
	 */
	static class MariadbVersionResolver implements VersionResolver {

		/**
		 * serialVersionUID
		 */
		private static final long serialVersionUID = 1L;

		static class DialectHolder {
			final static Dialect mariadb10_27Dialect = DialectUtils
					.getInstance(Mariadb10_27.class);
			final static Dialect mariadb10_25Dialect = DialectUtils
					.getInstance(Mariadb10_25.class, ()->mariadb10_27Dialect);
			final static Dialect mariadb10_00Dialect = DialectUtils.getInstance(
					Mariadb10_00.class, ()->mariadb10_25Dialect);
			final static Dialect defaultDialect = DialectUtils.getInstance(
					Mariadb.class, ()->mariadb10_00Dialect);
		}

		/**
		 * コンストラクタ
		 */
		public MariadbVersionResolver() {
		}

		@Override
		public Dialect getDialect(final int majorVersion, final int minorVersion,
				final Integer revision) {
			if (majorVersion==10){
				if (minorVersion>=2) {
					if (revision>=7) {
						return DialectHolder.mariadb10_27Dialect;
					} else {
						return DialectHolder.mariadb10_25Dialect;
					}
				}else {
					return DialectHolder.mariadb10_00Dialect;
				}
			} else if (majorVersion<10){
				return DialectHolder.defaultDialect;
			}
			return DialectHolder.mariadb10_27Dialect;
		}

	}

}
