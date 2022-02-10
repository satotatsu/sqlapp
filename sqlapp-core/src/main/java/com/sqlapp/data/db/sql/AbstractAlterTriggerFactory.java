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

import java.util.List;
import java.util.Map;

import com.sqlapp.data.schemas.DbObjectDifference;
import com.sqlapp.data.schemas.Difference;
import com.sqlapp.data.schemas.Trigger;
import com.sqlapp.util.AbstractSqlBuilder;
import com.sqlapp.util.CommonUtils;

/**
 * Alter Trigger Factory
 * 
 * @author satoh
 * 
 */
public abstract class AbstractAlterTriggerFactory<S extends AbstractSqlBuilder<?>>
		extends AbstractAlterNamedObjectFactory<S> {

	@Override
	protected List<SqlOperation> doCreateDiffSql(DbObjectDifference difference, Map<String, Difference<?>> allDiff){
		List<SqlOperation> list=CommonUtils.list();
		if (difference.getOriginal()!=null){
			list.addAll(this.getSqlFactoryRegistry().createSql((Trigger)difference.getOriginal(), SqlType.DROP));
		}
		if (difference.getTarget()!=null){
			list.addAll(this.getSqlFactoryRegistry().createSql((Trigger)difference.getTarget(), SqlType.CREATE));
		}
		return list;
	}
	
}
