/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
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
 * along with sqlapp-core.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.sqlapp.data.db.sql;

import com.sqlapp.data.schemas.Type;
import com.sqlapp.util.AbstractSqlBuilder;

/**
 * Type削除クラス
 * 
 * @author satoh
 * 
 */
public abstract class AbstractDropTypeFactory<S extends AbstractSqlBuilder<?>>
		extends AbstractDropNamedObjectFactory<Type, S> {

	@Override
	protected void addDropObject(Type obj, S builder) {
		builder.drop().type();
		builder.name(obj, this.getOptions().isDecorateSchemaName());
	}

}
