/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-sqlserver.
 *
 * sqlapp-core-sqlserver is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-sqlserver is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-sqlserver.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.sqlapp.data.db.dialect.sqlserver.util;

import java.sql.SQLException;
import java.util.function.Consumer;

import com.sqlapp.data.converter.Converters;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.SqlServer2000;
import com.sqlapp.data.db.dialect.resolver.SqlServerDialectResolver;
import com.sqlapp.data.schemas.Index;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.UniqueConstraint;
import com.sqlapp.data.schemas.properties.SpecificsProperty;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.EnumUtils;
import com.sqlapp.util.OnOffType;

public enum SqlServerIndexOptions {
	/**
	 * インデックス作成中の中間レベル ページの空き領域の割合にFILLFACTORを適用するか？
	 */
	PAD_INDEX() {
		@Override
		public OnOffType getDefaultValue() {
			return OnOffType.OFF;
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
	FILLFACTOR(){
		@Override
		public void setIndex(Index index, Object value) {
			Integer val=Converters.getDefault().convertObject(value, Integer.class);
			if (val==null) {
				index.getSpecifics().remove(getColumnKey());
				index.getSpecifics().remove(toString());
			}else if (val.intValue()>=0&&val.intValue()<=100) {
				index.getSpecifics().remove(getColumnKey());
				index.getSpecifics().put(this.toString(), val);
			}
		}
		@Override
		public String getColumnKey() {
			return "FILL_FACTOR";
		}

	},
	IGNORE_DUP_KEY() {
		@Override
		public OnOffType getDefaultValue() {
			return OnOffType.OFF;
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
	STATISTICS_NORECOMPUTE() {
		@Override
		public OnOffType getDefaultValue() {
			return OnOffType.OFF;
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
	STATISTICS_INCREMENTAL(){
		@Override
		public OnOffType getDefaultValue() {
			return OnOffType.OFF;
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
	/**
	 * インデックス データへのアクセスに行ロックを使用するか
	 */
	ALLOW_ROW_LOCKS(){
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
	/**
	 * インデックス データへのアクセスにページ ロックを使用するか
	 */
	ALLOW_PAGE_LOCKS(){
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
	OPTIMIZE_FOR_SEQUENTIAL_KEY(){
		@Override
		public OnOffType getDefaultValue() {
			return OnOffType.OFF;
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
		@Override
		public boolean supports(SqlServer2000 dialect) {
			Dialect dialect15 = SqlServerDialectResolver.getInstance().getDialect(15, 0);
			return dialect.compareTo(dialect15)>=0;
		}
	},
	COMPRESSION_DELAY(){
		@Override
		public void setTable(final ExResultSet rs, Table table) throws SQLException {
			if (!rs.contains(this.toString())) {
				return;
			}
			int val = rs.getInt(this.toString());
			if (val > 0) {
				table.getSpecifics().put(this.toString(), val);
			} else {
				table.getSpecifics().remove(this.toString());
			}
		}
	},

	DATA_COMPRESSION() {
		@Override
		public void setTable(final ExResultSet rs, Table table) throws SQLException {
			if (!rs.contains(this.toString())) {
				return;
			}
			String val = rs.getNString(this.toString());
			if ("NONE".equalsIgnoreCase(val)) {
				table.setCompression(false);
				table.setCompressionType(null);
			}else {
				table.setCompression(true);
				table.setCompressionType(val.toUpperCase());
			}
		}
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
		for(SqlServerIndexOptions enm:SqlServerIndexOptions.values()) {
			enm.setIndex(rs, index);
		}
	}
	
	public String getColumnKey() {
		return this.toString();
	}

	public void setUniqueConstraint(final ExResultSet rs, UniqueConstraint uk) throws SQLException {	
	}

	public static void setAllUniqueConstraint(final ExResultSet rs, UniqueConstraint uk) throws SQLException {	
		for(SqlServerIndexOptions enm:SqlServerIndexOptions.values()) {
			enm.setUniqueConstraint(rs, uk);
		}
	}

	public boolean supports(SqlServer2000 dialect) {
		return true;
	}
	
	protected void setIndexOnOffParams(Index index, Object value) {
		OnOffType onOffType=OnOffType.parse(value);
		index.getSpecifics().put(this.toString(), onOffType);
	}

	protected void setParams(final ExResultSet rs, Consumer<Object> cons) throws SQLException {
		if (!rs.contains(this.toString())) {
			return;
		}
		Object val = rs.getObject(this.getColumnKey());
		cons.accept(val);
	}

	protected void setOnOffParams(final ExResultSet rs, SpecificsProperty<?> obj) throws SQLException {
		if (!rs.contains(this.toString())) {
			return;
		}
		boolean bool = rs.getBoolean(this.getColumnKey());
		OnOffType onOffType=OnOffType.parse(bool);
		obj.getSpecifics().put(this.toString(), onOffType);
	}

	public static SqlServerIndexOptions parse(Object obj) {
		SqlServerIndexOptions enm=EnumUtils.parse(SqlServerIndexOptions.class, obj);
		if (enm!=null) {
			return enm;
		}
		if (obj instanceof String) {
			String val=String.class.cast(obj).toUpperCase().replace("_", "");
			for(SqlServerIndexOptions e:SqlServerIndexOptions.values()) {
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
