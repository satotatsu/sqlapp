/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
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
 * along with sqlapp-core-postgres.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.sqlapp.data.db.dialect.resolver;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.DialectUtils;
import com.sqlapp.data.db.dialect.Postgres;
import com.sqlapp.data.db.dialect.Postgres100;
import com.sqlapp.data.db.dialect.Postgres110;
import com.sqlapp.data.db.dialect.Postgres120;
import com.sqlapp.data.db.dialect.Postgres83;
import com.sqlapp.data.db.dialect.Postgres84;
import com.sqlapp.data.db.dialect.Postgres90;
import com.sqlapp.data.db.dialect.Postgres91;
import com.sqlapp.data.db.dialect.Postgres92;
import com.sqlapp.data.db.dialect.Postgres93;
import com.sqlapp.data.db.dialect.Postgres94;
import com.sqlapp.data.db.dialect.Postgres95;
import com.sqlapp.data.db.dialect.Postgres96;

/**
 * Dialect resolver for Oracle
 * 
 * @author satoh
 * 
 */
public class PostgresDialectResolver extends ProductNameDialectResolver {

	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = 1L;

	public PostgresDialectResolver() {
		super("Postgres.*", new PostgresVersionResolver());
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

		public static class DialectHolder {
			final static Dialect postgreSQL120 = DialectUtils
					.getInstance(Postgres120.class);
			final static Dialect postgreSQL110 = DialectUtils
					.getInstance(Postgres110.class,  ()->postgreSQL120);
			final static Dialect postgreSQL100 = DialectUtils
					.getInstance(Postgres100.class,  ()->postgreSQL110);
			final static Dialect postgreSQL96 = DialectUtils.getInstance(
					Postgres96.class, ()->postgreSQL100);
			final static Dialect postgreSQL95 = DialectUtils.getInstance(
					Postgres95.class, ()->postgreSQL96);
			final static Dialect postgreSQL94 = DialectUtils.getInstance(
					Postgres94.class, ()->postgreSQL95);
			final static Dialect postgreSQL93 = DialectUtils.getInstance(
					Postgres93.class, ()->postgreSQL94);
			final static Dialect postgreSQL92 = DialectUtils.getInstance(
					Postgres92.class, ()->postgreSQL93);
			final static Dialect postgreSQL91 = DialectUtils.getInstance(
					Postgres91.class, ()->postgreSQL92);
			final static Dialect postgreSQL90 = DialectUtils.getInstance(
					Postgres90.class, ()->postgreSQL91);
			final static Dialect postgreSQL84 = DialectUtils.getInstance(
					Postgres84.class, ()->postgreSQL90);
			final static Dialect postgreSQL83 = DialectUtils.getInstance(
					Postgres83.class, ()->postgreSQL84);
			final static Dialect postgreSQL82 = DialectUtils.getInstance(
					Postgres83.class, ()->postgreSQL83);
			final static Dialect defaultDialect = DialectUtils.getInstance(
					Postgres.class, ()->postgreSQL82);
		}

		/**
		 * コンストラクタ
		 */
		public PostgresVersionResolver() {
		}

		@Override
		public Dialect getDialect(final int majorVersion, final int minorVersion,
				final Integer revision) {
			if (majorVersion>=12) {
				return DialectHolder.postgreSQL120;
			}else if (majorVersion>=11) {
				return DialectHolder.postgreSQL110;
			}else if (majorVersion>=10) {
				return DialectHolder.postgreSQL100;
			}else if (majorVersion>=9) {
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
				
			}else if (majorVersion>=8) {
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
