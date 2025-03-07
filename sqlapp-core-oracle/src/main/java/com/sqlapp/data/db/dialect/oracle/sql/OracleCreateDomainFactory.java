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
import com.sqlapp.data.db.sql.AbstractCreateDomainFactory;
import com.sqlapp.data.schemas.Domain;

public class OracleCreateDomainFactory extends
		AbstractCreateDomainFactory<OracleSqlBuilder> {

	@Override
	protected void addCreateObject(final Domain obj, OracleSqlBuilder builder) {
		builder.create().or().replace().space().type();
		builder.name(obj, this.getOptions().isDecorateSchemaName());
		builder.as();
		if (obj.getArrayDimensionUpperBound()!=0){
			builder.varray(obj.getArrayDimensionUpperBound());
			builder.of();
			builder.space();
			builder.typeDefinition(obj.getDataType(), obj.getDataTypeName(), obj.getLength(), obj.getScale());
			builder.notNull(obj.isNotNull());
		} else{
			builder.table().of();
			builder.space();
			builder._add(obj.getDataTypeName());
			builder.notNull(obj.isNotNull());
		}
	}
}
