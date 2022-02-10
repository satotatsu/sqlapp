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

package com.sqlapp.data.schemas.properties;

import java.util.Map;

import com.sqlapp.data.schemas.DbInfo;

public interface StatisticsProperty<T> {
	/**
	 * get Statistics
	 * 
	 * @return the Statistics
	 */
	DbInfo getStatistics();

	/**
	 * set Statistics
	 * 
	 * @param Statistics
	 *            the Statistics to set
	 */
	T setStatistics(DbInfo value);
	
	/**
	 * set Specifics
	 * 
	 * @param Specifics
	 *            the Specifics to set
	 */
	@SuppressWarnings("unchecked")
	default T setStatistics(Map<?,?> value){
		if (value==null){
			return setStatistics((DbInfo)null);
		} else{
			if (this.getStatistics()==null){
				return setStatistics(new DbInfo());
			}
			value.forEach((k,v)->{
				getStatistics().put(k==null?null:k.toString(), v==null?null:v.toString());
			});
			return (T)this;
		}
	}

}
