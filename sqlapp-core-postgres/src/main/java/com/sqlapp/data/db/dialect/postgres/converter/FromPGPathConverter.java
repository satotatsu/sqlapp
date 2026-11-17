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

import com.sqlapp.data.geometry.Path;
import com.sqlapp.data.geometry.Point;
import org.postgresql.geometric.PGpath;

public class FromPGPathConverter extends AbstractFromObjectConverter<Path, PGpath> {

	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = 6488632910509733050L;

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.converter.Converter#copy(java.lang.Object)
	 */
	public Path copy(Object obj) {
		if (obj == null) {
			return null;
		}
		return (Path) convertObject(obj).clone();
	}

	@Override
	protected boolean isTargetInstanceof(Object value) {
		return value instanceof Path;
	}

	@Override
	protected boolean isInstanceof(Object value) {
		return value instanceof PGpath;
	}

	@Override
	protected Path toObjectFromString(String value) {
		// PostgreSQL の出力形式は core 側で解析する(pgjdbc の PGline(String) は2点形式の係数計算が誤っているため)
		return new Path().setValue(value);
	}

	@Override
	protected Path toObject(PGpath value) {
		if (value.points == null) {
			return null;
		}
		final Point[] points = new Point[value.points.length];
		for (int i = 0; i < points.length; i++) {
			points[i] = new Point(value.points[i].x, value.points[i].y);
		}
		return new Path(value.isOpen(), points);
	}

	@Override
	protected Path clone(Path value) {
		return value.clone();
	}

	@Override
	protected Class<PGpath> getObjectClass() {
		return PGpath.class;
	}
}
