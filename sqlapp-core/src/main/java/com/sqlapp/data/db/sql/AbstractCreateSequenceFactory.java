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

import com.sqlapp.data.schemas.Sequence;
import com.sqlapp.util.AbstractSqlBuilder;

/**
 * Create Sequence生成クラス
 * 
 * @author satoh
 * 
 */
public abstract class AbstractCreateSequenceFactory<S extends AbstractSqlBuilder<?>>
		extends AbstractCreateNamedObjectFactory<Sequence, S> {

	@Override
	protected void addCreateObject(final Sequence obj, S builder) {
		addCreateSequence(obj, builder);
		addIfNotExists(obj,builder);
		builder.name(obj, this.getOptions().isDecorateSchemaName());
		if (obj.getDataType()!=null||obj.getDataTypeName()!=null){
			addDataType(obj, builder);
		}
		if (obj.getStartValue()!=null){
			addStartWith(obj, builder);
		}
		if (obj.getIncrementBy()!=null){
			addIncrementBy(obj, builder);
		}
		if (obj.getMaxValue()!=null){
			addMaxValue(obj, builder);
		}
		if (obj.getMinValue()!=null){
			addMinValue(obj, builder);
		}
		addCycle(obj, builder);
		if (obj.getCacheSize()!=null){
			addCache(obj, builder);
		}
		addOptions(obj, builder);
	}

	protected void addCreateSequence(final Sequence obj, S builder){
		builder.create().sequence();
	}
	
	protected void addIfNotExists(final Sequence obj, S builder){
	}

	protected void addDataType(final Sequence obj, S builder){
		builder.as().space().typeDefinition(obj.getDataType(), obj.getDataTypeName(), obj.getPrecision(), obj.getScale());
	}

	protected void addStartWith(final Sequence obj, S builder){
		builder.start().with().space()._add(obj.getStartValue());
	}

	protected void addIncrementBy(final Sequence obj, S builder){
		builder.increment().by().space()._add(obj.getIncrementBy());
	}

	protected void addMaxValue(final Sequence obj, S builder){
		builder.maxvalue().space()._add(obj.getMaxValue());
	}

	protected void addMinValue(final Sequence obj, S builder){
		builder.minvalue().space()._add(obj.getMinValue());
	}

	protected void addCycle(final Sequence obj, S builder){
		if (obj.isCycle()){
			builder.cycle();
		}
	}
	
	protected void addCache(final Sequence obj, S builder){
		builder.cache().space()._add(obj.getCacheSize());
	}

	protected void addOrder(final Sequence obj, S builder){
		if (!obj.isOrder()){
			builder.noOrder();
		}
	}
	
	protected void addOptions(final Sequence obj, S builder){
	}

}
