/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
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
 * along with sqlapp-command.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.sqlapp.data.db.command.version;

import com.sqlapp.exceptions.SqlappException;

public class SchemaVersionFailureException extends SqlappException{

	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = 4643001993780436210L;

	private final Long versionNo;

	public SchemaVersionFailureException(Long versionNo){
		super("versionNo="+versionNo);
		this.versionNo=versionNo;
	}

	/**
	 * @return the versionNo
	 */
	public Long getVersionNo() {
		return versionNo;
	}
	
}
