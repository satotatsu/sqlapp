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

package com.sqlapp.data.schemas.properties;

import java.util.List;

import com.sqlapp.util.xml.StaxElementHandler;

public interface ISchemaProperty {

	Class<?> getPropertyClass();

	Class<?> getGetterPropertyClass();

	Class<?> getSetterPropertyClass();

	Class<?> getValueClass();

	Object getDefaultValue();

	boolean isGetterInstanceof(Object obj);

	boolean isSetterInstanceof(Object obj);

	boolean isInstanceof(Object obj);

	boolean isValueInstanceof(Object obj);

	boolean setValue(Object obj, Object value);

	ISchemaProperty getDependent();

	boolean isEnabled(Object obj);

	Object getValue(Object obj);

	Object getCloneValue(Object obj);

	List<StaxElementHandler> getXmlHandlers();

	String getLabel();

}