/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-virtica.
 *
 * sqlapp-core-virtica is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-virtica is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-virtica.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.sqlapp.data.db.dialect.virtica.resolver;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.DialectUtils;
import com.sqlapp.data.db.dialect.resolver.ProductNameDialectResolver;
import com.sqlapp.data.db.dialect.resolver.VersionResolver;
import com.sqlapp.data.db.dialect.virtica.Virtica;
import com.sqlapp.data.db.dialect.virtica.Virtica72;
import com.sqlapp.data.db.dialect.virtica.Virtica80;

public class VirticaDialectResolver extends ProductNameDialectResolver {

	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = 1L;

	public VirticaDialectResolver() {
		super("*.virtica", new VirticaVersionResolver());
	}

	/**
	 * Version Resolver
	 * 
	 * @author satoh
	 * 
	 */
	static class VirticaVersionResolver implements VersionResolver {

		/**
		 * serialVersionUID
		 */
		private static final long serialVersionUID = 1L;

		static class DialectHolder {
			final static Dialect defaultDialect80 = DialectUtils
					.getInstance(Virtica80.class);
			final static Dialect defaultDialect72 = DialectUtils
					.getInstance(Virtica72.class, ()->defaultDialect80);
			final static Dialect defaultDialect = DialectUtils.getInstance(
					Virtica.class, ()->defaultDialect72);
		}

		/**
		 * コンストラクタ
		 */
		public VirticaVersionResolver() {
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
			if (majorVersion>=8) {
				return DialectHolder.defaultDialect80;
			} else if (majorVersion>=7) {
				if (minorVersion>1){
					return DialectHolder.defaultDialect72;
				} else{
					return DialectHolder.defaultDialect;
				}
			}
			return DialectHolder.defaultDialect;
		}

	}

}
