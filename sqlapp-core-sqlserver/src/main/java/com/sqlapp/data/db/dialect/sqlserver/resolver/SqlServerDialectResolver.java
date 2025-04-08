/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
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
 * along with sqlapp-core-sqlserver.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.sqlserver.resolver;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.resolver.ProductNameDialectResolver;
import com.sqlapp.data.db.dialect.resolver.VersionResolver;
import com.sqlapp.data.db.dialect.sqlserver.DialectHolder;

/**
 * Dialect resolver for SQL Server
 * 
 * @author satoh
 * 
 */
public class SqlServerDialectResolver extends ProductNameDialectResolver {

	public SqlServerDialectResolver() {
		super("(Microsoft *SQL *Server.*|MS *SQL)", new SqlServerVersionResolver());
	}

	public static final SqlServerDialectResolver instance = new SqlServerDialectResolver();

	public static SqlServerDialectResolver getInstance() {
		return instance;
	}

	static class SqlServerVersionResolver implements VersionResolver {

		/**
		 * serialVersionUID
		 */
		private static final long serialVersionUID = 1L;

		/**
		 * コンストラクタ
		 */
		public SqlServerVersionResolver() {
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see com.sqlapp.data.db.dialect.resolver.VersionResolver#getDialect(int, int,
		 * java.lang.Integer)
		 */
		@Override
		public Dialect getDialect(final int majorVersion, final int minorVersion, final Integer revision) {
			if (majorVersion >= 15) {
				return DialectHolder.defaultDialect2019;
			} else if (majorVersion >= 14) {
				return DialectHolder.defaultDialect2017;
			} else if (majorVersion >= 13) {
				if (minorVersion > 1) {
					return DialectHolder.defaultDialect2016Sp1;
				}
				return DialectHolder.defaultDialect2016;
			} else if (majorVersion >= 12) {
				return DialectHolder.defaultDialect2014;
			} else if (majorVersion >= 11) {
				return DialectHolder.defaultDialect2012;
			} else if (majorVersion >= 10) {
				if (minorVersion >= 50) {
					return DialectHolder.defaultDialect2008R2;
				}
				return DialectHolder.defaultDialect2008;
			} else if (majorVersion >= 9) {
				return DialectHolder.defaultDialect2005;
			} else if (majorVersion >= 8) {
				return DialectHolder.defaultDialect2000;
			}
			return DialectHolder.defaultDialect2000;
		}
	}

}
