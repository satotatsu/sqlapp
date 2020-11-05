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

import java.sql.SQLException;
import com.sqlapp.data.geometry.Point;
import org.postgresql.geometric.PGpoint;

public class FromPGPointConverter extends AbstractFromObjectConverter<Point, PGpoint>{

	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = 6488632910509733050L;

	/* (non-Javadoc)
	 * @see com.sqlapp.data.converter.Converter#copy(java.lang.Object)
	 */
	public Point copy(Object obj){
		if (obj==null){
			return null;
		}
		return (Point)convertObject(obj).clone();
	}

	@Override
	protected boolean isTargetInstanceof(Object value) {
		return value instanceof Point;
	}

	@Override
	protected boolean isInstanceof(Object value) {
		return value instanceof PGpoint;
	}

	@Override
	protected Point toObjectFromString(String value) {
		String val=(String)value;
		try {
			PGpoint pgObject=new PGpoint(val);
			return toObject(pgObject);
		} catch(SQLException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	protected Point toObject(PGpoint value) {
		Point obj=new Point();
		obj.setValue(value.getValue());
		return obj;
	}

	@Override
	protected Point clone(Point value) {
		return value.clone();
	}

	@Override
	protected Class<PGpoint> getObjectClass() {
		return PGpoint.class;
	}
}
