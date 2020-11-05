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

import static com.sqlapp.util.CommonUtils.isEmpty;

import com.sqlapp.data.schemas.Trigger;
import com.sqlapp.util.AbstractSqlBuilder;
import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.SeparatedStringBuilder;

/**
 * Create Trigger生成クラス
 * 
 * @author satoh
 * 
 */
public abstract class AbstractCreateTriggerFactory<S extends AbstractSqlBuilder<?>>
		extends AbstractCreateNamedObjectFactory<Trigger, S> {

	@Override
	protected void addCreateObject(final Trigger obj, S builder) {
		if (!isEmpty(obj.getDefinition())) {
			builder._add(obj.getDefinition());
		} else {
			builder.create().trigger();
			builder.name(obj, this.getOptions().isDecorateSchemaName());
			addCreateObjectDetail(obj, builder);
		}
	}

	protected void addCreateObjectDetail(final Trigger obj, S builder) {
		addEventManipulationText(obj, builder);
		if (obj.getTableName()!=null){
			builder.lineBreak();
			builder.on();
			if (!CommonUtils.eq(obj.getSchemaName(), obj.getTableSchemaName())) {
				builder.name(obj.getTableSchemaName());
				builder._add(".");
			}
			builder.name(obj.getTableName());
		}
		addCreateTriggerBody(obj, builder);
	}

	protected void addEventManipulationText(final Trigger obj, S builder) {
		SeparatedStringBuilder sepBuilder = new SeparatedStringBuilder(" OR ");
		sepBuilder.add(obj.getEventManipulation());
		if (obj.getActionTiming()!=null){
			builder.lineBreak();
			builder._add(obj.getActionTiming());
		}
		if (!CommonUtils.isEmpty(obj.getEventManipulation())){
			builder.lineBreak();
			builder.space()._add(sepBuilder.toString());
		}
	}

	protected void addCreateTriggerBody(final Trigger obj, S builder) {
		builder.lineBreak();
		if (obj.getActionOrientation()==null){
			builder.forEach().row();
		} else{
			builder.forEach().space()._add(obj.getActionOrientation());
		}
		if (!CommonUtils.isEmpty(obj.getWhen())){
			addWhen(obj, builder);
		}
		builder.lineBreak();
		builder._add(toString(obj.getStatement()));
	}
	
	protected void addWhen(final Trigger obj, S builder){
		
	}

}
