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

import javax.xml.stream.XMLStreamException;

import com.sqlapp.data.schemas.function.TableRowPredicate;
import com.sqlapp.util.xml.AbstractSetValue;

/**
 * RowCollectionのXML読み込み
 * 
 * @author satoh
 * 
 */
class RowCollectionXmlReaderHandler extends AbstractObjectXmlReaderHandler<RowCollection> {

	public RowCollectionXmlReaderHandler() {
	}
	
	@Override
	protected void initializeSetValue() {
		super.initializeSetValue();
		setChild(new Row().getDbObjectXmlReaderHandler());
	}

	protected void setChild(RowXmlReaderHandler handler) {
		register(handler.getLocalName(),
				new AbstractSetValue<RowCollection, Row>() {
					@Override
					public void setValue(RowCollection target, String name,
							Row setValue) throws XMLStreamException {
						if(getAddRow().test(target.getParent(), setValue)){
							target.add(setValue);
						}
					}
				});
		registerChild(handler);
	}
	
	private TableRowPredicate addRowPredicate=null;
	
	private TableRowPredicate getAddRow(){
		if (addRowPredicate!=null){
			return addRowPredicate;
		}
		addRowPredicate=this.getReaderOptions().getAddRow();
		return addRowPredicate;
	}
	
	private static final String LOCAL_NAME = new RowCollection()
			.getSimpleName();

	@Override
	public String getLocalName() {
		return LOCAL_NAME;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.sqlapp.dataset.xml.AbstractSchemaHandler#createNewInstance(java.lang
	 * .Object)
	 */
	@Override
	protected RowCollection createNewInstance(Object parentObject) {
		if (parentObject instanceof Table) {
			Table table = (Table) parentObject;
			return table.getRows();
		}
		RowCollection result = new RowCollection();
		return result;
	}
	
}
