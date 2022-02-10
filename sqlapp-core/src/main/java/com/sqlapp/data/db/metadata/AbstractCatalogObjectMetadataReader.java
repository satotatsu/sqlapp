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

import java.sql.Connection;
import java.util.List;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.AbstractDbObject;
import com.sqlapp.data.schemas.AbstractDbObjectCollection;
import com.sqlapp.data.schemas.Catalog;
import com.sqlapp.data.schemas.SchemaObjectProperties;

public abstract class AbstractCatalogObjectMetadataReader<T extends AbstractDbObject<T>>
		extends MetadataReader<T, Catalog> {

	protected AbstractCatalogObjectMetadataReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	public void loadFull(Connection connection, Catalog catalog) {
		List<T> list = getAllFull(connection);
		int size = list.size();
		AbstractDbObjectCollection<T> c = getSchemaObjectList(catalog);
		for (int i = 0; i < size; i++) {
			T obj = list.get(i);
			c.add(obj);
		}
	}

	@SuppressWarnings("unchecked")
	protected AbstractDbObjectCollection<T> getSchemaObjectList(
			Catalog catalog){
		return (AbstractDbObjectCollection<T>)getSchemaObjectProperties().getValue(catalog);
	}

	protected abstract SchemaObjectProperties getSchemaObjectProperties();

	/**
	 * カタログ名を含むパラメタコンテキストを作成します。
	 * 
	 */
	protected ParametersContext defaultParametersContext(Connection connection) {
		ParametersContext context = newParametersContext(connection,
				this.getCatalogName());
		return context;
	}
}
