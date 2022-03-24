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

import com.sqlapp.data.converter.IntervalConverter;
import com.sqlapp.data.interval.Interval;
import org.postgresql.util.PGInterval;
import java.sql.SQLException;

public class ToPGIntervalConverter extends AbstractToObjectConverter<PGInterval, Interval>{

	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = 6488632910509733050L;

	public ToPGIntervalConverter() {
		super(new IntervalConverter());
	}

	@Override
	protected boolean isTargetInstanceof(Object value) {
		return value instanceof PGInterval;
	}

	@Override
	protected PGInterval newInstance() {
		return new PGInterval();
	}

	@Override
	protected PGInterval toDbType(Interval obj) {
		int scale=1;
		if (!obj.isPositive()){
			scale=-1;
		}
		return new PGInterval(obj.getYears()*scale
				, obj.getMonths()*scale
				, obj.getDays()*scale
				, obj.getHours()*scale
				, obj.getMinutes()*scale
				, obj.getSecondsAsDouble()*scale
				);
	}

	@Override
	protected void setValue(PGInterval obj, String value) throws SQLException {
		obj.setValue(value);
	}

	/* (non-Javadoc)
	 * @see com.sqlapp.data.converter.Converter#copy(java.lang.Object)
	 */
	public PGInterval copy(Object obj){
		if (obj==null){
			return null;
		}
		return convertObject(obj);
	}


}
