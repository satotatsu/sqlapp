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

import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;
import java.util.Deque;
import java.util.LinkedList;
import java.util.Set;

import javax.xml.namespace.NamespaceContext;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import com.sqlapp.util.CommonUtils;

/**
 * 標準のXMLStreamWriterが空要素を出力できないので、空要素出力機能を持った最低限の機能のXMLStreamWriter
 * 
 * @author tatsuo satoh
 * 
 */
public class SimpleXMLStreamWriter implements XMLStreamWriter {

	private Writer writer = null;

	private OutputStream outputStream = null;

	private Deque<ElementStatus> elementStack = new LinkedList<ElementStatus>();

	public SimpleXMLStreamWriter(Writer writer) {
		this.writer = writer;
	}

	public SimpleXMLStreamWriter(OutputStream outputStream) {
		this.outputStream = outputStream;
	}

	private SimpleXMLStreamWriter writeInternal(String value) {
		try {
			if (value == null) {
				return this;
			}
			if (writer != null) {
				writer.append(value);
			}
			if (outputStream != null) {
				outputStream.write(value.getBytes("UTF-8"));
			}
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		return this;
	}

	private String replaceInvalidCharacter(String text) {
		if (text==null){
			return null;
		}
		return CommonUtils.replaceInvalidCharacter(text);
	}

	private String escape(String text) {
		if (text == null || text.length() == 0) {
			return text;
		}
		StringBuilder builder = new StringBuilder((int) (text.length() * 1.2));
		int size = text.codePointCount(0, text.length());
		for (int i = 0; i < size; i++) {
			int codePoint = text.codePointAt(i);
			if (codePoint == '<') {
				builder.append("&lt;");
			} else if (codePoint == '&') {
				builder.append("&amp;");
			} else if (codePoint == '>') {
				builder.append("&gt;");
			} else if (codePoint == '"') {
				builder.append("&quot;");
//			} else if (codePoint == '\'') {
//				builder.append("&apos;");
			} else if (codePoint == '\n') {
				builder.appendCodePoint(codePoint);
			} else if (codePoint == '\t') {
				builder.appendCodePoint(codePoint);
			} else if (Character.isISOControl(codePoint)) {
				//
			} else {
				builder.appendCodePoint(codePoint);
			}
		}
		return builder.toString();
	}

	private String escapeAttributeValue(String text) {
		if (text == null || text.length() == 0) {
			return text;
		}
		StringBuilder builder = new StringBuilder((int) (text.length() * 1.2));
		int size = text.codePointCount(0, text.length());
		for (int i = 0; i < size; i++) {
			int codePoint = text.codePointAt(i);
			if (codePoint == '<') {
				builder.append("&lt;");
			} else if (codePoint == '&') {
				builder.append("&amp;");
			} else if (codePoint == '>') {
				builder.append("&gt;");
			} else if (codePoint == '"') {
				builder.append("&quot;");
			} else if (Character.isISOControl(codePoint)) {
				//
			} else {
				builder.appendCodePoint(codePoint);
			}
		}
		return builder.toString();
	}

	@Override
	public void writeStartElement(String localName) throws XMLStreamException {
		ElementStatus elementStatus = elementStack.peek();
		if (elementStatus == null) {
		} else {
			if (!elementStatus.isClose()) {
				elementStatus.setClose(true);
				writeInternal(">");
			}
		}
		writeInternal("<").writeInternal(localName);
		elementStatus = new ElementStatus(localName);
		elementStack.push(elementStatus);
	}

	@Override
	public void writeStartElement(String namespaceURI, String localName)
			throws XMLStreamException {
		throw new UnsupportedOperationException(
				"writeStartElement(namespaceURI, localName)");
	}

	@Override
	public void writeStartElement(String prefix, String localName,
			String namespaceURI) throws XMLStreamException {
		throw new UnsupportedOperationException(
				"writeStartElement(prefix, namespaceURI, localName)");
	}

	@Override
	public void writeEmptyElement(String namespaceURI, String localName)
			throws XMLStreamException {
		throw new UnsupportedOperationException(
				"writeStartElement(prefix, namespaceURI, localName)");
	}

	@Override
	public void writeEmptyElement(String prefix, String localName,
			String namespaceURI) throws XMLStreamException {
		throw new UnsupportedOperationException(
				"writeStartElement(prefix, namespaceURI, localName)");
	}

	@Override
	public void writeEmptyElement(String localName) throws XMLStreamException {
		closeStartElement();
		writeInternal("<").writeInternal(localName).writeInternal("/>");
	}

	@Override
	public void writeEndElement() throws XMLStreamException {
		ElementStatus elementStatus = elementStack.pop();
		if (elementStatus.isClose()) {
			writeInternal("</").writeInternal(elementStatus.getElementName())
					.writeInternal(">");
		} else {
			writeInternal("/>");
		}
	}

	@Override
	public void writeEndDocument() throws XMLStreamException {
	}

	@Override
	public void close() throws XMLStreamException {
		try {
			if (writer != null) {
				writer.close();
			}
			if (outputStream != null) {
				outputStream.close();
			}
		} catch (IOException e) {
			throw new XMLStreamException(e);
		}
	}

	@Override
	public void flush() throws XMLStreamException {
		try {
			if (writer != null) {
				writer.flush();
			}
			if (outputStream != null) {
				outputStream.flush();
			}
		} catch (IOException e) {
			throw new XMLStreamException(e);
		}
	}

	@Override
	public void writeAttribute(String localName, String value)
			throws XMLStreamException {
		ElementStatus elementStatus = elementStack.peek();
		elementStatus.addAttributeName(localName);
		writeInternal(" ").writeInternal(localName).writeInternal("=")
				.writeInternal("\"").writeInternal(escapeAttributeValue(value))
				.writeInternal("\"");
	}

	@Override
	public void writeAttribute(String prefix, String namespaceURI,
			String localName, String value) throws XMLStreamException {
		throw new UnsupportedOperationException(
				"writeStartElement(prefix, namespaceURI, localName)");
	}

	@Override
	public void writeAttribute(String namespaceURI, String localName,
			String value) throws XMLStreamException {
		throw new UnsupportedOperationException(
				"writeAttribute(namespaceURI, localName, value)");
	}

	@Override
	public void writeNamespace(String prefix, String namespaceURI)
			throws XMLStreamException {
		throw new UnsupportedOperationException(
				"writeNamespace(prefix, namespaceURI)");
	}

	@Override
	public void writeDefaultNamespace(String namespaceURI)
			throws XMLStreamException {
		throw new UnsupportedOperationException(
				"writeDefaultNamespace(namespaceURI)");
	}

	@Override
	public void writeComment(String data) throws XMLStreamException {
		this.writeInternal("<!--").writeInternal(escape(data))
				.writeInternal("-->");
	}

	@Override
	public void writeProcessingInstruction(String target)
			throws XMLStreamException {
		throw new UnsupportedOperationException(
				"writeProcessingInstruction(target)");
	}

	@Override
	public void writeProcessingInstruction(String target, String data)
			throws XMLStreamException {
		throw new UnsupportedOperationException(
				"writeStartElement(prefix, namespaceURI, localName)");
	}

	@Override
	public void writeCData(String data) throws XMLStreamException {
		closeStartElement();
		this.writeInternal("<![CDATA[");
		this.writeInternal(replaceInvalidCharacter(data).replace("]]>", "]]&gt;"));
		this.writeInternal("]]>");
	}

	private void closeStartElement() {
		ElementStatus elementStatus = elementStack.peek();
		if (elementStatus != null) {
			if (!elementStatus.isClose()) {
				this.writeInternal(">");
				elementStatus.setClose(true);
			}
		}
	}

	@Override
	public void writeDTD(String dtd) throws XMLStreamException {
		throw new UnsupportedOperationException(
				"writeStartElement(prefix, namespaceURI, localName)");
	}

	@Override
	public void writeEntityRef(String name) throws XMLStreamException {
		throw new UnsupportedOperationException(
				"writeStartElement(prefix, namespaceURI, localName)");
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see javax.xml.stream.XMLStreamWriter#writeStartDocument()
	 */
	@Override
	public void writeStartDocument() throws XMLStreamException {
		writeStartDocument("1.0");
	}

	@Override
	public void writeStartDocument(String version) throws XMLStreamException {
		writeStartDocument("utf-8", version);
	}

	@Override
	public void writeStartDocument(String encoding, String version)
			throws XMLStreamException {
		this.writeInternal("<?xml version=\"" + version + "\" encoding=\""
				+ encoding + "\"?>");
	}

	@Override
	public void writeCharacters(String text) throws XMLStreamException {
		closeStartElement();
		this.writeInternal(this.escape(text));
	}

	@Override
	public void writeCharacters(char[] text, int start, int len)
			throws XMLStreamException {
		throw new UnsupportedOperationException(
				"writeCharacters(text, start, len)");
	}

	@Override
	public String getPrefix(String uri) throws XMLStreamException {
		throw new UnsupportedOperationException("getPrefix(uri)");
	}

	@Override
	public void setPrefix(String prefix, String uri) throws XMLStreamException {
		throw new UnsupportedOperationException("setPrefix(prefix, uri)");
	}

	@Override
	public void setDefaultNamespace(String uri) throws XMLStreamException {
		throw new UnsupportedOperationException("setDefaultNamespace(uri)");
	}

	@Override
	public void setNamespaceContext(NamespaceContext context)
			throws XMLStreamException {
		throw new UnsupportedOperationException("setNamespaceContext(context)");
	}

	@Override
	public NamespaceContext getNamespaceContext() {
		throw new UnsupportedOperationException("getNamespaceContext()");
	}

	@Override
	public Object getProperty(String name) throws IllegalArgumentException {
		throw new UnsupportedOperationException("getProperty(name)");
	}

	static class ElementStatus {

		ElementStatus(String elementName) {
			this.elementName = elementName;
		}

		private String elementName;
		private Set<String> attributeNames;
		private boolean close = false;

		public void addAttributeName(String attributeName) {
			if (attributeNames == null) {
				attributeNames = CommonUtils.linkedSet();
			}
			attributeNames.add(attributeName);
		}

		/**
		 * @return the elementName
		 */
		public String getElementName() {
			return elementName;
		}

		/**
		 * @param elementName
		 *            the elementName to set
		 */
		public void setElementName(String elementName) {
			this.elementName = elementName;
		}

		/**
		 * @return the attributeNames
		 */
		public Set<String> getAttributeNames() {
			return attributeNames;
		}

		/**
		 * @param attributeNames
		 *            the attributeNames to set
		 */
		public void setAttributeNames(Set<String> attributeNames) {
			this.attributeNames = attributeNames;
		}

		/**
		 * @return the close
		 */
		public boolean isClose() {
			return close;
		}

		/**
		 * @param close
		 *            the close to set
		 */
		public void setClose(boolean close) {
			this.close = close;
		}
	}

}
