/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-hsql.
 *
 * sqlapp-core-hsql is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-hsql is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-hsql.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.sqlapp.data.db.converter.hsql;

import org.hsqldb.types.IntervalMonthData;

import com.sqlapp.data.interval.IntervalMonth;

public class FromHsqlIntervalMonthConverter extends AbstractFromObjectConverter<IntervalMonth, IntervalMonthData>{

	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = 6488632910509733050L;

	@Override
	protected boolean isTargetInstanceof(Object value) {
		return value instanceof IntervalMonth;
	}

	@Override
	protected boolean isInstanceof(Object value) {
		return value instanceof IntervalMonthData;
	}

	@Override
	protected Class<IntervalMonthData> getObjectClass() {
		return IntervalMonthData.class;
	}

	@Override
	protected IntervalMonth toObjectFromString(String value) {
		IntervalMonth obj=IntervalMonth.parse(value);
		return obj;
	}

	@Override
	protected IntervalMonth toObject(IntervalMonthData value) {
		IntervalMonth obj=new IntervalMonth();
		obj.setMonths((int)value.getMonths());
		if (value.getMonths()<0){
			obj.setNegative();
		}
		return obj;
	}

	@Override
	protected IntervalMonth clone(IntervalMonth value) {
		return value.clone();
	}
}
