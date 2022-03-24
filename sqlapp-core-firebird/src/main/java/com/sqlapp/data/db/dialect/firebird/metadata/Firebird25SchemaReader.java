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

import com.sqlapp.data.db.dialect.Dialect;

/**
 * Firebirdのスキーマ読み込み(Firebirdはスキーマがないので常に１つのSchemaオブジェクトを返す)
 * 
 * @author satoh
 * 
 */
public class Firebird25SchemaReader extends Firebird20SchemaReader {

	protected Firebird25SchemaReader(Dialect dialect) {
		super(dialect);
	}

}
