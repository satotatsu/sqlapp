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

package com.sqlapp.data.parameter;
/**
 * パラメタが見つからない場合の例外
 * @author satoh
 *
 */
public class ParameterNotFoundException extends RuntimeException{
	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = 6432827087051957164L;
	/**
	 * デフォルトコンストラクタ
	 */
	public ParameterNotFoundException(){
		
	}

	/**
	 * コンストラクタ
	 * @param message エラーメッセージ
	 */
    public ParameterNotFoundException(String message) {
		super(message);
    }
}
