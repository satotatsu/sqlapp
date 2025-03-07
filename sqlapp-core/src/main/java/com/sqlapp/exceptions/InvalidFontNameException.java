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

import com.sqlapp.util.FontUtils;
import com.sqlapp.util.OutputTextBuilder;

public class InvalidFontNameException extends CommandException{

	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = 4226531603314837670L;
	
	private String fontname;

	public InvalidFontNameException(String fontname) {
        super(createMessage(fontname));
		this.fontname=fontname;
    }

	private static String createMessage(String fontname){
		StringBuilder builder=new StringBuilder();
		builder.append("fontname="+fontname);
		builder.append("\n");
		builder.append("Available font=\n");
		OutputTextBuilder textBuilder=new OutputTextBuilder();
		textBuilder.append(FontUtils.getFontNamesAsTable());
		builder.append(textBuilder.toString());
		return builder.toString();
	}

	/**
	 * @return the fontname
	 */
	public String getFontname() {
		return fontname;
	}

	/**
	 * @param fontname the fontname to set
	 */
	public void setFontname(String fontname) {
		this.fontname = fontname;
	}


	
}
