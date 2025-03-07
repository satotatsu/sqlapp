/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-derby.
 *
 * sqlapp-core-derby is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-derby is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-derby.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.derby.metadata;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.jdbc.metadata.JdbcTableReader;
import com.sqlapp.data.db.metadata.CheckConstraintReader;
import com.sqlapp.data.db.metadata.ColumnReader;
import com.sqlapp.data.db.metadata.ForeignKeyConstraintReader;
import com.sqlapp.data.db.metadata.IndexReader;
import com.sqlapp.data.db.metadata.UniqueConstraintReader;

public class DerbyTableReader extends JdbcTableReader{

	public DerbyTableReader(Dialect dialect) {
		super(dialect);
	}

	
	@Override
	protected ColumnReader newColumnReader() {
		return new DerbyJdbcColumnReader(this.getDialect());
	}
	
	@Override
	protected UniqueConstraintReader newUniqueConstraintReader() {
		return new DerbyUniqueConstraintReader(this.getDialect());
	}
	
	@Override
	protected CheckConstraintReader newCheckConstraintReader() {
		return new DerbyCheckConstraintReader(this.getDialect());
	}
	
	@Override
	protected ForeignKeyConstraintReader newForeignKeyConstraintReader() {
		return new DerbyForeignKeyConstraintReader(this.getDialect());
	}
	
	@Override
	protected IndexReader newIndexReader() {
		return new DerbyIndexReader(this.getDialect());
	}
}
