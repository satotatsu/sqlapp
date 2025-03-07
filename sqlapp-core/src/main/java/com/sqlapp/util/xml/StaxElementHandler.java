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

package com.sqlapp.util.xml;
import javax.xml.stream.XMLStreamException;
import com.sqlapp.util.StaxReader;


public interface StaxElementHandler {
	String getLocalName();
	String getNamespaceURI();
	StaxElementHandler getParent();
	void setParent(StaxElementHandler parent);
	void registerChild(StaxElementHandler child);
	boolean match(StaxReader reader) throws XMLStreamException;
	boolean handle(StaxReader reader, Object parentObject) throws XMLStreamException;
	void callback(StaxReader reader, String name, Object ownObject, Object childObject) throws XMLStreamException;	
}
