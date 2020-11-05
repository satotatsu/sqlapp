/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-hsql.
 *
 * sqlapp-core-hsql is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-hsql is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-hsql.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.sqlapp.data.db.converter.hsql;

import com.sqlapp.data.converter.Converter;
import com.sqlapp.data.interval.IntervalSecond;

public class HsqlIntervalSecondConveterTest extends AbstractConveterTest<IntervalSecond>{

	@Override
	protected IntervalSecond newInstance() {
		return new IntervalSecond(125,123);
	}

	@Override
	protected Converter<?> createFromConverter() {
		return new FromHsqlIntervalSecondConverter();
	}

	@Override
	protected Converter<?> createToConverter() {
		return new ToHsqlIntervalSecondConverter();
	}
}
