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
import java.util.Objects;

import org.postgresql.util.PGobject;

import com.sqlapp.data.converter.AbstractConverter;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.postgres.PostgresIntervalCodec;
import com.sqlapp.data.interval.IntervalValue;

/** Uses exact labeled text instead of PGInterval's int-hour/double setters. */
public class ToPGIntervalConverter extends AbstractConverter<PGobject> {
	private static final long serialVersionUID = 6488632910509733050L;
	private final DataType type;
	private final int precision;

	public ToPGIntervalConverter() {
		this(DataType.INTERVAL, 6);
	}

	public ToPGIntervalConverter(final DataType type, final int precision) {
		this.type = Objects.requireNonNull(type, "type");
		PostgresIntervalCodec.validate(IntervalValue.ZERO, type, precision);
		this.precision = precision;
	}

	@Override
	public PGobject convertObject(final Object value) {
		if (isSupplier(value))
			return convertObject(getSupplierValue(value));
		final IntervalValue interval = PostgresIntervalCodec.decode(value);
		if (interval == null)
			return getDefaultValue();
		PostgresIntervalCodec.validate(interval, type, precision);
		final PGobject result = new PGobject();
		result.setType("interval");
		try {
			result.setValue(PostgresIntervalCodec.encode(interval));
		} catch (SQLException exception) {
			throw new IllegalArgumentException("Cannot encode interval", exception);
		}
		return result;
	}

	@Override
	public PGobject copy(final Object value) {
		return convertObject(value);
	}
}
