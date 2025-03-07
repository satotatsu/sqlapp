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

package com.sqlapp.util;

import static com.sqlapp.util.CommonUtils.map;
import static com.sqlapp.util.CommonUtils.set;

import java.io.InputStream;
import java.io.Reader;
import java.util.Map;
import java.util.Set;

import javax.xml.namespace.QName;
import javax.xml.stream.Location;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

public class StaxReader implements AutoCloseable{

	private XMLStreamReader reader = null;
	/**
	 * 読み込んでいる行番号
	 */
	private Location currentLocation = null;
	/**
	 * Readerでサポートするイベント
	 */
	private Set<Integer> supportEvents = set(XMLStreamReader.ATTRIBUTE,
			XMLStreamReader.CDATA, XMLStreamReader.CHARACTERS,
			XMLStreamReader.START_ELEMENT, XMLStreamReader.END_ELEMENT);

	private final static Map<Integer, String> EVENT_NAME_MAP = map(12, 1.0f);

	static {
		EVENT_NAME_MAP.put(XMLStreamReader.CDATA, "CDATA");
		EVENT_NAME_MAP.put(XMLStreamReader.ATTRIBUTE, "ATTRIBUTE");
		EVENT_NAME_MAP.put(XMLStreamReader.CHARACTERS, "CHARACTERS");
		EVENT_NAME_MAP.put(XMLStreamReader.COMMENT, "COMMENT");
		EVENT_NAME_MAP.put(XMLStreamReader.SPACE, "SPACE");
		EVENT_NAME_MAP.put(XMLStreamReader.START_DOCUMENT, "START_DOCUMENT");
		EVENT_NAME_MAP.put(XMLStreamReader.END_DOCUMENT, "END_DOCUMENT");
		EVENT_NAME_MAP.put(XMLStreamReader.START_ELEMENT, "START_ELEMENT");
		EVENT_NAME_MAP.put(XMLStreamReader.END_ELEMENT, "END_ELEMENT");
		EVENT_NAME_MAP.put(XMLStreamReader.DTD, "DTD");
		EVENT_NAME_MAP.put(XMLStreamReader.ENTITY_DECLARATION,
				"ENTITY_DECLARATION");
		EVENT_NAME_MAP
				.put(XMLStreamReader.ENTITY_REFERENCE, "ENTITY_REFERENCE");
	}

	/**
	 * コンストラクタ
	 * 
	 * @param reader
	 * @throws XMLStreamException
	 */
	public StaxReader(final Reader reader) throws XMLStreamException {
		final XMLInputFactory factory = XMLInputFactory.newInstance();
		factory.setProperty(XMLInputFactory.IS_COALESCING, true);
		this.reader = factory.createXMLStreamReader(reader);
	}

	/**
	 * コンストラクタ
	 * 
	 * @param stream
	 * @throws XMLStreamException
	 */
	public StaxReader(final InputStream stream) throws XMLStreamException {
		final XMLInputFactory factory = XMLInputFactory.newInstance();
		factory.setProperty(XMLInputFactory.IS_COALESCING, true);
		this.reader = factory.createXMLStreamReader(stream);
	}

	/**
	 * コンストラクタ
	 * 
	 * @param stream
	 * @param encoding
	 *            文字エンコーディング
	 * @throws XMLStreamException
	 */
	public StaxReader(final InputStream stream, final String encoding)
			throws XMLStreamException {
		final XMLInputFactory factory = XMLInputFactory.newInstance();
		factory.setProperty(XMLInputFactory.IS_COALESCING, true);
		this.reader = factory.createXMLStreamReader(stream, encoding);
	}

	/**
	 * コンストラクタ
	 * 
	 * @param reader
	 */
	public StaxReader(final XMLStreamReader reader) {
		this.reader = reader;
	}

	/**
	 * 現在のイベントのローカル名を返します
	 * 
	 */
	public String getLocalName() {
		return reader.getLocalName();
	}

	/**
	 * 現在の START_ELEMENT または END_ELEMENT イベントの QName を返します
	 * 
	 */
	public QName getName() {
		return reader.getName();
	}

	/**
	 * 現在のイベントの接頭辞を返します
	 * 
	 */
	public String getPrefix() {
		return reader.getPrefix();
	}

	/**
	 * 指定されたインデックスで宣言された名前空間の URI を返します
	 * 
	 */
	public String getNamespaceURI() {
		return reader.getNamespaceURI();
	}

	/**
	 * 構文解析イベントの現在の値を文字列として返します
	 * 
	 */
	public String getText() {
		return reader.getText();
	}

	/**
	 * カーソルが開始タグをポイントしている場合は true、そうでない場合は false を返します
	 * 
	 * @return カーソルが開始タグをポイントしている場合は true、そうでない場合は false
	 */
	public boolean isStartElement() {
		return reader.isStartElement();
	}

	/**
	 * カーソルが終了タグをポイントしている場合は true、そうでない場合は false を返します
	 * 
	 * @return カーソルが終了タグをポイントしている場合は true、そうでない場合は false
	 */
	public boolean isEndElement() {
		return reader.isEndElement();
	}

	/**
	 * カーソルが空白文字だけで構成される文字データイベントをポイントしている場合は true を返します
	 * 
	 * @return カーソルがすべての空白文字をポイントしている場合は true、そうでない場合は false
	 */
	public boolean isWhiteSpace() {
		return reader.isWhiteSpace();
	}

	/**
	 * 
	 */
	public boolean isCharacters() {
		return reader.isCharacters();
	}

	/**
	 * 
	 */
	public boolean isStartDocument() {
		return reader.getEventType() == XMLStreamReader.START_DOCUMENT;
	}

	/**
	 * 
	 */
	public boolean isEndDocument() {
		return reader.getEventType() == XMLStreamReader.END_DOCUMENT;
	}

	/**
	 * カーソルがコメントをポイントしている場合は true を返します
	 * 
	 * @return コメントをポイントしている場合は true、そうでない場合は false
	 */
	public boolean isComment() {
		return reader.getEventType() == XMLStreamReader.COMMENT;
	}

	/**
	 * 構文解析イベントがまだある場合は true、そうでない場合は false を返します
	 * 
	 * @throws XMLStreamException
	 */
	public boolean hasNext() throws XMLStreamException {
		return reader.hasNext();
	}

	/**
	 * 要素エラー
	 * 
	 * @param elementName
	 * @throws XMLStreamException
	 */
	public void raiseElementException(final String elementName)
			throws XMLStreamException {
		String message = null;
		if (currentLocation == null) {
			message = MessageReader.getInstance().getMessage("E0000001",
					elementName, getLocalName(reader), "", "");
		} else {
			message = MessageReader.getInstance().getMessage("E0000001",
					elementName, getLocalName(reader),
					currentLocation.getLineNumber(),
					currentLocation.getColumnNumber());
		}
		final XMLStreamException e = new XMLStreamException(message);
		throw e;
	}

	private String getLocalName(final XMLStreamReader reader){
		try{
			return reader.getLocalName();
		} catch(final IllegalStateException e){
			return "";
		}
	}
	
	/**
	 * Attribute未存在エラー
	 * 
	 * @param attributeName
	 * @throws XMLStreamException
	 */
	public void raiseAttributeNotFoundException(final String attributeName)
			throws XMLStreamException {
		final String message = MessageReader.getInstance().getMessage("E0000002",
				attributeName, currentLocation.getLineNumber(),
				currentLocation.getColumnNumber());
		final XMLStreamException e = new XMLStreamException(message);
		throw e;
	}

	/**
	 * 次の構文解析イベントを取得します
	 * 
	 * @throws XMLStreamException
	 */
	public int next() throws XMLStreamException {
		while (hasNext()) {
			final int result = reader.next();
			if (supportEvents.contains(result)) {
				final Location location = reader.getLocation();
				currentLocation = location;
				return result;
			}
		}
		return 0;
	}

	/**
	 * プロセッサの現在の位置を返します。Location がわからない場合、プロセッサは Location の実装を返します。 これは、位置について
	 * -1 を返し、publicId と systemId について null を返します。 位置情報は、next() が呼び出されるまで有効です。
	 * 
	 * @throws XMLStreamException
	 */
	public Location getLocation() throws XMLStreamException {
		return reader.getLocation();
	}

	/**
	 * この START_ELEMENT 上の属性の数を返します
	 * 
	 */
	public int getAttributeCount() {
		return reader.getAttributeCount();
	}

	/**
	 * 指定されたインデックスにある属性の localName を返します
	 * 
	 * @param index
	 *            属性の位置
	 * @return 属性の localName
	 */
	public String getAttributeLocalName(final int index) {
		return reader.getAttributeLocalName(index);
	}

	/**
	 * インデックスにある属性の値を返します
	 * 
	 * @param index
	 *            属性の位置
	 * @return 属性値
	 */
	public String getAttributeValue(final int index) {
		return reader.getAttributeValue(index);
	}

	/**
	 * @return the supportEvents
	 */
	public Set<Integer> getSupportEvents() {
		return supportEvents;
	}

	/**
	 * @param supportEvents
	 *            the supportEvents to set
	 */
	public void setSupportEvents(final Set<Integer> supportEvents) {
		this.supportEvents = supportEvents;
	}

	public void setSupportEvents(final Integer... supportEvents) {
		this.supportEvents = set(supportEvents);
	}

	/**
	 * 最初の開始要素まで移動します
	 * 
	 * @throws XMLStreamException
	 */
	public int nextFristStartElement() throws XMLStreamException {
		int result = 0;
		while (this.hasNext()) {
			result = next();
			if (isStartElement()) {
				return result;
			}
		}
		return result;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		final ToStringBuilder builder = new ToStringBuilder(StaxReader.class);
		builder.add("eventType", EVENT_NAME_MAP.get(reader.getEventType()));
		if (reader.isStartElement() || reader.isEndElement()) {
			builder.add("localName", reader.getLocalName());
			builder.add("prefix", reader.getPrefix());
			builder.add("namespaceURI", reader.getNamespaceURI());
		}
		if (reader.isStartElement()) {
			final int size = reader.getAttributeCount();
			for (int i = 0; i < size; i++) {
				final String name = reader.getAttributeLocalName(i);
				final String value = reader.getAttributeValue(i);
				builder.add(name, value);
			}
		}
		if (reader.isCharacters()) {
			builder.add("value", reader.getText());
		}
		return builder.toString();
	}

	@Override
	public void close() throws Exception {
		if (reader != null) {
			reader.close();
			this.reader=null;
		}
	}
}
