/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-firebird.
 *
 * sqlapp-core-firebird is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-firebird is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-firebird.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.sqlapp.data.db.dialect.firebird.util;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.resolver.FirebirdDialectResolver.FirebirdVersionResolver;
import com.sqlapp.data.schemas.AbstractColumn;
import com.sqlapp.util.AbstractSqlBuilder;

/**
 * Firebird用のSQLビルダー
 * 
 * @author tatsuo satoh
 * 
 */
public class FirebirdSqlBuilder extends AbstractSqlBuilder<FirebirdSqlBuilder> {

	public FirebirdSqlBuilder(Dialect dialect) {
		super(dialect);
	}

	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = 1L;

	/**
	 * GENERATOR句を追加します
	 * 
	 */
	public FirebirdSqlBuilder generator() {
		appendElement("GENERATOR");
		return instance();
	}

	/**
	 * RESTART句を追加します
	 * 
	 */
	public FirebirdSqlBuilder restart() {
		appendElement("RESTART");
		return instance();
	}

	/**
	 * ACTIVE句を追加します
	 * 
	 */
	public FirebirdSqlBuilder active() {
		appendElement("ACTIVE");
		return instance();
	}

	/**
	 * INACTIVE句を追加します
	 * 
	 */
	public FirebirdSqlBuilder inactive() {
		appendElement("INACTIVE");
		return instance();
	}

	/**
	 * POSITION句を追加します
	 * 
	 */
	public FirebirdSqlBuilder position() {
		appendElement("POSITION");
		return instance();
	}

	@Override
	protected FirebirdSqlBuilder autoIncrement(AbstractColumn<?> column) {
		FirebirdVersionResolver resolver = new FirebirdVersionResolver();
		Dialect dialect = resolver.getDialect(3, 0, 0);
		if (this.getDialect().compareTo(dialect) >= 0) {
			space().generated().by()._default().as().identity();
		}
		return instance();
	}

	@Override
	public FirebirdSqlBuilder clone() {
		return (FirebirdSqlBuilder) super.clone();
	}
}
