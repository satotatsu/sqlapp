/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-symfoware.
 *
 * sqlapp-core-symfoware is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-symfoware is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-symfoware.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.sqlapp.data.db.dialect.symfoware.metadata;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.jdbc.metadata.JdbcSchemaReader;
import com.sqlapp.data.db.metadata.ViewReader;
/**
 * Symfowareのスキーマ読み込み
 * @author satoh
 *
 */
public class SymfowareSchemaReader extends JdbcSchemaReader{

	public SymfowareSchemaReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected ViewReader newViewReader() {
		return new SymfowareViewReader(this.getDialect());
	}
}
