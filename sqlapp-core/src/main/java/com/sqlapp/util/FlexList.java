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

import java.util.ArrayList;

public class FlexList<T> extends ArrayList<T>{

	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = 6878843914817997856L;
	
	/**
	 * 位置を指定して値を取得
	 */
	@Override
	public T get(int index){
		if (this.size()>index){
			return super.get(index);
		}
		return null;
	}
	

	/**
	 * 位置を指定して値をセット
	 */
	@Override
	public T set(int index, T val){
		if (this.size()>index){
			return super.set(index, val);
		}
		for(int i=this.size();i<index;i++){
			super.add(null);
		}
		super.add(val);
		return null;
	}
	
	@Override
    public void add(int index, T element) {
		if (this.size()>index){
	    	super.add(index, element);
	    	return;
		}
		for(int i=this.size();i<index;i++){
			super.add(null);
		}
    	super.add(index, element);
    	return;
    }
}
