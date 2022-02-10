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

package com.sqlapp.data.db.sql;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * Operationオプション
 * 
 * @author tatsuo satoh
 * 
 */
@Data
@EqualsAndHashCode(callSuper=true)
@ToString(callSuper=true)
public class Options extends AbstractBean {
	/**
	 * COMMIT
	 */
	private boolean outputCommit = false;

	/**
	 * DROP IF EXISTS
	 */
	private boolean dropIfExists = true;
	/**
	 * CREATE IF NOT EXISTS
	 */
	private boolean createIfNotExists = true;
	/**
	 * Schema Name Decoration
	 */
	private boolean decorateSchemaName = true;
	/**
	 * Set Search Path to Schema
	 */
	private boolean setSearchPathToSchema = true;
	
	private TableOptions tableOptions=new TableOptions();
	
	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Object#clone()
	 */
	@Override
	public Options clone() {
		final Options clone=new Options();
		clone.setOutputCommit(outputCommit);
		clone.setDropIfExists(dropIfExists);
		clone.setCreateIfNotExists(createIfNotExists);
		clone.setDecorateSchemaName(decorateSchemaName);
		clone.setSetSearchPathToSchema(setSearchPathToSchema);
		if (this.tableOptions!=null) {
			clone.setTableOptions(this.tableOptions.clone());
		}
		return clone;
	}
}
