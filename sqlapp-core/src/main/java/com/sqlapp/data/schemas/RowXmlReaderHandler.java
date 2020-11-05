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

import static com.sqlapp.util.CommonUtils.cast;

import javax.xml.stream.Location;
import javax.xml.stream.XMLStreamException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.sqlapp.data.schemas.function.RowValueConverter;
import com.sqlapp.util.StaxReader;
import com.sqlapp.util.xml.NotEmptyTextHandler;
import com.sqlapp.util.xml.SetValue;
import com.sqlapp.util.xml.StaxElementHandler;

/**
 * RowのXML読み込み
 * 
 * @author satoh
 * 
 */
class RowXmlReaderHandler extends AbstractObjectXmlReaderHandler<Row> {

	private static Logger log = LogManager.getLogger(RowXmlReaderHandler.class);

	private RowValueConverter rowValueConverter=null;
	
	protected RowXmlReaderHandler() {
	}

	@Override
	protected void initializeSetValue() {
		super.initializeSetValue();
		this.registerChild(new NotEmptyTextHandler());
		this.registerChild(new ValueHolderXmlReaderHandler());
	}

	private final String LOCAL_NAME = createNewInstance(null).getSimpleName();

	@Override
	public String getLocalName() {
		return LOCAL_NAME;
	}

	@Override
	public void doCallback(StaxReader reader, StaxElementHandler child,
			String name, Object ownObject, Object childObject)
			throws XMLStreamException {
		Row row=(Row) ownObject;
		try{
			setValue(row, name, childObject);
		} catch(RuntimeException e){
			if (row!=null){
				Location location=reader.getLocation();
				row.setDataSourceRowNumber(location.getLineNumber());
			}
			throw e;
		}
	}

	@Override
	protected void setValue(Row obj, String key, Object value)
			throws XMLStreamException {
		String comment=null;
		String option=null;
		if (value instanceof ValueHolder) {
			ValueHolder valueHolder=ValueHolder.class.cast(value);
			key = valueHolder.getKey();
			value = valueHolder.getValue();
			comment = valueHolder.getComment();
			option = valueHolder.getOption();
		}
		Column column = obj.getParent().getParent().getColumns().get(key);
		if (column != null) {
			obj.put(column, getRowValueConverter().apply(obj, column, value));
			if (comment!=null){
				obj.putRemarks(column, comment);
			}
			if (option!=null){
				obj.putOption(column, option);
			}
		} else {
			SetValue<Row, Object> setValue = cast(setValueMap.get(key));
			if (setValue != null && value != null) {
				setValue.setValue(obj, key, value);
			} else {
				if (log.isWarnEnabled()) {
					log.warn("[" + key + "] column not found.");
				}
			}
		}
	}

	/**
	 * @return the rowValueConverter
	 */
	protected RowValueConverter getRowValueConverter() {
		if (rowValueConverter==null){
			this.rowValueConverter=getReaderOptions().getRowValueConverter();
		}
		return rowValueConverter;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.sqlapp.dataset.AbstractSchemaHandler#createNewInstance(java.lang.
	 * Object)
	 */
	@Override
	protected Row createNewInstance(Object parentObject) {
		if (parentObject instanceof RowCollection) {
			RowCollection rows = (RowCollection) parentObject;
			return rows.newElement();
		} else if (parentObject instanceof Table) {
			Table table = (Table) parentObject;
			return table.newRow();
		}
		Row row = new Row();
		return row;
	}

}
