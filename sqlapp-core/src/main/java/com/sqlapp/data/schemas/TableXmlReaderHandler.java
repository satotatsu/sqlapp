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

import java.util.function.Supplier;

import javax.xml.stream.XMLStreamException;

import com.sqlapp.util.StaxReader;
import com.sqlapp.util.xml.AbstractSetValue;
import com.sqlapp.util.xml.StaxElementHandler;

/**
 * TableのXML読み込み
 * 
 * @author satoh
 * 
 */
class TableXmlReaderHandler extends AbstractNamedObjectXmlReaderHandler<Table> {

	public TableXmlReaderHandler() {
		this(()->new Table());
	}

	public TableXmlReaderHandler(Supplier<Table> supplier) {
		super(supplier);
	}

	@Override
	protected void initializeSetValue() {
		super.initializeSetValue();
		// Other
		StaxElementHandler handler = new PrimaryKeyConstraintXmlReaderHandler();
		register(handler.getLocalName(),
				new AbstractSetValue<Table, UniqueConstraint>() {
					@Override
					public void setValue(Table target, String name,
							UniqueConstraint setValue)
							throws XMLStreamException {
						target.getConstraints().add(setValue);
					}
				});
		registerChild(handler);
		this.setAlias(handler.getLocalName(), SchemaProperties.PRIMARY_KEY.getLabel());
		//
		handler = new RowCollectionXmlReaderHandler();
		register(handler.getLocalName(),
				new AbstractSetValue<Table, RowCollection>() {
					@Override
					public void setValue(Table target, String name,
							RowCollection setValue) throws XMLStreamException {
						target.getRows().addAll(setValue);
					}
				});
		registerChild(handler);
		//
		handler = new DummyTableCollectionXmlReaderHandler(){
			@Override
			public String getLocalName(){
				return "inherits";
			}
		};
		register(handler.getLocalName(),
				new AbstractSetValue<Table, DummyTableCollection>() {
					@Override
					public void setValue(Table target, String name,
							DummyTableCollection setValue)
							throws XMLStreamException {
						target.getInherits().addAll(setValue.toTableCollection());
					}
				});
		registerChild(handler);
	}

	@Override
	protected void finishDoHandle(StaxReader reader, Object parentObject,
			Table table) {
		setConstraints(table);
	}

	/**
	 * 制約のカラムをTableのカラムに変更する
	 * 
	 * @param table
	 */
	private void setConstraints(Table table) {
		ConstraintCollection constraints = table.getConstraints();
		constraints.setParent(table);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.sqlapp.dataset.AbstractNamedObjectHandler#getParent(java.lang.Object)
	 */
	@Override
	protected TableCollection toParent(Object parentObject) {
		TableCollection parent = null;
		if (parentObject instanceof TableCollection) {
			parent = (TableCollection) parentObject;
		} else if (parentObject instanceof Schema) {
			Schema schema = (Schema) parentObject;
			parent = schema.getTables();
		}
		return parent;
	}
}
