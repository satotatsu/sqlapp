/**
 * Copyright (C) 2026-2026 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core.
 *
 * sqlapp-core is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.jdbc.sql;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.schemas.Sequence;
import com.sqlapp.data.schemas.ValuesGenerator;

public class SequenceValueGenerator implements ValuesGenerator<Object> {

	private final SequenceGenerator sequenceGenerator;

	public SequenceValueGenerator(Connection connection, Sequence sequence) {
		final Dialect dialect = DialectResolver.getInstance().getDialect(connection);
		sequenceGenerator = new SequenceGenerator(connection, dialect, sequence);
	}

	public SequenceValueGenerator(Connection connection, Dialect dialect, Sequence sequence) {
		sequenceGenerator = new SequenceGenerator(connection, dialect, sequence);
	}

	@Override
	public List<Object> generateValues(int size) throws SQLException {
		return sequenceGenerator.get(size);
	}

	@Override
	public void close() {
		sequenceGenerator.close();
	}
}
