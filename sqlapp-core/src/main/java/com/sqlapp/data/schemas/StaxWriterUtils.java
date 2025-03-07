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

import static com.sqlapp.util.CommonUtils.isEmpty;

import java.util.Collection;
import java.util.List;

import javax.xml.stream.XMLStreamException;

import com.sqlapp.data.schemas.properties.NameProperty;
import com.sqlapp.util.StaxWriter;

public final class StaxWriterUtils {

	/**
	 * カラム情報を書き込みます
	 * 
	 * @param stax
	 * @param column
	 * @throws XMLStreamException
	 */
	public static void writeColumnSimple(StaxWriter stax, NameProperty<?> column)
			throws XMLStreamException {
		stax.newLine();
		stax.indent();
		stax.writeStartElement("column");
		stax.writeAttribute("name", column.getName());
		stax.writeEndElement();
	}

	/**
	 * カラム情報を書き込みます
	 * 
	 * @param stax
	 * @param columns
	 * @throws XMLStreamException
	 */
	public static void writeColumnSimple(StaxWriter stax,
			ReferenceColumn... columns) throws XMLStreamException {
		int size = columns.length;
		for (int i = 0; i < size; i++) {
			ReferenceColumn column = columns[i];
			writeColumnSimple(stax, column);
		}
	}

	/**
	 * カラム情報を書き込みます
	 * 
	 * @param stax
	 * @param columns
	 * @throws XMLStreamException
	 */
	@SafeVarargs
	public static <T extends NameProperty<?>> void writeColumnSimple(StaxWriter stax, T... columns)
			throws XMLStreamException {
		int size = columns.length;
		for (int i = 0; i < size; i++) {
			T column = columns[i];
			writeColumnSimple(stax, column);
		}
	}

	/**
	 * カラム情報を書き込みます
	 * 
	 * @param stax
	 * @param c
	 * @throws XMLStreamException
	 */
	public static void writeColumnSimple(StaxWriter stax,
			Collection<? extends NameProperty<?>> c) throws XMLStreamException {
		for (NameProperty<?> prop:c) {
			writeColumnSimple(stax, prop);
		}
	}

	/**
	 * DB特化情報の書き込み
	 * 
	 * @param stax
	 * @param elemnentName
	 * @param text
	 * @throws XMLStreamException
	 */
	public static void write(StaxWriter stax, String elemnentName,
			List<String> text) throws XMLStreamException {
		if (!isEmpty(text)) {
			stax.newLine();
			stax.indent();
			stax.writeElement(elemnentName, text);
		}
	}
}
