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
/**
 * ベンチマーク用のクラス
 * @author satoh
 *
 */
public abstract class AbstractBenchMark {
	private int count=10000;
	
	public AbstractBenchMark(){}
	public AbstractBenchMark(int count){
		this.count=count;
	}
	public long execute(){
		long start=System.currentTimeMillis();
		int size=getCount();
		for(int i=0;i<size;i++){
			handle();
		}
		long end=System.currentTimeMillis();
		return (end-start);
	}
	
	protected abstract void handle();
	
	protected int getCount(){
		return count;
	}
}
