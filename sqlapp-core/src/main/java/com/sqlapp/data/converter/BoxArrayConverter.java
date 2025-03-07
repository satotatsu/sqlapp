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

package com.sqlapp.data.converter;

import com.sqlapp.data.geometry.Box;

/**
 * Lseg配列のコンバーター
 * @author satoh
 *
 */
public class BoxArrayConverter extends AbstractArrayConverter<Box[],Box>{

	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = 7015695661930806993L;

	protected BoxArrayConverter(Converter<Box> unitConverter) {
		super(unitConverter);
	}

	@Override
	protected Box[] newArrayInstance(int size) {
		return new Box[size];
	}

	@Override
	protected void setArray(Box[] array, int i, Box value) {
		array[i]=value;
	}

}
