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

import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

import javax.xml.stream.XMLStreamException;

import com.sqlapp.data.schemas.properties.ISchemaProperty;
import com.sqlapp.util.xml.AbstractSetValue;
import com.sqlapp.util.xml.StaxElementHandler;

abstract class AbstractBaseDbObjectXmlReaderHandler<T extends AbstractBaseDbObject<?>>
		extends AbstractObjectXmlReaderHandler<T> {

	protected AbstractBaseDbObjectXmlReaderHandler(Supplier<T> supplier) {
		super(supplier);
	}
	
	private boolean setParent=true;

	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Override
	protected void initializeSetValue() {
		super.initializeSetValue();
		Object obj=this.createNewInstance();
		Set<ISchemaProperty> properties=SchemaUtils.getAllSchemaProperties(obj.getClass());
		for(ISchemaProperty prop:properties){
			if (!isAutoRegistProp(prop)){
				continue;
			}
			if (isDbObject(prop.getValueClass())){
				List<StaxElementHandler> handlers=prop.getXmlHandlers();
				handlers.forEach(h->{
					registerChild(h);
				});
				handlers.stream().filter(h->h instanceof AbstractBaseDbObjectCollectionXmlReaderHandler)
				.map(h->(AbstractBaseDbObjectCollectionXmlReaderHandler)h)
				.forEach(h->{
					h.setInstanceGetter((o)->prop.getValue(o));
				});
				register(prop.getLabel(), new AbstractSetValue<T, Object>() {
					@Override
					public void setValue(T target, String name, Object setValue)
							throws XMLStreamException {
						prop.setValue(target, setValue);
					}
				});
			} else{
				registerTextHandler(prop);
				List<StaxElementHandler> handlers=prop.getXmlHandlers();
				if (!handlers.isEmpty()){
					registerTransparent(prop.getLabel(), handlers.toArray(new StaxElementHandler[0]));
				}
			}
		}
	}

	protected boolean isAutoRegistProp(ISchemaProperty prop){
		return true;
	}
	
	protected DbCommonObject<?> toParent(Object parentObject){
		if (parentObject instanceof DbCommonObject<?>){
			return ((DbCommonObject<?>)parentObject);
		}
		return null;
	}

	@Override
	protected T createNewInstance(Object parentObject) {
		T result = createNewInstance();
		DbCommonObject<?> parent = toParent(parentObject);
		if (parent != null) {
			setParent(result, parent);
		}
		return result;
	}

	protected void setParent(T t, DbCommonObject<?> parent) {
		if (this.setParent){
			t.setParent(parent);
		}
	}


	protected AbstractBaseDbObjectXmlReaderHandler<T> setSetParent(boolean setParent) {
		this.setParent = setParent;
		return this;
	}

	private final String LOCAL_NAME = createNewInstance().getSimpleName();

	@Override
	public String getLocalName() {
		return LOCAL_NAME;
	}
}
