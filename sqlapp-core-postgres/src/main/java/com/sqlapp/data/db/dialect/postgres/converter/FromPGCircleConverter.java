/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
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
 * along with sqlapp-core-postgres.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.postgres.converter;

import java.sql.SQLException;
import com.sqlapp.data.geometry.Circle;
import org.postgresql.geometric.PGcircle;

public class FromPGCircleConverter extends AbstractFromObjectConverter<Circle, PGcircle>{

	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = 6488632910509733050L;

	/* (non-Javadoc)
	 * @see com.sqlapp.data.converter.Converter#copy(java.lang.Object)
	 */
	public Circle copy(Object obj){
		if (obj==null){
			return null;
		}
		return (Circle)convertObject(obj).clone();
	}

	@Override
	protected boolean isTargetInstanceof(Object value) {
		return value instanceof Circle;
	}

	@Override
	protected boolean isInstanceof(Object value) {
		return value instanceof PGcircle;
	}

	@Override
	protected Circle toObjectFromString(String value) {
		String val=(String)value;
		try {
			PGcircle pgObject=new PGcircle(val);
			return toObject(pgObject);
		} catch(SQLException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	protected Circle toObject(PGcircle value) {
		Circle obj=new Circle();
		obj.setValue(value.getValue());
		return obj;
	}

	@Override
	protected Circle clone(Circle value) {
		return value.clone();
	}

	@Override
	protected Class<PGcircle> getObjectClass() {
		return PGcircle.class;
	}
}
