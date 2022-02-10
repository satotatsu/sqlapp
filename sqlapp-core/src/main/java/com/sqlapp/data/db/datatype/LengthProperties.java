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

package com.sqlapp.data.db.datatype;


public interface LengthProperties<T> {

	/**
	 * Default Lengthを取得します
	 * @return Default Length
	 */
	Long getDefaultLength();

	/**
	 * Default Lengthを設定します
	 * @param defaultLength
	 */
	T setDefaultLength(Long defaultLength);

	/**
	 * MAX Lengthを取得します
	 * @return MAX Length
	 */
	Long getMaxLength();

	/**
	 * MAX Lengthを設定します
	 * @param defaultLength
	 */
	T setMaxLength(Long maxLength);

	/**
	 * 最大値とデフォルト値を考慮したLengthを取得します
	 * @param Length
	 * @return 最大値とデフォルト値を考慮したLength
	 */
	long getLength(final Long length);
}
