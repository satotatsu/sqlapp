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

package com.sqlapp.data.db.dialect.postgres.converter;

import java.sql.SQLException;
import com.sqlapp.data.geometry.Line;
import org.postgresql.geometric.PGline;

public class FromPGLineConverter extends AbstractFromObjectConverter<Line, PGline>{

	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = 6488632910509733050L;

	/* (non-Javadoc)
	 * @see com.sqlapp.data.converter.Converter#copy(java.lang.Object)
	 */
	public Line copy(Object obj){
		if (obj==null){
			return null;
		}
		return (Line)convertObject(obj).clone();
	}

	@Override
	protected boolean isTargetInstanceof(Object value) {
		return value instanceof Line;
	}

	@Override
	protected boolean isInstanceof(Object value) {
		return value instanceof PGline;
	}

	@Override
	protected Line toObjectFromString(String value) {
		String val=(String)value;
		try {
			PGline pgObject=new PGline(val);
			return toObject(pgObject);
		} catch(SQLException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	protected Line toObject(PGline value) {
		Line obj=new Line();
		obj.setValue(value.getValue());
		return obj;
	}

	@Override
	protected Line clone(Line value) {
		return value.clone();
	}

	@Override
	protected Class<PGline> getObjectClass() {
		return PGline.class;
	}
}
