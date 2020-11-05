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

import static com.sqlapp.util.CommonUtils.notZero;

import org.hsqldb.types.IntervalSecondData;

import com.sqlapp.data.interval.IntervalSecond;

public class FromHsqlIntervalSecondConverter extends AbstractFromObjectConverter<IntervalSecond, IntervalSecondData>{

	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = 6488632910509733050L;

	@Override
	protected boolean isTargetInstanceof(Object value) {
		return value instanceof IntervalSecond;
	}

	@Override
	protected boolean isInstanceof(Object value) {
		return value instanceof IntervalSecondData;
	}

	@Override
	protected Class<IntervalSecondData> getObjectClass() {
		return IntervalSecondData.class;
	}

	@Override
	protected IntervalSecond toObjectFromString(String value) {
		IntervalSecond obj=IntervalSecond.parse(value);
		return obj;
	}

	@Override
	protected IntervalSecond toObject(IntervalSecondData value) {
		long seconds=value.getSeconds();
		int nanos=value.getNanos();
		IntervalSecond obj=new IntervalSecond();
		obj.setSeconds((int)seconds);
		obj.setNanos(nanos);
		long notZeroInt=notZero((int)seconds, nanos);
		if (notZeroInt<0){
			obj.setNegative();
		} else if (seconds<0){
			obj.setNegative();
		}
		return obj;
	}

	@Override
	protected IntervalSecond clone(IntervalSecond value) {
		return value.clone();
	}
}
