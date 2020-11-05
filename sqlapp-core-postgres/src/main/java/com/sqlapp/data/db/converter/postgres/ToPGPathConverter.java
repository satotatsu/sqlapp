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

import com.sqlapp.data.converter.PathConverter;
import com.sqlapp.data.geometry.Path;
import org.postgresql.geometric.PGpath;
import java.sql.SQLException;

public class ToPGPathConverter extends AbstractToObjectConverter<PGpath, Path>{

	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = 6488632910509733050L;

	public ToPGPathConverter() {
		super(new PathConverter());
	}

	@Override
	protected boolean isTargetInstanceof(Object value) {
		return value instanceof PGpath;
	}

	@Override
	protected PGpath newInstance() {
		return new PGpath();
	}

	@Override
	protected PGpath toDbType(Path obj) {
		PGpath ret= newInstance();
		try {
			setValue(ret, obj.toString());
		} catch(SQLException e) {
			throw new RuntimeException(e);
		}
		return ret;
	}

	@Override
	protected void setValue(PGpath obj, String value) throws SQLException {
		obj.setValue(value);
	}

	/* (non-Javadoc)
	 * @see com.sqlapp.data.converter.Converter#copy(java.lang.Object)
	 */
	public PGpath copy(Object obj){
		if (obj==null){
			return null;
		}
		return convertObject(obj);
	}


}
