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
import com.sqlapp.data.geometry.Box;
import org.postgresql.geometric.PGbox;

public class FromPGBoxConverter extends AbstractFromObjectConverter<Box, PGbox>{

	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = 6488632910509733050L;

	/* (non-Javadoc)
	 * @see com.sqlapp.data.converter.Converter#copy(java.lang.Object)
	 */
	public Box copy(Object obj){
		if (obj==null){
			return null;
		}
		return (Box)convertObject(obj).clone();
	}

	@Override
	protected boolean isTargetInstanceof(Object value) {
		return value instanceof Box;
	}

	@Override
	protected boolean isInstanceof(Object value) {
		return value instanceof PGbox;
	}

	@Override
	protected Box toObjectFromString(String value) {
		String val=(String)value;
		try {
			PGbox pgObject=new PGbox(val);
			return toObject(pgObject);
		} catch(SQLException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	protected Box toObject(PGbox value) {
		Box obj=new Box();
		obj.setValue(value.getValue());
		return obj;
	}

	@Override
	protected Box clone(Box value) {
		return value.clone();
	}

	@Override
	protected Class<PGbox> getObjectClass() {
		return PGbox.class;
	}
}
