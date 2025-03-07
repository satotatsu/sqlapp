/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-command.
 *
 * sqlapp-command is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-command is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-command.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.command.html;

import java.io.File;

import com.sqlapp.exceptions.SqlappException;

/**
 * DuplicatePropertyFilesException
 * 
 * @author SATOH
 *
 */
public class DuplicatePropertyFilesException extends SqlappException {

	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = -9041345051937881992L;

	public DuplicatePropertyFilesException(File... files) {
		super(createMessage(files));
	}

	private static String createMessage(File... files) {
		StringBuilder builder = new StringBuilder();
		builder.append("files=[");
		boolean first=true;
		for (File file : files) {
			if (!first){
				builder.append("\n,");
			}
			builder.append(file.getAbsolutePath());
			first=false;
		}
		builder.append("]");
		return builder.toString();
	}

}
