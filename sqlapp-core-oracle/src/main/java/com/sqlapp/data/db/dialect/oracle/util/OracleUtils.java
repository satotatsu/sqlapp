/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-oracle.
 *
 * sqlapp-core-oracle is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-oracle is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-oracle.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.sqlapp.data.db.dialect.oracle.util;

import java.util.Map;

import com.sqlapp.data.schemas.Difference;
import com.sqlapp.util.CommonUtils;

public class OracleUtils {
	private static String[] TABLE_STATISTICS_PROPERTIES=new String[]{
		"PCT_FREE"
		,"PCT_USED"
		,"INI_TRANS"
		,"MAX_TRANS"
		,"INITIAL_EXTENT"
		,"NEXT_EXTENT"
		,"MIN_EXTENTS"
		,"MAX_EXTENTS"
		,"PCT_INCREASE"
		,"FREELISTS"
		,"FREELIST_GROUPS"
		,"LOGGING"
		,"COMPRESS_FOR"
		,"INTERVAL"
		,"BUFFER_POOL"
	};
	

	private static String[] TABLE_STORAGE_PROPERTIES=new String[]{
		"INITIAL_EXTENT"
		,"NEXT_EXTENT"
		,"MIN_EXTENTS"
		,"MAX_EXTENTS"
		,"FREELISTS"
		,"FREELIST_GROUPS"
		,"BUFFER_POOL"
	};
	
	public static String[] getTableStatisticsKeys(){
		return TABLE_STATISTICS_PROPERTIES;
	}

	public static String[] getTableStorageKeys(){
		return TABLE_STORAGE_PROPERTIES;
	}

	
	public Map<String, Difference<?>> getAll(Map<String, Difference<?>> allDiff, String... args){
		Map<String, Difference<?>> result=CommonUtils.map();
		for(String arg:args){
			Difference<?> diff=allDiff.get(arg);
			if (diff!=null){
				result.put(arg, diff);
			}
		}
		return result;
	}
}
