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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Serializable;
import java.io.Writer;
import java.util.function.Predicate;

import javax.xml.stream.XMLStreamException;

import com.sqlapp.util.StaxWriter;

/**
 * DBオブジェクトIF
 * 
 * @author 竜夫
 * 
 * @param <T>
 */
public interface DbCommonObject<T extends DbCommonObject<?>> extends
		Serializable, Cloneable {
	/**
	 * 指定したハンドラーでオブジェクトと等しいかを判定します
	 * 
	 * @param obj
	 * @param equalsHandler
	 */
	boolean equals(Object obj, EqualsHandler equalsHandler);

	/**
	 * 自オブジェクトの祖先となるオブジェクトをクラス指定で取得します。
	 * 
	 * @param clazz
	 *            祖先となるオブジェクト
	 * @return 祖先となるオブジェクト
	 */
	default <S> S getAncestor(Class<S> clazz){
		return getAncestor(obj->obj.getClass()==clazz);
	}

	/**
	 * 自オブジェクトの祖先となるオブジェクトをPredicate指定で取得します。
	 * 
	 * @param predicate
	 *            祖先となるオブジェクトを判定するPredicate
	 * @return 祖先となるオブジェクト
	 */
	@SuppressWarnings("unchecked")
	default <S> S getAncestor(Predicate<DbCommonObject<?>> predicate){
		if (!(this instanceof HasParent)){
			return null;
		}
		HasParent<? extends DbCommonObject<?>> hasParent=(HasParent<? extends DbCommonObject<?>>)this;
		DbCommonObject<?> parent=hasParent.getParent();
		while(true){
			if (parent == null) {
				return null;
			}
			if (predicate.test(parent)) {
				return (S) parent;
			}
			if (!(parent instanceof HasParent)){
				return null;
			}
			hasParent=(HasParent<? extends DbCommonObject<?>>)parent;
			parent=hasParent.getParent();
		}
	}
	

	/**
	 * ReaderからXMLを読み込みます
	 * 
	 * @param stream
	 * @throws XMLStreamException
	 */
	default void loadXml(Reader reader) throws XMLStreamException{
		loadXml(reader, null);
	}
	/**
	 * ReaderからXMLを読み込みます
	 * 
	 * @param stream
	 * @throws XMLStreamException
	 */
	void loadXml(Reader reader, XmlReaderOptions options) throws XMLStreamException;

	/**
	 * ストリームからXMLを読み込みます
	 * 
	 * @param stream
	 * @throws XMLStreamException
	 */
	default void loadXml(InputStream stream) throws XMLStreamException{
		loadXml(stream, null);
	}
	/**
	 * ストリームからXMLを読み込みます
	 * 
	 * @param stream
	 * @throws XMLStreamException
	 */
	void loadXml(InputStream stream, XmlReaderOptions options) throws XMLStreamException;

	/**
	 * 指定したパスからXMLを読み込みます
	 * 
	 * @param path
	 *            ファイルパス
	 * @throws XMLStreamException
	 * @throws FileNotFoundException
	 */
	default void loadXml(String path) throws XMLStreamException, FileNotFoundException{
		loadXml(path, null);
	}
	/**
	 * 指定したパスからXMLを読み込みます
	 * 
	 * @param path
	 *            ファイルパス
	 * @throws XMLStreamException
	 * @throws FileNotFoundException
	 */
	void loadXml(String path, XmlReaderOptions options) throws XMLStreamException, FileNotFoundException;

	/**
	 * 指定したパスからXMLを読み込みます
	 * 
	 * @param file
	 *            ファイルパス
	 * @throws XMLStreamException
	 * @throws FileNotFoundException
	 */
	default void loadXml(File file) throws XMLStreamException, IOException{
		loadXml(file, null);
	}
	/**
	 * 指定したパスからXMLを読み込みます
	 * 
	 * @param file
	 *            ファイルパス
	 * @throws XMLStreamException
	 * @throws FileNotFoundException
	 */
	void loadXml(File file, XmlReaderOptions options) throws XMLStreamException, IOException;
	/**
	 * 指定したパスにXMLとして書き込みます
	 * 
	 * @param path
	 * @throws XMLStreamException
	 * @throws FileNotFoundException
	 */
	void writeXml(String path) throws XMLStreamException, IOException;

	/**
	 * 指定したパスにXMLとして書き込みます
	 * 
	 * @param file
	 * @throws XMLStreamException
	 * @throws FileNotFoundException
	 */
	void writeXml(File file) throws XMLStreamException, IOException;

	/**
	 * ストリームにXMLとして書き込みます
	 * 
	 * @param stream
	 * @throws XMLStreamException
	 */
	void writeXml(OutputStream stream) throws XMLStreamException;

	/**
	 * WriterにXMLとして書き込みます
	 * 
	 * @param writer
	 * @throws XMLStreamException
	 */
	void writeXml(Writer writer) throws XMLStreamException;

	/**
	 * StaxWriterにXMLとして書き込みます
	 * 
	 * @param staxWriter
	 * @throws XMLStreamException
	 */
	void writeXml(StaxWriter staxWriter) throws XMLStreamException;

	/**
	 * 簡易形式の文字列表現を取得します
	 * 
	 */
	String toStringSimple();

}
