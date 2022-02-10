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

package com.sqlapp.util.xml;

import static com.sqlapp.util.CommonUtils.list;

import java.util.List;

import javax.xml.stream.XMLStreamException;

import com.sqlapp.util.StaxReader;
import com.sqlapp.util.ToStringBuilder;

/**
 * 読み込み結果のオブジェクトを取得するためクラス
 * 
 * @author satoh
 * 
 */
public class ResultHandler extends AbstractStaxElementHandler {

	@SuppressWarnings("rawtypes")
	private List result = list();

	@Override
	public String getLocalName() {
		return null;
	}

	public ResultHandler() {
		this.registerChild(new EmptyTextSkipHandler());
	}

	public boolean match(StaxReader reader) {
		return true;
	}

	@SuppressWarnings("unchecked")
	@Override
	public void doCallback(StaxReader reader, StaxElementHandler child,
			String name, Object ownObject, Object childObject)
			throws XMLStreamException {
		result.add(childObject);
	}

	@Override
	protected void doHandle(StaxReader reader, Object parentObject)
			throws XMLStreamException {
		while (reader.hasNext()) {
			callChilds(reader, parentObject);
		}
	}

	/**
	 * @return the result
	 */
	@SuppressWarnings("unchecked")
	public <T> List<T> getResult() {
		return (List<T>) result;
	}

	/**
	 * @param result
	 *            the result to set
	 */
	public void setResult(List<Object> result) {
		this.result = result;
	}

	@Override
	public String toString() {
		ToStringBuilder builder = new ToStringBuilder(this.getClass());
		builder.add(result);
		return builder.toString();
	}
}
