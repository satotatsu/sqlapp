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
import com.sqlapp.data.db.dialect.DialectUtils;
import com.sqlapp.data.db.dialect.postgres.Postgres;
import com.sqlapp.data.db.dialect.postgres.Postgres100;
import com.sqlapp.data.db.dialect.postgres.Postgres110;
import com.sqlapp.data.db.dialect.postgres.Postgres120;
import com.sqlapp.data.db.dialect.postgres.Postgres130;
import com.sqlapp.data.db.dialect.postgres.Postgres140;
import com.sqlapp.data.db.dialect.postgres.Postgres150;
import com.sqlapp.data.db.dialect.postgres.Postgres160;
import com.sqlapp.data.db.dialect.postgres.Postgres83;
import com.sqlapp.data.db.dialect.postgres.Postgres84;
import com.sqlapp.data.db.dialect.postgres.Postgres90;
import com.sqlapp.data.db.dialect.postgres.Postgres91;
import com.sqlapp.data.db.dialect.postgres.Postgres92;
import com.sqlapp.data.db.dialect.postgres.Postgres93;
import com.sqlapp.data.db.dialect.postgres.Postgres94;
import com.sqlapp.data.db.dialect.postgres.Postgres95;
import com.sqlapp.data.db.dialect.postgres.Postgres96;
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

		public static class DialectHolder {
			final static Dialect postgreSQL160 = DialectUtils.getInstance(Postgres160.class);
			final static Dialect postgreSQL150 = DialectUtils.getInstance(Postgres150.class, () -> postgreSQL160);
			final static Dialect postgreSQL140 = DialectUtils.getInstance(Postgres140.class, () -> postgreSQL150);
			final static Dialect postgreSQL130 = DialectUtils.getInstance(Postgres130.class, () -> postgreSQL140);
			final static Dialect postgreSQL120 = DialectUtils.getInstance(Postgres120.class, () -> postgreSQL130);
			final static Dialect postgreSQL110 = DialectUtils.getInstance(Postgres110.class, () -> postgreSQL120);
			final static Dialect postgreSQL100 = DialectUtils.getInstance(Postgres100.class, () -> postgreSQL110);
			final static Dialect postgreSQL96 = DialectUtils.getInstance(Postgres96.class, () -> postgreSQL100);
			final static Dialect postgreSQL95 = DialectUtils.getInstance(Postgres95.class, () -> postgreSQL96);
			final static Dialect postgreSQL94 = DialectUtils.getInstance(Postgres94.class, () -> postgreSQL95);
			final static Dialect postgreSQL93 = DialectUtils.getInstance(Postgres93.class, () -> postgreSQL94);
			final static Dialect postgreSQL92 = DialectUtils.getInstance(Postgres92.class, () -> postgreSQL93);
			final static Dialect postgreSQL91 = DialectUtils.getInstance(Postgres91.class, () -> postgreSQL92);
			final static Dialect postgreSQL90 = DialectUtils.getInstance(Postgres90.class, () -> postgreSQL91);
			final static Dialect postgreSQL84 = DialectUtils.getInstance(Postgres84.class, () -> postgreSQL90);
			final static Dialect postgreSQL83 = DialectUtils.getInstance(Postgres83.class, () -> postgreSQL84);
			final static Dialect postgreSQL82 = DialectUtils.getInstance(Postgres83.class, () -> postgreSQL83);
			final static Dialect defaultDialect = DialectUtils.getInstance(Postgres.class, () -> postgreSQL82);
		}

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
