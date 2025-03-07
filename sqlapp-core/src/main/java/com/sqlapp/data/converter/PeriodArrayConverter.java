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

import java.time.Period;

/**
 * Period配列のコンバーター
 * @author satoh
 *
 */
public class PeriodArrayConverter extends AbstractArrayConverter<Period[],Period>{

	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = 7015695661930806993L;

	protected PeriodArrayConverter(final Converter<Period> unitConverter) {
		super(unitConverter);
	}

	@Override
	protected Period[] newArrayInstance(final int size) {
		return new Period[size];
	}

	@Override
	protected void setArray(final Period[] array, final int i, final Period value) {
		array[i]=value;
	}

}
