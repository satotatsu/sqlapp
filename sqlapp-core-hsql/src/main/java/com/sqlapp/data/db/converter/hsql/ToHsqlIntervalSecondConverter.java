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

import org.hsqldb.types.IntervalSecondData;
import org.hsqldb.types.IntervalType;

import com.sqlapp.data.converter.IntervalSecondConverter;
import com.sqlapp.data.interval.IntervalSecond;

public class ToHsqlIntervalSecondConverter extends AbstractToObjectConverter<IntervalSecondData,IntervalSecond>{

	public ToHsqlIntervalSecondConverter() {
		super(new IntervalSecondConverter());
	}

	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = 6488632910509733050L;

	@Override
	protected boolean isTargetInstanceof(Object value) {
		return value instanceof IntervalSecondData;
	}

	@Override
	protected IntervalSecondData newInstance() {
		return IntervalSecondData.newIntervalSeconds(0, IntervalType.SQL_INTERVAL_SECOND);
	}

	@Override
	protected IntervalSecondData toDbType(IntervalSecond obj) {
		int scale=1;
		if (!obj.isPositive()){
			scale=-1;
		}
		return new IntervalSecondData(obj.getSecondsFull()*scale
				, obj.getNanos()*scale
				);
	}
}
