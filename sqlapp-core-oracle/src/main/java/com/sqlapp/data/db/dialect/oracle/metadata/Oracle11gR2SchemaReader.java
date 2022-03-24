/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
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
 * along with sqlapp-core-oracle.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.sqlapp.data.db.dialect.oracle.metadata;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.MviewLogReader;

public class Oracle11gR2SchemaReader extends Oracle11gSchemaReader{

	protected Oracle11gR2SchemaReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected MviewLogReader newMviewLogReader() {
		return new Oracle11gR2MviewLogReader(this.getDialect());
	}
}
