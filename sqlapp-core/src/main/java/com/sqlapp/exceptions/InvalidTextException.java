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

package com.sqlapp.exceptions;
/**
 * Text parse error
 * @author SATOH
 *
 */
public class InvalidTextException extends SqlappException {

	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = -9041345051937881992L;
	
    public InvalidTextException(String line,int lineNo, int pos, String message) {
		super(createMessage(line, lineNo, pos, message));
    }

    public InvalidTextException(String line,int lineNo, String message) {
		super(createMessage(line, lineNo, message));
    }

    
    private static String createMessage(String line,int lineNo, int pos, String message){
    	return message+" [lineNo="+lineNo+", pos="+pos+",line="+ line+"]";
    }
    
    private static String createMessage(String line,int lineNo, String message){
    	return message+" [lineNo="+lineNo+",line="+ line+"]";
    }
}
