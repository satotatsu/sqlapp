/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
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
 * along with sqlapp-core-hsql.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.hsql.converter;

import com.sqlapp.data.converter.Converter;
import com.sqlapp.data.interval.IntervalMonth;

public class HsqlIntervalMonthConveterTest extends AbstractConveterTest<IntervalMonth> {

	@Override
	protected IntervalMonth newInstance() {
		return new IntervalMonth(13);
	}

	@Override
	protected Converter<?> createFromConverter() {
		return new FromHsqlIntervalMonthConverter();
	}

	@Override
	protected Converter<?> createToConverter() {
		return new ToHsqlIntervalMonthConverter();
	}
}
