/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-sqlserver.
 *
 * sqlapp-core-sqlserver is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-sqlserver is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-sqlserver.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.sqlapp.data.db.dialect.resolver;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.DialectUtils;
import com.sqlapp.data.db.dialect.SqlServer2000;
import com.sqlapp.data.db.dialect.SqlServer2005;
import com.sqlapp.data.db.dialect.SqlServer2008;
import com.sqlapp.data.db.dialect.SqlServer2012;
import com.sqlapp.data.db.dialect.SqlServer2014;
import com.sqlapp.data.db.dialect.SqlServer2016;
import com.sqlapp.data.db.dialect.SqlServer2017;
import com.sqlapp.data.db.dialect.SqlServer2019;

/**
 * Dialect resolver for SQL Server
 * 
 * @author satoh
 * 
 */
public class SqlServerDialectResolver extends ProductNameDialectResolver {

	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = 1L;

	public SqlServerDialectResolver() {
		super("(Microsoft *SQL *Server.*|MS *SQL)",
				new SqlServerVersionResolver());
	}

	public static final SqlServerDialectResolver instance=new SqlServerDialectResolver();
	
	public static SqlServerDialectResolver getInstance(){
		return instance;
	}
	
	static class SqlServerVersionResolver implements VersionResolver {

		/**
		 * serialVersionUID
		 */
		private static final long serialVersionUID = 1L;

		static class DialectHolder {
			final static Dialect defaultDialect2019 = DialectUtils
					.getInstance(SqlServer2019.class);
			final static Dialect defaultDialect2017 = DialectUtils
					.getInstance(SqlServer2017.class, ()->defaultDialect2019);
			final static Dialect defaultDialect2016 = DialectUtils
					.getInstance(SqlServer2016.class, ()->defaultDialect2017);
			final static Dialect defaultDialect2014 = DialectUtils
					.getInstance(SqlServer2014.class, ()->defaultDialect2016);
			final static Dialect defaultDialect2012 = DialectUtils.getInstance(
					SqlServer2012.class, ()->defaultDialect2014);
			final static Dialect defaultDialect2008 = DialectUtils.getInstance(
					SqlServer2008.class, ()->defaultDialect2012);
			final static Dialect defaultDialect2008R2 = defaultDialect2008;
			final static Dialect defaultDialect2005 = DialectUtils.getInstance(
					SqlServer2005.class, ()->defaultDialect2008);
			final static Dialect defaultDialect2000 = DialectUtils.getInstance(
					SqlServer2000.class, ()->defaultDialect2005);
		}

		/**
		 * コンストラクタ
		 */
		public SqlServerVersionResolver() {
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
			if (majorVersion>=15) {
				return DialectHolder.defaultDialect2019;
			} else if (majorVersion>=14) {
				return DialectHolder.defaultDialect2017;
			} else if (majorVersion>=13) {
				return DialectHolder.defaultDialect2016;
			} else if (majorVersion>=12) {
				return DialectHolder.defaultDialect2014;
			} else if (majorVersion>=11) {
				return DialectHolder.defaultDialect2012;
			} else if (majorVersion>=10) {
				if (minorVersion >= 50) {
					return DialectHolder.defaultDialect2008R2;
				}
				return DialectHolder.defaultDialect2008;
			} else if (majorVersion>=9) {
				return DialectHolder.defaultDialect2005;
			} else if (majorVersion>=8) {
				return DialectHolder.defaultDialect2000;
			}
			return DialectHolder.defaultDialect2000;
		}
	}

}
