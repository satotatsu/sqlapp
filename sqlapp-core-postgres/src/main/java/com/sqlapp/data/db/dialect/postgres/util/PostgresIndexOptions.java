/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
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
 * along with sqlapp-core-postgres.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.postgres.util;

import java.sql.SQLException;
import java.util.function.Consumer;
import java.util.function.Supplier;

import com.sqlapp.data.converter.Converters;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.postgres.Postgres;
import com.sqlapp.data.db.dialect.postgres.resolver.PostgresDialectResolver;
import com.sqlapp.data.schemas.Index;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.UniqueConstraint;
import com.sqlapp.data.schemas.properties.SpecificsProperty;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.EnumUtils;
import com.sqlapp.util.OnOffAutoType;
import com.sqlapp.util.OnOffType;

public enum PostgresIndexOptions {
	FILLFACTOR(){
		@Override
		public void setIndex(Index index, Object value) {
			Integer val=Converters.getDefault().convertObject(value, Integer.class);
			if (val==null) {
				index.getSpecifics().remove(getColumnKey());
				index.getSpecifics().remove(toString());
			}else if (val.intValue()>=0&&val.intValue()<=100) {
				index.getSpecifics().remove(getColumnKey());
				index.getSpecifics().remove(toString());
				if (val.intValue()!=0) {
					index.getSpecifics().put(this.toString(), val);
				}
			}
		}
		@Override
		public String getColumnKey() {
			return "FILLFACTOR";
		}
	},
	/**
	 * ッファリング構築技術をインデックスを構築する時に使用するかどうかを決定します
	 */
	BUFFERING() {
		@Override
		public OnOffAutoType getDefaultValue() {
			return OnOffAutoType.AUTO;
		}
		@Override
		public Class<?> getValueClass() {
			return OnOffAutoType.class;
		}
		@Override
		public void setIndex(Index index, Object value) {
			setIndexOnOffAutoParams(index, value);
		}
		@Override
		public void setIndex(final ExResultSet rs, Index index) throws SQLException {
			setOnOffAutoParams(rs, index);
		}
	},
	FASTUPDATE() {
		@Override
		public OnOffType getDefaultValue() {
			return OnOffType.ON;
		}
		@Override
		public Class<?> getValueClass() {
			return OnOffType.class;
		}
		@Override
		public void setIndex(Index index, Object value) {
			setIndexOnOffParams(index, value);
		}
		@Override
		public void setIndex(final ExResultSet rs, Index index) throws SQLException {
			setOnOffParams(rs, index);
		}
		@Override
		public void setUniqueConstraint(final ExResultSet rs, UniqueConstraint uc) throws SQLException {
			setOnOffParams(rs, uc);
		}
	},
	GIN_PENDING_LIST_LIMIT() {
	},
	PAGE_PER_RANGE() {
	},
	AUTOSUMMARISE() {
	},
	;

	public Class<?> getValueClass() {
		return null;
	}

	public Object getDefaultValue() {
		return null;
	}

	public void setIndex(Index index, Object value){
	}

	public void setTable(Table table, String value) {
	}

	public void setTable(final ExResultSet rs, Table table) throws SQLException {
		setParams(rs, val->{
			if (val!=null) {
				setTable(table, val.toString());
			} else {
				setTable(table, null);
			}
		});
	}

	public void setIndex(final ExResultSet rs, Index index) throws SQLException {	
		setParams(rs, val->{
			if (val!=null) {
				setIndex(index, val);
			} else {
				setIndex(index, null);
			}
		});
	}

	public static void setAllIndex(final ExResultSet rs, Index index) throws SQLException {	
		for(PostgresIndexOptions enm:PostgresIndexOptions.values()) {
			enm.setIndex(rs, index);
		}
	}
	
	public String getColumnKey() {
		return this.toString();
	}

	public void setUniqueConstraint(final ExResultSet rs, UniqueConstraint uk) throws SQLException {	
	}

	public static void setAllUniqueConstraint(final ExResultSet rs, UniqueConstraint uk) throws SQLException {	
		for(PostgresIndexOptions enm:PostgresIndexOptions.values()) {
			enm.setUniqueConstraint(rs, uk);
		}
	}
	
	protected void setIndexOnOffParams(Index index, Object value) {
		OnOffType enm=OnOffType.parse(value);
		index.getSpecifics().put(this.toString(), enm);
	}

	protected void setIndexOnOffAutoParams(Index index, Object value) {
		OnOffAutoType enm=OnOffAutoType.parse(value);
		index.getSpecifics().put(this.toString(), enm);
	}

	protected void setParams(final ExResultSet rs, Consumer<Object> cons) throws SQLException {
		if (!rs.contains(this.getColumnKey())) {
			return;
		}
		Object val = rs.getObject(this.getColumnKey());
		cons.accept(val);
	}

	public boolean supports(Postgres dialect) {
		Dialect target = getSupportVersion().get();
		return dialect.compareTo(target)>=0;
	}

	public Supplier<Dialect> getSupportVersion() {
		//デフォルトサポートを10にする
		return ()->PostgresDialectResolver.getInstance().getDialect(10, 0);
	}

	protected void setOnOffParams(final ExResultSet rs, SpecificsProperty<?> obj) throws SQLException {
		if (!rs.contains(this.toString())) {
			return;
		}
		boolean bool = rs.getBoolean(this.getColumnKey());
		OnOffType enm=OnOffType.parse(bool);
		obj.getSpecifics().put(this.toString(), enm);
	}

	protected void setOnOffAutoParams(final ExResultSet rs, SpecificsProperty<?> obj) throws SQLException {
		if (!rs.contains(this.toString())) {
			return;
		}
		boolean bool = rs.getBoolean(this.getColumnKey());
		OnOffAutoType enm=OnOffAutoType.parse(bool);
		obj.getSpecifics().put(this.toString(), enm);
	}

	public static PostgresIndexOptions parse(Object obj) {
		PostgresIndexOptions enm=EnumUtils.parse(PostgresIndexOptions.class, obj);
		if (enm!=null) {
			return enm;
		}
		if (obj instanceof String) {
			String val=String.class.cast(obj).toUpperCase().replace("_", "");
			for(PostgresIndexOptions e:PostgresIndexOptions.values()) {
				if (CommonUtils.eq(val, e.toString().replace("_", ""))) {
					return e;
				}
			}
		}
		return null;
	}
	
	public boolean isOnOff() {
		return this.getValueClass()==OnOffType.class;
	}
}
