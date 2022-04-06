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


import com.sqlapp.data.converter.Converter;
import com.sqlapp.data.db.dialect.postgres.converter.FromPGIntervalConverter;
import com.sqlapp.data.db.dialect.postgres.converter.ToPGIntervalConverter;
import com.sqlapp.data.interval.Interval;

public class PGIntervalConveterTest extends AbstractConveterTest<Interval>{

	@Override
	protected Interval newInstance() {
		return new Interval(2011,02,25,0,0,0);
	}

	@Override
	protected Converter<?> createFromConverter() {
		return new FromPGIntervalConverter();
	}

	@Override
	protected Converter<?> createToConverter() {
		return new ToPGIntervalConverter();
	}

}
