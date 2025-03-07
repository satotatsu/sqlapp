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

import com.sqlapp.data.converter.CircleConverter;
import com.sqlapp.data.geometry.Circle;
import org.postgresql.geometric.PGcircle;
import java.sql.SQLException;

public class ToPGCircleConverter extends AbstractToObjectConverter<PGcircle, Circle>{

	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = 6488632910509733050L;

	public ToPGCircleConverter() {
		super(new CircleConverter());
	}

	@Override
	protected boolean isTargetInstanceof(Object value) {
		return value instanceof PGcircle;
	}

	@Override
	protected PGcircle newInstance() {
		return new PGcircle();
	}

	@Override
	protected PGcircle toDbType(Circle obj) {
		return new PGcircle(obj.getX()
				, obj.getY(), obj.getR()
				);
	}

	@Override
	protected void setValue(PGcircle obj, String value) throws SQLException {
		obj.setValue(value);
	}

	/* (non-Javadoc)
	 * @see com.sqlapp.data.converter.Converter#copy(java.lang.Object)
	 */
	public PGcircle copy(Object obj){
		if (obj==null){
			return null;
		}
		return convertObject(obj);
	}


}
