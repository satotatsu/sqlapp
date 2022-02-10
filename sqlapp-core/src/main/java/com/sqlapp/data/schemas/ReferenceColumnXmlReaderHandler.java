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

package com.sqlapp.data.schemas;

/**
 * ReferenceColumnのXML読み込み
 * 
 * @author satoh
 * 
 */
class ReferenceColumnXmlReaderHandler extends
		AbstractNamedObjectXmlReaderHandler<ReferenceColumn> {

	protected ReferenceColumnXmlReaderHandler() {
		super(()->new ReferenceColumn());
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.dataset.AbstractNamedObjectHandler#createNewInstance()
	 */
	@Override
	protected ReferenceColumn createNewInstance() {
		return new ReferenceColumn();
	}

	@Override
	protected ReferenceColumn getInstance(Object parentObject, String name,
			String specificName, String schemaName, ReferenceColumn obj) {
		ReferenceColumnCollection parent = null;
		if (parentObject instanceof Index) {
			Index index = (Index) parentObject;
			parent = index.getColumns();
		}else if (parentObject instanceof ForeignKeyConstraint) {
			ForeignKeyConstraint fk = (ForeignKeyConstraint) parentObject;
			parent = fk.getRelatedColumns();
		}
		if (parent != null) {
			ReferenceColumn column = parent.get(schemaName);
			if (column != null) {
				return column;
			}
			obj.setParent(parent);
		}
		return obj;
	}

	@Override
	protected ReferenceColumnCollection toParent(Object parentObject) {
		return null;
	}

}
