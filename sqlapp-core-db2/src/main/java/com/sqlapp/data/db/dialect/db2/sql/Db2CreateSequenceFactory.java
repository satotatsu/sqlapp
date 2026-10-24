/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-db2.
 *
 * sqlapp-core-db2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-db2 is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-db2.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.db2.sql;

import com.sqlapp.data.db.dialect.db2.util.Db2SqlBuilder;
import com.sqlapp.data.db.sql.AbstractCreateSequenceFactory;
import com.sqlapp.data.schemas.Sequence;

public class Db2CreateSequenceFactory extends AbstractCreateSequenceFactory<Db2SqlBuilder> {

	@Override
	protected void addCreateSequence(final Sequence obj, Db2SqlBuilder builder) {
		builder.create().or().replace().sequence();
	}

	@Override
	protected void addDataType(final Sequence obj, Db2SqlBuilder builder) {
		String name = obj.getDataTypeName();
		if (obj.getDataType() == com.sqlapp.data.db.datatype.DataType.DECIMAL
				|| name != null && (name.equalsIgnoreCase("DECIMAL") || name.equalsIgnoreCase("NUMERIC"))) {
			if (obj.getScale() != null && obj.getScale() != 0) {
				throw new IllegalArgumentException("Db2 sequence scale must be zero");
			}
			builder.as().space()._add(name == null ? "DECIMAL" : name)._add("(")
					._add(obj.getPrecision() == null ? 31 : obj.getPrecision())._add(",0)");
		} else {
			super.addDataType(obj, builder);
		}
	}

	@Override
	protected void addCache(final Sequence obj, Db2SqlBuilder builder) {
		if (obj.getCacheSize() < 0)
			throw new IllegalArgumentException("Db2 sequence cache size must not be negative");
		if (!obj.isCache() || obj.getCacheSize() <= 1) {
			builder.no().cache();
		} else {
			super.addCache(obj, builder);
		}
	}

	@Override
	protected void addOptions(final Sequence obj, Db2SqlBuilder builder) {
		if (!obj.isCache() && obj.getCacheSize() == null) {
			builder.no().cache();
		}
		if (obj.isOrder()) {
			builder.order();
		}
	}

}
