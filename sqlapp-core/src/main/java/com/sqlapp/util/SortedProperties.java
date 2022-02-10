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

package com.sqlapp.util;

import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.Vector;

public class SortedProperties extends Properties{
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	private Comparator<String> comparator;
	
	public SortedProperties(){
		this.comparator=new StringComparator();
	}
	
	public SortedProperties(Comparator<String> comparator){
		this.comparator=comparator;
	}
	
	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Override
	public synchronized Enumeration<Object> keys() {
		Enumeration<Object> keysEnum = super.keys();
		Vector keyList = new Vector();
		while(keysEnum.hasMoreElements()){
			keyList.add(keysEnum.nextElement());
		}
		Collections.sort(keyList, comparator);
		return keyList.elements();
	}

	static class StringComparator implements Comparator<String>{

		@Override
		public int compare(String o1, String o2) {
			String[] split1=o1.split("\\.");
			String[] split2=o2.split("\\.");
			if (split1.length>split2.length){
				return 1;
			} else if (split1.length<split2.length){
				return -1;
			}
			return o1.compareTo(o2);
		}
		
	}

	@Override
	public Set<Map.Entry<Object,Object>> entrySet(){
		Map<Object,Object> map=new LinkedHashMap<>();
		Enumeration<Object> keysEnum = keys();
		while(keysEnum.hasMoreElements()){
			Object key=keysEnum.nextElement();
			map.put(key, this.get(key));
		}
		return map.entrySet();
	}
}
