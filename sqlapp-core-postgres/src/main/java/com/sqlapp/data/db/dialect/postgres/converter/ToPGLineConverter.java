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

import com.sqlapp.data.converter.LineConverter;
import com.sqlapp.data.geometry.Line;
import org.postgresql.geometric.PGline;
import java.sql.SQLException;

public class ToPGLineConverter extends AbstractToObjectConverter<PGline, Line> {

	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = 6488632910509733050L;

	public ToPGLineConverter() {
		super(new LineConverter());
	}

	@Override
	protected boolean isTargetInstanceof(Object value) {
		return value instanceof PGline;
	}

	@Override
	protected PGline newInstance() {
		return new PGline();
	}

	@Override
	protected PGline toDbType(Line obj) {
		return new PGline(obj.getPoints()[0].getX(), obj.getPoints()[0].getY(), obj.getPoints()[1].getX(),
				obj.getPoints()[1].getY());
	}

	@Override
	protected void setValue(PGline obj, String value) throws SQLException {
		// pgjdbc の PGline#setValue は2点形式の係数計算が誤っているため、core 側で解析してから係数を求める
		final PGline line = toDbType(new Line().setValue(value));
		obj.a = line.a;
		obj.b = line.b;
		obj.c = line.c;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.converter.Converter#copy(java.lang.Object)
	 */
	public PGline copy(Object obj) {
		if (obj == null) {
			return null;
		}
		return convertObject(obj);
	}

}
