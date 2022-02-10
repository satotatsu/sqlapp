/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
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
 * along with sqlapp-core.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.sqlapp.data.converter;

import com.sqlapp.data.geometry.Point;

/**
 * Point配列のコンバーター
 * @author satoh
 *
 */
public class PointArrayConverter extends AbstractArrayConverter<Point[],Point>{

	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = 7015695661930806993L;

	protected PointArrayConverter(Converter<Point> unitConverter) {
		super(unitConverter);
	}

	@Override
	protected Point[] newArrayInstance(int size) {
		return new Point[size];
	}

	@Override
	protected void setArray(Point[] array, int i, Point value) {
		array[i]=value;
	}

}
