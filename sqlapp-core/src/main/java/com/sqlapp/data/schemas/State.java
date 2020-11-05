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
package com.sqlapp.data.schemas;

import com.sqlapp.util.CommonUtils;

/**
 * 状態
 * 
 * @author SATOH
 * 
 */
public enum State {
	/**
	 * Unchanged
	 */
	Unchanged(){
		@Override
		public boolean isChanged() {
			return false;
		}
	},
	/**
	 * Added
	 */
	Added(){
		@Override
		public boolean isAdded(){
			return true;
		}
		@Override
		public State reverse(){
			return Deleted;
		}
	},
	/**
	 * Modified
	 */
	Modified(){
		@Override
		public boolean isModified(){
			return true;
		}
	},
	/**
	 * Deleted
	 */
	Deleted(){
		@Override
		public State reverse(){
			return Added;
		}
		@Override
		public boolean isDeleted(){
			return true;
		}
	},
	;

	private State() {
	}

	public boolean isAdded(){
		return false;
	}

	public boolean isDeleted(){
		return false;
	}

	public boolean isModified(){
		return false;
	}

	/**
	 * @return the changed
	 */
	public boolean isChanged() {
		return true;
	}
	
	public State reverse(){
		return this;
	}
	
	/**
	 * 2つのオブジェクトからStateを取得します。
	 * @param original 変更元のオブジェクト
	 * @param target 変更後のオブジェクト
	 * @return 対応するState
	 */
	public static State getState(Object original, Object target){
		if (!CommonUtils.eq(original, target)){
			if (original==null){
				return State.Added;
			}else if (target==null){
				return State.Deleted;
			} else{
				return State.Modified;
			}
		} else{
			return State.Unchanged;
		}
	}
	
}
