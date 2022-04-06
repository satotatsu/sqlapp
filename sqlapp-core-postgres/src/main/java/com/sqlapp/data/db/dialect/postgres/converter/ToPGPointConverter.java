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

import com.sqlapp.data.converter.PointConverter;
import com.sqlapp.data.geometry.Point;
import org.postgresql.geometric.PGpoint;
import java.sql.SQLException;

public class ToPGPointConverter extends AbstractToObjectConverter<PGpoint, Point>{

	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = 6488632910509733050L;

	public ToPGPointConverter() {
		super(new PointConverter());
	}

	@Override
	protected boolean isTargetInstanceof(Object value) {
		return value instanceof PGpoint;
	}

	@Override
	protected PGpoint newInstance() {
		return new PGpoint();
	}

	@Override
	protected PGpoint toDbType(Point obj) {
		return new PGpoint(obj.getX()
				, obj.getY()
			);
	}

	@Override
	protected void setValue(PGpoint obj, String value) throws SQLException {
		obj.setValue(value);
	}

	/* (non-Javadoc)
	 * @see com.sqlapp.data.converter.Converter#copy(java.lang.Object)
	 */
	public PGpoint copy(Object obj){
		if (obj==null){
			return null;
		}
		return convertObject(obj);
	}


}
