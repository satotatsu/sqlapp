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

package com.sqlapp.data.db.dialect.postgres.metadata;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.DomainReader;
import com.sqlapp.data.db.metadata.OperatorClassReader;
import com.sqlapp.data.db.metadata.OperatorReader;

public class Postgres83SchemaReader extends Postgres82SchemaReader{

	protected Postgres83SchemaReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected DomainReader newDomainReader() {
		return new Postgres83DomainReader(this.getDialect());
	}

	@Override
	protected OperatorReader newOperatorReader() {
		return new Postgres83OperatorReader(this.getDialect());
	}

	@Override
	protected OperatorClassReader newOperatorClassReader() {
		return new Postgres83OperatorClassReader(this.getDialect());
	}
}
