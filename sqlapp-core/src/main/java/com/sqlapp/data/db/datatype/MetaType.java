/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
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
 * along with sqlapp-core.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.datatype;
/**
 * DBの型のメタタイプ
 * @author 竜夫
 *
 */
public enum MetaType {
	CHARACTER(){
		@Override
		public boolean isCharacter() {
			return true;
		}
	}
	, DATETIME(){
		@Override
		public boolean isDateTime() {
			return true;
		}
	}
	, INTERVAL(){
		@Override
		public boolean isInterval() {
			return true;
		}
	}
	, BINARY(){
		@Override
		public boolean isBinary() {
			return true;
		}
	}
	, NUMERIC(){
		@Override
		public boolean isNumeric() {
			return true;
		}
	}
	, OTHER(){
		@Override
		public boolean isOther() {
			return true;
		}
	};
	
	/**
	 * 数値型か?
	 */
	public boolean isNumeric() {
		return false;
	}

	/**
	 * 文字型か?
	 */
	public boolean isCharacter() {
		return false;
	}

	/**
	 * インターバル型か?
	 */
	public boolean isInterval() {
		return false;
	}

	/**
	 * バイナリ型か?
	 */
	public boolean isBinary() {
		return false;
	}

	/**
	 * DATETIME型か?
	 */
	public boolean isDateTime() {
		return false;
	}
	
	/**
	 * その他の型か?
	 */
	public boolean isOther() {
		return false;
	}
}
