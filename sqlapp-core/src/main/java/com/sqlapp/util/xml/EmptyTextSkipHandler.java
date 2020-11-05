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

import static com.sqlapp.util.CommonUtils.isEmpty;

import javax.xml.stream.XMLStreamException;

import com.sqlapp.util.StaxReader;

/**
 * 空文字読み飛ばしクラス
 * 
 * @author satoh
 * 
 */
public class EmptyTextSkipHandler extends AbstractStaxElementHandler {

	@Override
	public String getLocalName() {
		return null;
	}

	@Override
	public boolean match(StaxReader reader) {
		if (reader.isWhiteSpace()) {
			return true;
		}
		if (reader.isCharacters()) {
			String text = reader.getText();
			if (isEmpty(text)) {
				return true;
			}
			for(int i=0;i<text.length();i++){
				if (!Character.isWhitespace(text.charAt(i))){
					return false;
				}
			}
			return false;
//			Matcher matcher = SPACE_PATTERN.matcher(text);
//			return matcher.matches();
		}
		return false;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.sqlapp.util.xml.AbstractStaxElementHandler#doHandle(com.sqlapp.util
	 * .StaxReader)
	 */
	@Override
	protected void doHandle(StaxReader reader, Object parentObject)
			throws XMLStreamException {
		if (reader.hasNext()) {
			reader.next();
		}
	}

	@Override
	public void doCallback(StaxReader reader, StaxElementHandler child,
			String name, Object ownObject, Object childObject)
			throws XMLStreamException {
	}
}
