/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-postgres.
 *
 * sqlapp-core-postgres is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-postgres is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-postgres.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.sqlapp.data.db.dialect.postgres.util;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.resolver.PostgresDialectResolver.PostgresVersionResolver;
import com.sqlapp.data.schemas.AbstractColumn;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.util.AbstractSqlBuilder;

/**
 * Postgres用のSQLビルダー
 * 
 * @author tatsuo satoh
 * 
 */
public class PostgresSqlBuilder extends AbstractSqlBuilder<PostgresSqlBuilder> {

	public PostgresSqlBuilder(Dialect dialect) {
		super(dialect);
	}

	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = 1L;

	private PostgresVersionResolver postgresVersionResolver=new PostgresVersionResolver();

	private Dialect postgres92=postgresVersionResolver.getDialect(9, 2, 0);
	
	/**
	 * カラムの型の定義を追加します
	 * 
	 * @param column
	 *            カラム
	 */
	@Override
	protected PostgresSqlBuilder typeDefinition(
			Column column) {
		if (column.isIdentity() && getDialect().supportsIdentity()) {
			if (column.getDataType() == DataType.SMALLINT&&this.getDialect().compareTo(postgres92)>=0) {
				_add("smallserial");
			} else if (column.getDataType() == DataType.INT) {
				_add("serial");
			} else if (column.getDataType() == DataType.BIGINT) {
				_add("bigserial");
			} else if (column.getDataType()==null) {
				return super.typeDefinition(column);
			} else if (column.getDataType().isNumeric()) {
				_add("serial");
			} else {
				return super.typeDefinition(column);
			}
		} else {
			return super.typeDefinition(column);
		}
		return this;
	}
	
	/**
	 * 変更時のカラムのNOT NULL定義を追加します
	 * 
	 * @param column
	 */
	@Override
	protected PostgresSqlBuilder notNullDefinitionForAlter(Column column) {
		if (column.isNotNull()) {
			space().set().not()._null();
		}
		return instance();
	}
	
	public PostgresSqlBuilder search() {
		appendElement("SEARCH");
		return instance();
	}

	public PostgresSqlBuilder path() {
		appendElement("PATH");
		return instance();
	}

	public PostgresSqlBuilder operator() {
		appendElement("OPERATOR");
		return instance();
	}
	
	public PostgresSqlBuilder _class() {
		operator().appendElement("CLASS");
		return instance();
	}

	public PostgresSqlBuilder using() {
		appendElement("USING");
		return instance();
	}

	public PostgresSqlBuilder gist() {
		appendElement("GIST");
		return instance();
	}

	public PostgresSqlBuilder exclude() {
		appendElement("EXCLUDE");
		return instance();
	}
	
	public PostgresSqlBuilder conflict() {
		appendElement("CONFLICT");
		return instance();
	}
	
	public PostgresSqlBuilder immutable() {
		appendElement("IMMUTABLE");
		return instance();
	}

	public PostgresSqlBuilder stable() {
		appendElement("STABLE");
		return instance();
	}
	
	public PostgresSqlBuilder _volatile() {
		appendElement("VOLATILE");
		return instance();
	}
	
	public PostgresSqlBuilder _do() {
		appendElement("DO");
		return instance();
	}

	public PostgresSqlBuilder comment() {
		appendElement("COMMENT");
		return instance();
	}
	
	public PostgresSqlBuilder fillfactor() {
		appendElement("FILLFACTOR");
		return instance();
	}

	public PostgresSqlBuilder oids() {
		appendElement("OIDS");
		return instance();
	}
	
	public PostgresSqlBuilder binding() {
		appendElement("BINDING");
		return instance();
	}

	public PostgresSqlBuilder leftarg() {
		appendElement("LEFTARG");
		return instance();
	}

	public PostgresSqlBuilder rightarg() {
		appendElement("RIGHTARG");
		return instance();
	}

	public PostgresSqlBuilder commutator() {
		appendElement("COMMUTATOR");
		return instance();
	}

	public PostgresSqlBuilder negator() {
		appendElement("NEGATOR");
		return instance();
	}

	public PostgresSqlBuilder restrict() {
		appendElement("RESTRICT");
		return instance();
	}

	public PostgresSqlBuilder hashes() {
		appendElement("HASHES");
		return instance();
	}

	public PostgresSqlBuilder merges() {
		appendElement("MERGES");
		return instance();
	}
	
	protected PostgresSqlBuilder autoIncrement(AbstractColumn<?> column) {
		return instance();
	}
	
	@Override
	public PostgresSqlBuilder clone(){
		return (PostgresSqlBuilder)super.clone();
	}
}
