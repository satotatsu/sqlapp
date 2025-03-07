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

package com.sqlapp.data.schemas;

import java.util.List;

import com.sqlapp.util.Diff;
/**
 * DbObjectのDIFFを行います。
 *
 * @param <T>
 */
public class DbObjectDiff<T extends DbObject<?>> extends Diff<T> {

	private EqualsHandler equalsHandler;
	
	public DbObjectDiff(List<T> a, List<T> b, EqualsHandler equalsHandler) {
		super(a, b);
		this.equalsHandler=equalsHandler;
	}

	@Override
	protected boolean eq(T a, T b) {
		boolean bool;
		if (equalsHandler==null){
			bool= ((DbObject<?>) a).like(b);
		} else{
			bool= ((DbObject<?>) a).like(b,equalsHandler);
		}
		return bool;
	}
}
