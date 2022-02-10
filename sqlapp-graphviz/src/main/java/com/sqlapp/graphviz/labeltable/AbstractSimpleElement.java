/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-graphviz.
 *
 * sqlapp-graphviz is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-graphviz is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-graphviz.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.sqlapp.graphviz.labeltable;

import javax.xml.stream.XMLStreamException;

import com.sqlapp.util.StaxWriter;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Accessors(chain=true) 
@Getter
@Setter
@EqualsAndHashCode(callSuper=true)
public abstract class AbstractSimpleElement extends AbstractHtmlElement{

	protected abstract String getElementName();

	@Override
	protected void writeXml(StaxWriter staxWriter) throws XMLStreamException {
		staxWriter.writeStartElement(getElementName());
		staxWriter.writeEndElement();
	}
}
