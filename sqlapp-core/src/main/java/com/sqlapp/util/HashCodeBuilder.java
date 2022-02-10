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

import java.io.Serializable;
import java.util.Collection;
import java.util.List;
/**
 * ハッシュコードを作成するためのビルダークラス
 * @author satoh
 *
 */
public class HashCodeBuilder implements Serializable{

	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = 1752507174479245520L;

	private int multiplier =37;

	private int seed =53;
	/**
	 * 大文字小文字を区別します
	 */
	private boolean caseSensitive=true;

	public HashCodeBuilder(boolean caseSensitive){
		this(53, true);
	}

	public HashCodeBuilder(){
	}

	public HashCodeBuilder(int seed){
		this(seed, true);
	}

	public HashCodeBuilder(int seed, boolean caseSensitive){
		this.seed=seed;
		this.caseSensitive=caseSensitive;
	}
	
	public HashCodeBuilder append(int val){
		seed=seed*multiplier+val;
		return this;
	}
	
	private HashCodeBuilder append(){
		seed=seed*multiplier;
		return this;
	}

	public HashCodeBuilder append(int[] vals){
		for(int i=0;i<vals.length;i++){
			this.append(vals[i]);
		}
		return this;
	}

	public HashCodeBuilder append(Integer[] vals){
		for(int i=0;i<vals.length;i++){
			this.append(vals[i]);
		}
		return this;
	}
	
	public HashCodeBuilder append(Integer val){
		if (val==null){
			return append();
		}
		return append(val.intValue());
	}
	
	public HashCodeBuilder append(long val){
		seed=seed*multiplier+(int)(val ^ (val >>> 32));
		return this;
	}
	
	public HashCodeBuilder append(Long val){
		if (val==null){
			return append();
		}
		return append(val.longValue());
	}

	public HashCodeBuilder append(long[] vals){
		for(int i=0;i<vals.length;i++){
			this.append(vals[i]);
		}
		return this;
	}

	public HashCodeBuilder append(Long[] vals){
		for(int i=0;i<vals.length;i++){
			this.append(vals[i]);
		}
		return this;
	}

	public HashCodeBuilder append(short val){
		return append((int)(val));
	}
	
	public HashCodeBuilder append(Short val){
		if (val==null){
			return append();
		}
		return append(val.intValue());
	}

	public HashCodeBuilder append(short[] vals){
		for(int i=0;i<vals.length;i++){
			this.append(vals[i]);
		}
		return this;
	}

	public HashCodeBuilder append(Short[] vals){
		for(int i=0;i<vals.length;i++){
			this.append(vals[i]);
		}
		return this;
	}
	
	public HashCodeBuilder append(float val){
		return append(Float.floatToIntBits(val));
	}
	
	public HashCodeBuilder append(Float val){
		if (val==null){
			return append();
		}
		return append(val.floatValue());
	}

	public HashCodeBuilder append(float[] vals){
		for(int i=0;i<vals.length;i++){
			this.append(vals[i]);
		}
		return this;
	}

	public HashCodeBuilder append(Float[] vals){
		for(int i=0;i<vals.length;i++){
			this.append(vals[i]);
		}
		return this;
	}

	public HashCodeBuilder append(double val){
		return append(Double.doubleToLongBits(val));
	}
	
	public HashCodeBuilder append(Double val){
		if (val==null){
			return append();
		}
		return append(val.doubleValue());
	}

	public HashCodeBuilder append(double[] vals){
		for(int i=0;i<vals.length;i++){
			this.append(vals[i]);
		}
		return this;
	}

	public HashCodeBuilder append(Double[] vals){
		for(int i=0;i<vals.length;i++){
			this.append(vals[i]);
		}
		return this;
	}

	public HashCodeBuilder append(boolean val){
		return append(val?1:0);
	}

	public HashCodeBuilder append(Boolean val){
		if (val==null){
			return append();
		}
		return append(val.booleanValue());
	}

	public HashCodeBuilder append(boolean[] vals){
		for(int i=0;i<vals.length;i++){
			this.append(vals[i]);
		}
		return this;
	}

	public HashCodeBuilder append(Boolean[] vals){
		for(int i=0;i<vals.length;i++){
			this.append(vals[i]);
		}
		return this;
	}

	public HashCodeBuilder append(Object val){
		if (val==null){
			return append();
		}
		this.append(val.hashCode());
		return this;
	}

	public HashCodeBuilder append(Object[] vals){
		if (vals==null){
			return append();
		}
		for(int i=0;i<vals.length;i++){
			this.append(vals[i]);
		}
		return this;
	}

	public HashCodeBuilder append(Collection<Object> vals){
		if (vals==null){
			return append();
		}
		for(Object val:vals){
			this.append(val);
		}
		return this;
	}

	public HashCodeBuilder append(List<Object> vals){
		if (vals==null){
			return append();
		}
		int size=vals.size();
		for(int i=0;i<size;i++){
			this.append(vals.get(i));
		}
		return this;
	}
	
	public HashCodeBuilder append(String val){
		if (val==null){
			return append();
		}
		if (this.caseSensitive){
			return append(val.hashCode());
		} else{
			return append(val.toUpperCase().hashCode());
		}
	}

	public HashCodeBuilder append(String[] vals){
		for(int i=0;i<vals.length;i++){
			this.append(vals[i]);
		}
		return this;
	}
	
	@Override
	public int hashCode(){
		return seed;
	}
}
