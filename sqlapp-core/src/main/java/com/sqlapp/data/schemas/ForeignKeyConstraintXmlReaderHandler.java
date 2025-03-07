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

import javax.xml.stream.XMLStreamException;

import com.sqlapp.util.xml.AbstractSetValue;
import com.sqlapp.util.xml.StaxElementHandler;

/**
 * ForeignKeyConstraintのXML読み込み
 * 
 * @author satoh
 * 
 */
class ForeignKeyConstraintXmlReaderHandler extends AbstractNamedObjectXmlReaderHandler<Constraint> {

	public ForeignKeyConstraintXmlReaderHandler() {
		super(()->new ForeignKeyConstraint());
	}

	@Override
	protected void initializeSetValue() {
		super.initializeSetValue();
		// Other
		StaxElementHandler elementHandler = new DummyTableXmlReaderHandler(){
			@Override
			public String getLocalName(){
				return ForeignKeyConstraint.RELATED_TABLE;
			}
		};
		register(elementHandler.getLocalName(),
				new AbstractSetValue<Constraint, DummyTable>() {
					@Override
					public void setValue(Constraint constraint,
							String name, DummyTable setValue)
							throws XMLStreamException {
						ForeignKeyConstraint target=(ForeignKeyConstraint)constraint;
						target.setRelatedTableSchemaName(setValue.getSchemaName());
						target.setRelatedTableName(setValue.getName());
						target.setRelatedColumns(setValue.getColumns()
								.toArray());
					}
				});
		registerChild(elementHandler.getLocalName(), elementHandler);
		//
		elementHandler = new DummyTableXmlReaderHandler();
		register(elementHandler.getLocalName(),
				new AbstractSetValue<Constraint, DummyTable>() {
					@Override
					public void setValue(Constraint constraint,
							String name, DummyTable setValue)
							throws XMLStreamException {
						ForeignKeyConstraint target=(ForeignKeyConstraint)constraint;
						target.setColumns(setValue.getColumns().toArray());
					}
				});
		registerChild(elementHandler.getLocalName(), elementHandler);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.dataset.AbstractSchemaHandler#createNewInstance()
	 */
	@Override
	protected ForeignKeyConstraint createNewInstance() {
		return new ForeignKeyConstraint();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.sqlapp.dataset.AbstractNamedObjectHandler#getParent(java.lang.Object)
	 */
	protected ConstraintCollection toParent(Object parentObject) {
		ConstraintCollection parent = null;
		if (parentObject instanceof Table) {
			parent = ((Table) parentObject).getConstraints();
		} else if (parentObject instanceof ConstraintCollection) {
			parent = (ConstraintCollection) parentObject;
		}
		return parent;
	}
}
