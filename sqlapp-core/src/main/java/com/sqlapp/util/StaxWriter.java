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

import static com.sqlapp.util.CommonUtils.getString;
import static com.sqlapp.util.CommonUtils.isEmpty;

import java.io.OutputStream;
import java.io.Writer;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import com.sqlapp.data.converter.Base64Converter;
import com.sqlapp.data.converter.Converter;
import com.sqlapp.data.converter.Converters;
import com.sqlapp.data.schemas.DbInfo;
import com.sqlapp.data.schemas.SchemaProperties;
import com.sqlapp.util.xml.SimpleXMLStreamWriter;

/**
 * XML書き込みクラス
 * 
 * @author satoh
 * 
 */
public class StaxWriter {

	private final XMLStreamWriter writer;

	public static final String LIST_ELEMENT = "list";
	public static final String MAP_ELEMENT = "map";
	public static final String ENTRY_ELEMENT = "entry";
	public static final String SET_ELEMENT = "set";
	public static final String KEY_ELEMENT = "key";
	public static final String VALUE_ELEMENT = "value";
	public static final String VALUES_ELEMENT = "values";
	public static final String NULL_ELEMENT = "null";

	private Converter<byte[]> binaryConverter = new Base64Converter();

	/**
	 * 空タグの書き込み
	 */
	private boolean writeEmptyTag = true;
	/**
	 * 改行
	 */
	private String lineSeparator = "\n";
	/**
	 * インデントレベル
	 */
	private int indentLevel = 0;
	/**
	 * インデント文字
	 */
	private String indentString = "\t";
	/**
	 * 現在のインデント文字列
	 */
	private String currentIndentString = null;
	/**
	 * 書き込み時の値のハンドラー
	 */
	private ValueHandler valueHandler = null;
	/**
	 * 現在までに出力したElementの数
	 */
	private long writeElementCount = 0;
	/**
	 * 現在までに出力したAttributeの数
	 */
	private long writeAttributeCount = 0;
	/**
	 * 現在までに出力したValueの数
	 */
	private long writeValueCount = 0;
	/**
	 * 現在までに出力したElementの数
	 */
	private long writeCommentCount = 0;
	/**
	 * 
	 */
	private boolean writePreserveSpace=true;
	/**
	 * 
	 */
	private boolean writeAutoStartDocument=true;
	
	/**
	 * コンストラクタ
	 * 
	 * @param writer
	 * @throws XMLStreamException
	 */
	public StaxWriter(final Writer writer) throws XMLStreamException {
		// XMLOutputFactory factory = newXMLOutputFactory();
		// StringWriter writer=new StringWriter();
		// this.writer = factory.createXMLStreamWriter(writer);
		this.writer = new SimpleXMLStreamWriter(writer);
	}

	public StaxWriter setHtmlMode(){
		setWriteAutoStartDocument(false);
		setWritePreserveSpace(false);
		return this;
	}
	
	/**
	 * コンストラクタ
	 * 
	 * @param writer
	 * @throws XMLStreamException
	 */
	public StaxWriter(final XMLStreamWriter writer) throws XMLStreamException {
		this.writer = writer;
	}

	/**
	 * コンストラクタ
	 * 
	 * @param outputStream
	 * @throws XMLStreamException
	 */
	public StaxWriter(final OutputStream outputStream) throws XMLStreamException {
		// XMLOutputFactory factory = newXMLOutputFactory();
		// this.writer = factory.createXMLStreamWriter(outputStream);
		this.writer = new SimpleXMLStreamWriter(outputStream);
	}

	/**
	 * @return the binaryConverter
	 */
	public Converter<byte[]> getBinaryConverter() {
		return binaryConverter;
	}

	/**
	 * @param binaryConverter
	 *            the binaryConverter to set
	 */
	public void setBinaryConverter(final Converter<byte[]> binaryConverter) {
		this.binaryConverter = binaryConverter;
	}

	/**
	 * 出力した要素数をカウントアップします
	 * 
	 * @throws XMLStreamException
	 */
	protected void addElementCount() throws XMLStreamException {
		if (this.writeElementCount == 0) {
			if (this.writePreserveSpace){
				writer.writeAttribute("xml:space", "preserve");
			}
		}
		writeElementCount++;
	}

	/**
	 * 出力したAttribute数をカウントアップします
	 */
	protected void addAttributeCount() throws XMLStreamException {
		writeAttributeCount++;
	}

	/**
	 * 出力したValue数をカウントアップします
	 */
	protected void addValueCount() throws XMLStreamException {
		writeValueCount++;
	}

	/**
	 * 出力したコメント数をカウントアップします
	 */
	protected void addCommentCount() throws XMLStreamException {
		writeCommentCount++;
	}

	/**
	 * @return the writeElementCount
	 */
	public long getWriteElementCount() throws XMLStreamException {
		return writeElementCount;
	}

	/**
	 * @return the writeAttributeCount
	 */
	public long getWriteAttributeCount() {
		return writeAttributeCount;
	}

	/**
	 * @return the writeValueCount
	 */
	public long getWriteValueCount() {
		return writeValueCount;
	}

	/**
	 * @return the writeCommentCount
	 */
	public long getWriteCommentCount() {
		return writeCommentCount;
	}

	/**
	 * @return the writePreserveSpace
	 */
	public boolean isWritePreserveSpace() {
		return writePreserveSpace;
	}

	/**
	 * @param writePreserveSpace the writePreserveSpace to set
	 */
	public StaxWriter setWritePreserveSpace(final boolean writePreserveSpace) {
		this.writePreserveSpace = writePreserveSpace;
		return this;
	}

	/**
	 * @param valueHandler
	 *            the handleValue to set
	 */
	public StaxWriter setHandleValue(final ValueHandler valueHandler) {
		this.valueHandler = valueHandler;
		return this;
	}

	private static Converters converters = Converters
			.getNewBooleanTrueInstance();

	protected XMLOutputFactory newXMLOutputFactory() {
		// XMLOutputFactory2 factory = (XMLOutputFactory2)
		// org.codehaus.stax2.XMLOutputFactory2
		// .newInstance();
		// factory.setProperty(WstxOutputFactory.P_AUTOMATIC_EMPTY_ELEMENTS,
		// true);
		final XMLOutputFactory factory = XMLOutputFactory.newInstance();
		return factory;
	}

	@SuppressWarnings("unchecked")
	protected <T> T handleValue(final String prefix, final String namespaceURI,
			final String localName, final Object value) {
		if (valueHandler == null) {
			return (T) value;
		}
		return (T) valueHandler.convert(prefix, namespaceURI, localName, value);
	}

	/**
	 * 属性を出力ストリームに書き込みます
	 * 
	 * @param localName
	 *            属性のローカル名
	 * @param value
	 *            属性の値
	 * @throws XMLStreamException
	 */
	public StaxWriter writeAttribute(final String localName, String value)
			throws XMLStreamException {
		value = handleValue(null, null, localName, value);
		if (value != null) {
			writer.writeAttribute(localName, value);
			addAttributeCount();
		}
		return this;
	}

	/**
	 * 属性を出力ストリームに書き込みます
	 * 
	 * @param prop
	 *            属性のローカル名
	 * @param value
	 *            属性の値
	 * @throws XMLStreamException
	 */
	public StaxWriter writeAttribute(final SchemaProperties prop, final Object value)
			throws XMLStreamException {
		return writeAttribute(prop.getLabel(), prop.getValue(value));
	}

	public StaxWriter writeAttribute(final String localName, Object value)
			throws XMLStreamException {
		value = handleValue(null, null, localName, value);
		if (value != null) {
			final String text = getConverters()
					.convertString(value, value.getClass());
			return writeAttribute(localName, text);
		}
		return this;
	}

	/**
	 * @return the writeStartDocument
	 */
	protected boolean isWriteStartDocument() {
		return writeStartDocument;
	}

	/**
	 * @param writeStartDocument
	 *            the writeStartDocument to set
	 */
	protected void setWriteStartDocument(final boolean writeStartDocument) {
		this.writeStartDocument = writeStartDocument;
	}

	protected Converters getConverters() {
		return converters;
	}

	/**
	 * 属性を出力ストリームに書き込みます
	 * 
	 * @param namespaceURI
	 *            この属性の接頭辞の URI
	 * @param localName
	 *            属性のローカル名
	 * @param value
	 *            属性の値
	 * @throws XMLStreamException
	 */
	public StaxWriter writeAttribute(final String namespaceURI, final String localName,
			String value) throws XMLStreamException {
		value = handleValue(null, namespaceURI, localName, value);
		if (value != null) {
			writer.writeAttribute(namespaceURI, localName, value);
			addAttributeCount();
		}
		return this;
	}

	/**
	 * 属性を出力ストリームに書き込みます
	 * 
	 * @param prefix
	 *            この属性の接頭辞
	 * @param namespaceURI
	 *            この属性の接頭辞の URI
	 * @param localName
	 *            属性のローカル名
	 * @param value
	 *            属性の値
	 * @throws XMLStreamException
	 */
	public StaxWriter writeAttribute(final String prefix, final String namespaceURI,
			final String localName, String value) throws XMLStreamException {
		value = handleValue(prefix, namespaceURI, localName, value);
		if (value != null) {
			writer.writeAttribute(prefix, namespaceURI, localName, value);
			addAttributeCount();
		}
		return this;
	}

	/**
	 * CData セクションを書き込みます
	 * 
	 * @param data
	 * @throws XMLStreamException
	 */
	public StaxWriter writeCData(final String data) throws XMLStreamException {
		if (data != null) {
			writer.writeCData(data);
			addValueCount();
		}
		return this;
	}

	/**
	 * コメントアウトされたデータを使用して XML コメントを書き込みます
	 * 
	 * @param data
	 *            コメント (null化)
	 * @throws XMLStreamException
	 */
	public StaxWriter writeComment(final String data) throws XMLStreamException {
		if (data != null) {
			writer.writeComment(data);
			addCommentCount();
		}
		return this;
	}

	/**
	 * 出力にテキストを書き込みます
	 * 
	 * @param text
	 *            書き込む値
	 * @throws XMLStreamException
	 */
	public StaxWriter writeCharacters(final String text) throws XMLStreamException {
		if (text != null) {
			writer.writeCharacters(text);
			addValueCount();
		}
		return this;
	}

	/**
	 * オブジェクトをテキストに書き込みます。
	 * 
	 * @param obj
	 * @throws XMLStreamException
	 */
	public StaxWriter writeCharacters(final Object obj) throws XMLStreamException {
		if (obj != null) {
			if (obj instanceof byte[]) {
				writer.writeCharacters(getBinaryConverter().convertString(
						(byte[]) obj));
				addValueCount();
			} else {
				final String text = getConverters()
						.convertString(obj, obj.getClass());
				writer.writeCharacters(text);
				addValueCount();
			}
		}
		return this;
	}

	/**
	 * 出力に開始タグを書き込みます
	 * 
	 * @param namespaceURI
	 *            使用する接頭辞の namespaceURI (null 以外)
	 * @param localName
	 *            タグのローカル名 (null 以外)
	 * @throws XMLStreamException
	 */
	public StaxWriter writeStartElement(final String namespaceURI, final String localName)
			throws XMLStreamException {
		this.writeStartDocument();
		writer.writeStartElement(namespaceURI, localName);
		addElementCount();
		return this;
	}

	/**
	 * 出力に開始タグを書き込みます
	 * 
	 * @param localName
	 *            タグのローカル名 (null 以外)
	 * @throws XMLStreamException
	 */
	public StaxWriter writeStartElement(final String localName)
			throws XMLStreamException {
		this.writeStartDocument();
		writer.writeStartElement(localName);
		addElementCount();
		return this;
	}

	/**
	 * 出力にタグを書き込みます
	 * 
	 * @param localName
	 *            タグのローカル名 (null 以外)
	 * @throws XMLStreamException
	 */
	public StaxWriter writeElement(final String localName, final RunWithException run)
			throws XMLStreamException {
		this.writeStartElement(localName);
		run.run();
		this.writeEndElement();
		return this;
	}

	/**
	 * 出力に空の要素タグを書き込みます
	 * 
	 * @param namespaceURI
	 *            使用する接頭辞の namespaceURI (null 以外)
	 * @param localName
	 *            タグのローカル名 (null 以外)
	 * @throws XMLStreamException
	 */
	public StaxWriter writeEmptyElement(final String namespaceURI, final String localName)
			throws XMLStreamException {
		this.writeStartDocument();
		writer.writeEmptyElement(localName);
		addElementCount();
		return this;
	}

	/**
	 * 出力に空の要素タグを書き込みます
	 * 
	 * @param localName
	 *            タグのローカル名
	 * @throws XMLStreamException
	 */
	public StaxWriter writeEmptyElement(final String localName)
			throws XMLStreamException {
		this.writeStartDocument();
		writer.writeEmptyElement(localName);
		addElementCount();
		return this;
	}

	/**
	 * 出力に終了タグを書き込みます
	 * 
	 * @throws XMLStreamException
	 */
	public StaxWriter writeEndElement() throws XMLStreamException {
		writer.writeEndElement();
		return this;
	}

	/**
	 * XML 宣言を書き込みます
	 * 
	 * @throws XMLStreamException
	 */
	public StaxWriter writeStartDocument() throws XMLStreamException {
		return writeStartDocument("utf-8", "1.0");
	}

	private boolean writeStartDocument = false;

	/**
	 * XML 宣言を書き込みます
	 * 
	 * @param encoding
	 *            XML 宣言のエンコーディング
	 * @param version
	 *            XML ドキュメントのバージョン
	 * @throws XMLStreamException
	 */
	public StaxWriter writeStartDocument(final String encoding, final String version)
			throws XMLStreamException {
		if (!writeAutoStartDocument){
			return this;
		}
		if (this.isWriteStartDocument()) {
			return this;
		}
		writer.writeStartDocument(encoding, version);
		writeStartDocument = true;
		return newLine();
	}

	/**
	 * XML 宣言を書き込みます
	 * 
	 * @param encoding
	 *            XML 宣言のエンコーディング
	 * @param version
	 *            XML ドキュメントのバージョン
	 * @throws XMLStreamException
	 */
	public StaxWriter writeDocument(final String encoding, final String version, final RunWithException run)
			throws XMLStreamException {
		writeStartDocument(encoding, version);
		run.run();
		return writeEndDocument();
	}

	/**
	 * 
	 * @throws XMLStreamException
	 */
	public StaxWriter writeEndDocument() throws XMLStreamException {
		writer.writeEndDocument();
		return this;
	}

	/**
	 * 要素(値付き)の書き込み
	 * 
	 * @param localName
	 * @param value
	 * @throws XMLStreamException
	 */
	public StaxWriter writeElement(final String localName, final String value)
			throws XMLStreamException {
		if (value != null) {
			writeStartElement(localName);
			writeCharacters(value);
			writeEndElement();
		} else {
			if (writeEmptyTag) {
				writeEmptyElement(localName);
			}
		}
		return this;
	}

	/**
	 * 要素(値付き)の書き込み
	 * 
	 * @param localName
	 * @param value
	 * @throws XMLStreamException
	 */
	public StaxWriter writeCData(final String localName, String value)
			throws XMLStreamException {
		value = handleValue(null, null, localName, value);
		if (value != null) {
			writeStartElement(localName);
			this.writeCData(value);
			writeEndElement();
		} else {
			if (writeEmptyTag) {
				writeEmptyElement(localName);
			}
		}
		return this;
	}

	/**
	 * 要素(値付き)の書き込み
	 * 
	 * @param localName
	 * @param value
	 * @throws XMLStreamException
	 */
	public StaxWriter writeElement(final String localName, Object value)
			throws XMLStreamException {
		value = handleValue(null, null, localName, value);
		if (value != null) {
			writeStartElement(localName);
			writeCharacters(value);
			writeEndElement();
		} else {
			if (writeEmptyTag) {
				writeEmptyElement(localName);
			}
		}
		return this;
	}

	/**
	 * 要素(値付き)の書き込み
	 * 
	 * @param localName
	 * @param values
	 * @throws XMLStreamException
	 */
	public StaxWriter writeElement(final String localName, List<String> values)
			throws XMLStreamException {
		values = handleValue(null, null, localName, values);
		if (!isEmpty(values)) {
			writeStartElement(localName);
			final SeparatedStringBuilder builder = new SeparatedStringBuilder("\n");
			builder.add(values);
			writeCharacters(builder.toString());
			writeEndElement();
		} else {
			if (writeEmptyTag) {
				writeEmptyElement(localName);
			}
		}
		return this;
	}

	/**
	 * 要素(値付き)の書き込み
	 * 
	 * @param localName
	 * @param value
	 * @throws XMLStreamException
	 */
	public StaxWriter writeElement(final String localName, DbInfo value)
			throws XMLStreamException {
		value = handleValue(null, null, localName, value);
		if (value == null || value.isEmpty()) {
			if (writeEmptyTag) {
				writeEmptyElement(localName);
			}
		} else {
			writeStartElement(localName);
			for (final Map.Entry<String, String> entry : value
					.entrySet()) {
				writeElement(ENTRY_ELEMENT, ()->{
					final String key = entry.getKey();
					final Object val = entry.getValue();
					writeAttribute(KEY_ELEMENT, key);
					if (isEmpty(val)) {
					} else {
						writeAttribute(VALUE_ELEMENT, (String) val);
					}
				});
			}
			writeEndElement();
		}
		return this;
	}

	protected StaxWriter write(final Object obj) throws XMLStreamException {
		if (obj instanceof List<?>) {
			return write((List<?>) obj);
		} else if (obj instanceof Set<?>) {
			return write((Set<?>) obj);
		} else if (obj instanceof Map<?, ?>) {
			return write((Map<?, ?>) obj);
		}
		return writeCharacters(obj);
	}

	protected StaxWriter write(final List<?> list) throws XMLStreamException {
		writeCollection(LIST_ELEMENT, list);
		return this;
	}

	protected StaxWriter write(final Set<?> set) throws XMLStreamException {
		writeCollection(SET_ELEMENT, set);
		return this;
	}

	/**
	 * Map要素(値付き)の書き込み
	 * 
	 * @param map
	 * @throws XMLStreamException
	 */
	public StaxWriter write(final Map<?, ?> map) throws XMLStreamException {
		if (map != null) {
			writeStartElement(MAP_ELEMENT);
			for (final Map.Entry<?, ?> entry : map.entrySet()) {
				writeStartElement(ENTRY_ELEMENT);
				writeAttribute(KEY_ELEMENT, entry.getKey());
				writeAttribute(VALUE_ELEMENT, entry.getValue());
				writeEndElement();
			}
			writeEndElement();
		}
		return this;
	}

	/**
	 * コレクションの書き込み
	 * 
	 * @param localName
	 * @param c
	 * @throws XMLStreamException
	 */
	protected void writeCollection(final String localName, Collection<?> c)
			throws XMLStreamException {
		c = handleValue(null, null, localName, c);
		writeStartElement(localName);
		for (final Object val : c) {
			if (val == null) {
				writeEmptyElement(VALUE_ELEMENT);
			} else {
				writeStartElement(VALUE_ELEMENT);
				writeCharacters(val);
				writeEndElement();
			}
		}
		writeEndElement();
	}

	/**
	 * Set要素(値付き)の書き込み
	 * 
	 * @param localName
	 * @param set
	 * @throws XMLStreamException
	 */
	public StaxWriter writeElement(final String localName, Set<?> set)
			throws XMLStreamException {
		set = handleValue(null, null, localName, set);
		if (set == null) {
			if (writeEmptyTag) {
				writeEmptyElement(localName);
			}
		} else {
			writeStartElement(localName);
			writeCollection(SET_ELEMENT, set);
			writeEndElement();
		}
		return this;
	}

	/**
	 * Set要素(値付き)の書き込み
	 * 
	 * @param localName
	 * @param c
	 * @throws XMLStreamException
	 */
	public StaxWriter writeElementValues(final String localName, Collection<?> c)
			throws XMLStreamException {
		c = handleValue(null, null, localName, c);
		if (c == null) {
			if (writeEmptyTag) {
				writeEmptyElement(localName);
			}
		} else {
			writeStartElement(localName);
			for(final Object obj:c){
				writeElement("value", obj);
			}
			writeEndElement();
		}
		return this;
	}

	
	/**
	 * Map要素(値付き)の書き込み
	 * 
	 * @param localName
	 * @param map
	 * @throws XMLStreamException
	 */
	public StaxWriter writeElement(final String localName, Map<?, ?> map)
			throws XMLStreamException {
		map = handleValue(null, null, localName, map);
		if (map != null) {
			writeStartElement(localName);
			write(map);
			writeEndElement();
		} else {
			if (writeEmptyTag) {
				writeEmptyElement(localName);
			}
		}
		return this;
	}

	/**
	 * @return the writeEmptyTag
	 */
	public boolean isWriteEmptyTag() {
		return writeEmptyTag;
	}

	/**
	 * @param writeEmptyTag
	 *            the writeEmptyTag to set
	 */
	public StaxWriter setWriteEmptyTag(final boolean writeEmptyTag) {
		this.writeEmptyTag = writeEmptyTag;
		return this;
	}

	/**
	 * @return the writeAutoStartDocument
	 */
	public boolean isWriteAutoStartDocument() {
		return writeAutoStartDocument;
	}

	/**
	 * @param writeAutoStartDocument the writeAutoStartDocument to set
	 */
	public StaxWriter setWriteAutoStartDocument(final boolean writeAutoStartDocument) {
		this.writeAutoStartDocument = writeAutoStartDocument;
		return this;
	}

	/**
	 * 改行の書き込み
	 * 
	 * @throws XMLStreamException
	 */
	public StaxWriter newLine() throws XMLStreamException {
		this.writeStartDocument();
		return writeCharacters(lineSeparator);
	}

	/**
	 * @return the lineSeparator
	 */
	public String getLineSeparator() {
		return lineSeparator;
	}

	/**
	 * @param lineSeparator
	 *            the lineSeparator to set
	 */
	public StaxWriter setLineSeparator(final String lineSeparator) {
		this.lineSeparator = lineSeparator;
		return this;
	}

	/**
	 * @return the indentLevel
	 */
	public int getIndentLevel() {
		return indentLevel;
	}

	/**
	 * @param indentLevel
	 *            the indentLevel to set
	 */
	public StaxWriter setIndentLevel(final int indentLevel) {
		if (indentLevel >= 0) {
			this.indentLevel = indentLevel;
			this.currentIndentString = getString(this.indentString, indentLevel);
		}
		return this;
	}

	/**
	 * インデントレベルの追加
	 * 
	 * @param addCount
	 *            追加するインデントレベル
	 */
	public StaxWriter addIndentLevel(final int addCount) {
		setIndentLevel(this.getIndentLevel() + addCount);
		return this;
	}

	/**
	 * インデントレベルの追加
	 * 
	 * @param addCount
	 *            追加するインデントレベル
	 */
	public StaxWriter indent(final int addCount, final RunWithException run) throws XMLStreamException{
		addIndentLevel(addCount);
		run.run();
		addIndentLevel(-addCount);
		return this;
	}

	/**
	 * インデントレベルの追加
	 * 
	 * @param run
	 *            実行する処理
	 */
	public StaxWriter indent(final RunWithException run) throws XMLStreamException{
		return indent(1, run);
	}

	/**
	 * @return the indentString
	 */
	public String getIndentString() {
		return indentString;
	}

	/**
	 * @param indentString
	 *            the indentString to set
	 */
	public StaxWriter setIndentString(final String indentString) {
		this.indentString = indentString;
		return setIndentLevel(this.getIndentLevel());
	}

	/**
	 * インデントの出力
	 * 
	 * @throws XMLStreamException
	 */
	public StaxWriter indent() throws XMLStreamException {
		writeCharacters(this.currentIndentString);
		return this;
	}

	/**
	 * 書き込み時の値のハンドラー
	 * 
	 * @author tatsuo satoh
	 * 
	 */
	public static interface ValueHandler {
		Object convert(String prefix, String namespaceURI, String localName,
				Object value);
	}

	/**
	 * 書き込まれた全要素数を返します
	 * 
	 */
	public long getWriteCount() {
		return this.writeAttributeCount + this.writeCommentCount
				+ this.writeElementCount + this.writeValueCount;
	}
	
	@FunctionalInterface
	public static interface RunWithException{
		void run() throws XMLStreamException;
	}
}
