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
package com.sqlapp.data.db.metadata;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.schemas.DbObjects;
import com.sqlapp.data.schemas.PartitionFunction;
import com.sqlapp.data.schemas.SchemaObjectProperties;
/**
 * パーティション関数読み込み(SQLServer2005以降専用)
 * @author satoh
 *
 */
public abstract class PartitionFunctionReader extends AbstractCatalogNamedObjectMetadataReader<PartitionFunction>{
	protected static String PARTITION_FUNCTION_NAME="partition_function_name";

	protected PartitionFunctionReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected SchemaObjectProperties getSchemaObjectProperties(){
		return SchemaObjectProperties.PARTITION_FUNCTIONS;
	}

	@Override
	protected String getNameLabel(){
		return DbObjects.PARTITION_FUNCTION.getCamelCaseNameLabel();
	}
}
