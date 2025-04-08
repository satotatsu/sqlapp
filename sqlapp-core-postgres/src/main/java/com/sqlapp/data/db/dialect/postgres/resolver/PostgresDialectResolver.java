/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-postgres.
 *
 * sqlapp-core-postgres is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-postgres is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-postgres.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.postgres.resolver;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.postgres.DialectHolder;
import com.sqlapp.data.db.dialect.resolver.ProductNameDialectResolver;
import com.sqlapp.data.db.dialect.resolver.VersionResolver;

/**
 * Dialect resolver for Oracle
 * 
 * @author satoh
 * 
 */
public class PostgresDialectResolver extends ProductNameDialectResolver {

	public PostgresDialectResolver() {
		super("Postgres.*", new PostgresVersionResolver());
	}

	public static final PostgresDialectResolver instance = new PostgresDialectResolver();

	public static PostgresDialectResolver getInstance() {
		return instance;
	}

	/**
	 * PostgreSql用のバージョンResolver
	 * 
	 * @author satoh
	 * 
	 */
	public static class PostgresVersionResolver implements VersionResolver {

		/**
		 * serialVersionUID
		 */
		private static final long serialVersionUID = 1L;

		/**
		 * コンストラクタ
		 */
		public PostgresVersionResolver() {
		}

		@Override
		public Dialect getDialect(final int majorVersion, final int minorVersion, final Integer revision) {
			if (majorVersion >= 16) {
				return DialectHolder.postgreSQL160;
			} else if (majorVersion >= 15) {
				return DialectHolder.postgreSQL150;
			} else if (majorVersion >= 14) {
				return DialectHolder.postgreSQL140;
			} else if (majorVersion >= 13) {
				return DialectHolder.postgreSQL130;
			} else if (majorVersion >= 12) {
				return DialectHolder.postgreSQL120;
			} else if (majorVersion >= 11) {
				return DialectHolder.postgreSQL110;
			} else if (majorVersion >= 10) {
				return DialectHolder.postgreSQL100;
			} else if (majorVersion >= 9) {
				switch (minorVersion) {
				case 0:
					return DialectHolder.postgreSQL90;
				case 1:
					return DialectHolder.postgreSQL91;
				case 2:
					return DialectHolder.postgreSQL92;
				case 3:
					return DialectHolder.postgreSQL93;
				case 4:
					return DialectHolder.postgreSQL94;
				case 5:
					return DialectHolder.postgreSQL95;
				case 6:
					return DialectHolder.postgreSQL96;
				default:
					return DialectHolder.postgreSQL90;
				}

			} else if (majorVersion >= 8) {
				switch (minorVersion) {
				case 0:
					return DialectHolder.defaultDialect;
				case 1:
					return DialectHolder.defaultDialect;
				case 2:
					return DialectHolder.postgreSQL82;
				case 3:
					return DialectHolder.postgreSQL83;
				case 4:
					return DialectHolder.postgreSQL84;
				default:
					return DialectHolder.defaultDialect;
				}
			}
			return DialectHolder.defaultDialect;
		}
	}
}
