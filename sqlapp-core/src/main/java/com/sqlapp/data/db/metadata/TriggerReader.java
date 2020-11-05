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
package com.sqlapp.data.db.metadata;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.schemas.DbObjects;
import com.sqlapp.data.schemas.SchemaObjectProperties;
import com.sqlapp.data.schemas.Trigger;

public abstract class TriggerReader extends AbstractSchemaObjectReader<Trigger>{

	protected static final String EVENT_MANIPULATION="EVENT_MANIPULATION";
	protected static final String EVENT_OBJECT_CATALOG="EVENT_OBJECT_CATALOG";
	protected static final String EVENT_OBJECT_SCHEMA="EVENT_OBJECT_SCHEMA";
	protected static final String EVENT_OBJECT_TABLE="EVENT_OBJECT_TABLE";
	protected static final String ACTION_CONDITION="ACTION_CONDITION";
	protected static final String ACTION_STATEMENT="ACTION_STATEMENT";
	protected static final String ACTION_TIMING="ACTION_TIMING";
	protected static final String ACTION_ORIENTATION="ACTION_ORIENTATION";
	protected static final String ACTION_REFERENCE_OLD_ROW="ACTION_REFERENCE_OLD_ROW";
	protected static final String ACTION_REFERENCE_NEW_ROW="ACTION_REFERENCE_NEW_ROW";
	
	protected TriggerReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected SchemaObjectProperties getSchemaObjectProperties(){
		return SchemaObjectProperties.TRIGGERS;
	}
	
	/* (non-Javadoc)
	 * @see com.sqlapp.data.db.dialect.metadata.AbstractNamedMetadataFactory#getNameLabel()
	 */
	@Override
	protected String getNameLabel(){
		return DbObjects.TRIGGER.getCamelCaseNameLabel();
	}
}
