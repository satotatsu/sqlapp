/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-postgres.
 *
 * sqlapp-core-postgres is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-postgres is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-postgres.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.sqlapp.data.db.converter.postgres;

import static com.sqlapp.util.CommonUtils.abs;
import static com.sqlapp.util.CommonUtils.notZero;

import com.sqlapp.data.interval.Interval;
import java.sql.SQLException;
import org.postgresql.util.PGInterval;

public class FromPGIntervalConverter extends AbstractFromObjectConverter<Interval, PGInterval>{

	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = 6488632910509733050L;

	/* (non-Javadoc)
	 * @see com.sqlapp.data.converter.Converter#copy(java.lang.Object)
	 */
	public Interval copy(Object obj){
		if (obj==null){
			return null;
		}
		return (Interval)convertObject(obj).clone();
	}

	@Override
	protected boolean isTargetInstanceof(Object value) {
		return value instanceof Interval;
	}

	@Override
	protected boolean isInstanceof(Object value) {
		return value instanceof PGInterval;
	}

	@Override
	protected Interval toObjectFromString(String value) {
		String val=(String)value;
		if (Interval.isParsable(val)){
			return Interval.parse(val);
		}
		try {
			PGInterval pgObject=new PGInterval(val);
			return toObject(pgObject);
		} catch(SQLException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	protected Interval toObject(PGInterval value) {
		int years=value.getYears();
		int months=value.getMonths();
		int days=value.getDays();
		int hours=value.getHours();
		int minutes=value.getMinutes();
		double seconds=value.getSeconds();
		Interval interval=new Interval(abs(years)
				, abs(months)
				, abs(days)
				, abs(hours)
				, abs(minutes)
				, abs(seconds));
		int notZeroInt=notZero(years, months, days, hours, minutes);
		if (notZeroInt<0){
			interval.scale(-1);
		} else if (seconds<0){
			interval.scale(-1);
		}
		return interval;
	}

	@Override
	protected Interval clone(Interval value) {
		return value.clone();
	}

	@Override
	protected Class<PGInterval> getObjectClass() {
		return PGInterval.class;
	}
}
