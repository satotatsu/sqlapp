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
package com.sqlapp.data.db.dialect.resolver;

import java.io.Serializable;

import com.sqlapp.data.db.dialect.Dialect;

public interface VersionResolver extends Serializable {

	/**
	 * バージョン番号に従ってDialectを返す
	 * 
	 * @param dbProductName database product name
	 * @param majorVersion major version
	 * @param minorVersion minor version
	 * @return dialect
	 */
	Dialect getDialect(int majorVersion, int minorVersion, Integer revision);

}