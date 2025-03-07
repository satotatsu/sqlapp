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

package com.sqlapp.exceptions;

import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Row;

public class InvalidValueException extends CommandException{

	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = 4226531603314837670L;
	
	private String key;
	
	private Object value;

	public InvalidValueException(String message, String detail, String key, Object value, Throwable t) {
        super(createMessage(message, detail, key, value), t);
        this.key=key;
        this.value=value;
    }

	public InvalidValueException(Row row, Column column, Object value, Throwable t) {
        super(createMessage(row.getDataSourceInfo(), row.getDataSourceDetailInfo(), row.getDataSourceRowNumber(), column.getName(), value), t);
        this.key=column.getName();
        this.value=value;
    }

	private static String createMessage(String message, String detail, Number lineNumber, Object key, Object value){
		StringBuilder builder=new StringBuilder();
		builder.append("message=");
		builder.append(message);
		if (detail!=null){
			builder.append(", detail=");
			builder.append(detail);
		}
		if (lineNumber!=null){
			builder.append(", lineNumber=");
			builder.append(lineNumber);
		}
		builder.append(", key=");
		builder.append(key);
		builder.append(", value=");
		builder.append(value);
		return builder.toString();
	}
	
	private static String createMessage(String message, String detail, Object key, Object value){
		return createMessage(message, detail, null, key, value);
	}

	/**
	 * @return the value
	 */
	public Object getValue() {
		return value;
	}

	/**
	 * @return the key
	 */
	public String getKey() {
		return key;
	}

	
}
