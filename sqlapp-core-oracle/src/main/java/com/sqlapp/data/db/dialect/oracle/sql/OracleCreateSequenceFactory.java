/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-oracle.
 *
 * sqlapp-core-oracle is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-oracle is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-oracle.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.oracle.sql;

import com.sqlapp.data.db.dialect.oracle.util.OracleSqlBuilder;
import com.sqlapp.data.db.sql.AbstractCreateSequenceFactory;
import com.sqlapp.data.schemas.Sequence;

public class OracleCreateSequenceFactory extends AbstractCreateSequenceFactory<OracleSqlBuilder> {

	@Override
	protected void addDataType(final Sequence obj, OracleSqlBuilder builder) {
	}

	@Override
	protected void addCache(final Sequence sequence, OracleSqlBuilder builder) {
		if (sequence.getCacheSize() == 0)
			builder.space()._add("NOCACHE");
		else
			super.addCache(sequence, builder);
	}

	@Override
	protected void addOptions(final Sequence sequence, OracleSqlBuilder builder) {
		for (String key : java.util.List.of("SCALE", "EXTEND", "SESSION")) {
			String value = sequence.getSpecifics().get(key);
			if (value != null && !"true".equalsIgnoreCase(value) && !"false".equalsIgnoreCase(value))
				throw new IllegalArgumentException("Invalid Oracle sequence flag " + key + ": " + value);
			if (Boolean.parseBoolean(value) && !(getDialect() instanceof com.sqlapp.data.db.dialect.oracle.Oracle18c))
				throw new IllegalArgumentException("Sequence " + key + " requires Oracle 18c or later");
		}
		if (Boolean.parseBoolean(sequence.getSpecifics().get("EXTEND")) && !Boolean.parseBoolean(sequence.getSpecifics().get("SCALE")))
			throw new IllegalArgumentException("Sequence EXTEND requires SCALE");
		if (!sequence.isCache() && sequence.getCacheSize() == null)
			builder.space()._add("NOCACHE");
		if (sequence.isOrder())
			builder.space()._add("ORDER");
	}
}
