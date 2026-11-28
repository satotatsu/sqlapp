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

import com.sqlapp.data.converter.AbstractConverter;
import com.sqlapp.data.db.dialect.postgres.PostgresIntervalCodec;
import com.sqlapp.data.interval.IntervalValue;

/** Reads exact PostgreSQL values without collapsing days into time. */
public class FromPGIntervalConverter extends AbstractConverter<IntervalValue> {
	private static final long serialVersionUID = 6488632910509733050L;

	@Override
	public IntervalValue convertObject(final Object value) {
		if (isSupplier(value))
			return convertObject(getSupplierValue(value));
		return value == null ? getDefaultValue() : PostgresIntervalCodec.decode(value);
	}

	@Override
	public IntervalValue copy(final Object value) {
		return convertObject(value);
	}

	@Override
	public String format(final IntervalValue value) {
		return value == null ? null : PostgresIntervalCodec.encode(value);
	}
}
