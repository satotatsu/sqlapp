/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-h2.
 *
 * sqlapp-core-h2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-h2 is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-h2.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.h2.sql;

import com.sqlapp.data.db.dialect.h2.util.H2SqlBuilder;
import com.sqlapp.data.db.sql.AbstractCreateSequenceFactory;
import com.sqlapp.data.schemas.Sequence;

public class H2CreateSequenceFactory extends
		AbstractCreateSequenceFactory<H2SqlBuilder> {

	@Override
	protected void addIfNotExists(final Sequence obj, H2SqlBuilder builder){
		builder.ifNotExists(this.getOptions().isCreateIfNotExists());
	}

	@Override
	protected void addMaxValue(final Sequence obj, H2SqlBuilder builder){
	}

	@Override
	protected void addMinValue(final Sequence obj, H2SqlBuilder builder){
	}

	@Override
	protected void addCycle(final Sequence obj, H2SqlBuilder builder){
	}
	
	@Override
	protected void addOrder(final Sequence obj, H2SqlBuilder builder){
	}
	
}
