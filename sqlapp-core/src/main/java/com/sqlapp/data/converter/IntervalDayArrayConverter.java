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

import com.sqlapp.data.interval.IntervalDay;

/**
 * IntervalDay配列のコンバーター
 * @author satoh
 *
 */
public class IntervalDayArrayConverter extends AbstractArrayConverter<IntervalDay[],IntervalDay>{

	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = -4019990269964884424L;

	protected IntervalDayArrayConverter(Converter<IntervalDay> unitConverter) {
		super(unitConverter);
	}

	@Override
	protected IntervalDay[] newArrayInstance(int size) {
		return new IntervalDay[size];
	}

	@Override
	protected void setArray(IntervalDay[] array, int i, IntervalDay value) {
		array[i]=value;
	}

}
