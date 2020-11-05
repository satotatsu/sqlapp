/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-firebird.
 *
 * sqlapp-core-firebird is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-firebird is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-firebird.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.sqlapp.data.db.dialect.firebird.metadata;

import java.sql.SQLException;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.schemas.Sequence;
import com.sqlapp.jdbc.ExResultSet;

public class Firebird30SequenceReader extends Firebird20SequenceReader {

	protected Firebird30SequenceReader(Dialect dialect) {
		super(dialect);
	}

	protected Sequence createSequence(ExResultSet rs) throws SQLException {
		Sequence sequence = super.createSequence(rs);
		sequence.setStartValue(rs.getLong("RDB$INITIAL_VALUE"));
		sequence.setIncrementBy(rs.getLong("RDB$GENERATOR_INCREMENT"));
		return sequence;
	}

}
