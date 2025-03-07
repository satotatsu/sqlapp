/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
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
 * along with sqlapp-core.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.schemas;

/**
 * ConstraintCollectionのXML読み込み
 * 
 * @author satoh
 * 
 */
class ConstraintCollectionXmlReaderHandler extends AbstractNamedObjectCollectionXmlReaderHandler<ConstraintCollection> {

	public ConstraintCollectionXmlReaderHandler() {
		super(()->new ConstraintCollection());
	}

	@Override
	protected void initializeSetValue() {
		super.initializeSetValue();
		PrimaryKeyConstraintXmlReaderHandler primaryKeyHandler = new PrimaryKeyConstraintXmlReaderHandler();
		setChild(primaryKeyHandler);
		this.setAlias(primaryKeyHandler.getLocalName(),
				SchemaProperties.PRIMARY_KEY.getLabel());
		setChild(new UniqueConstraint().getDbObjectXmlReaderHandler());
		setChild(new ExcludeConstraint().getDbObjectXmlReaderHandler());
		setChild(new CheckConstraint().getDbObjectXmlReaderHandler());
		setChild(new ForeignKeyConstraint().getDbObjectXmlReaderHandler());
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.sqlapp.dataset.xml.AbstractSchemaHandler#getInstance(java.lang.Object
	 * , java.lang.String, java.lang.String, java.lang.Object)
	 */
	@Override
	protected ConstraintCollection getInstance(Object parentObject,
			ConstraintCollection obj) {
		if (parentObject instanceof Table) {
			Table table = ((Table) parentObject);
			if (table.getConstraints() != null) {
				return table.getConstraints();
			}
			obj.setParent(table);
		}
		return obj;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.sqlapp.dataset.AbstractNamedObjectListHandler#createNewInstance()
	 */
	@Override
	protected ConstraintCollection createNewInstance() {
		return new ConstraintCollection();
	}
}
