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
import com.sqlapp.data.geometry.Polygon;
import org.postgresql.geometric.PGpolygon;

public class FromPGPolygonConverter extends AbstractFromObjectConverter<Polygon, PGpolygon>{

	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = 6488632910509733050L;

	/* (non-Javadoc)
	 * @see com.sqlapp.data.converter.Converter#copy(java.lang.Object)
	 */
	public Polygon copy(Object obj){
		if (obj==null){
			return null;
		}
		return (Polygon)convertObject(obj).clone();
	}

	@Override
	protected boolean isTargetInstanceof(Object value) {
		return value instanceof Polygon;
	}

	@Override
	protected boolean isInstanceof(Object value) {
		return value instanceof PGpolygon;
	}

	@Override
	protected Polygon toObjectFromString(String value) {
		String val=(String)value;
		try {
			PGpolygon pgObject=new PGpolygon(val);
			return toObject(pgObject);
		} catch(SQLException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	protected Polygon toObject(PGpolygon value) {
		Polygon obj=new Polygon();
		obj.setValue(value.getValue());
		return obj;
	}

	@Override
	protected Polygon clone(Polygon value) {
		return value.clone();
	}

	@Override
	protected Class<PGpolygon> getObjectClass() {
		return PGpolygon.class;
	}
}
