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

package com.sqlapp.data.db.sql;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.sqlapp.jdbc.sql.AbstractSqlRegistry;

/**
 * SqlExecutor for Standard out
 * 
 * @author tatsuo satoh
 * 
 */
public class LoggerSqlExecutor extends AbstractSqlExecutor {
	protected static final Logger loggger = LogManager
			.getLogger(AbstractSqlRegistry.class);
	@Override
	protected void write(final String value){
		loggger.info(value);
	}

}
