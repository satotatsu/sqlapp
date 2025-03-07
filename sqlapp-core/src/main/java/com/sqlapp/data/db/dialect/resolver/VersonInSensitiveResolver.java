/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core.
 *
 * sqlapp-core is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.resolver;

import com.sqlapp.data.db.dialect.Dialect;

/**
 * バージョンに関係なく常に同じDbDialectを返すResolver
 * 
 * @author satoh
 * 
 */
public class VersonInSensitiveResolver implements VersionResolver {
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private Dialect dialect = null;

	/**
	 * コンストラクタ
	 * 
	 * @param dialect
	 */
	public VersonInSensitiveResolver(Dialect dialect) {
		this.dialect = dialect;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.sqlapp.data.db.dialect.resolver.VersionResolver#getDialect(int,int
	 * ,int)
	 */
	@Override
	public Dialect getDialect(int majorVersion, int minorVersion,
			Integer revision) {
		return dialect;
	}
}
