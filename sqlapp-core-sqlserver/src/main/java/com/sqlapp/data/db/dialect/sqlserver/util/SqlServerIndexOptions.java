package com.sqlapp.data.db.dialect.sqlserver.util;

import java.sql.SQLException;

import com.sqlapp.data.converter.Converters;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.SqlServer2000;
import com.sqlapp.data.db.dialect.resolver.SqlServerDialectResolver;
import com.sqlapp.data.schemas.Index;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.properties.SpecificsProperty;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.util.OnOffType;

public enum SqlServerIndexOptions {
	PAD_INDEX() {
		@Override
		public Object getDefaultValue() {
			return OnOffType.OFF;
		}
		@Override
		public void setIndex(Index index, Object value) {
			setIndexOnOffParams(index, value);
		}
		@Override
		public void setIndex(final ExResultSet rs, Index index) throws SQLException {
			setOnOffParams(rs, index);
		}
	},
	FILLFACTOR(){
		@Override
		public void setIndex(Index index, Object value) {
			Integer val=Converters.getDefault().convertObject(value, Integer.class);
			if (val!=null&&val.intValue()>=0&&val.intValue()<=100) {
				index.getSpecifics().put(this.toString(), val);
			}
		}
	},
	IGNORE_DUP_KEY() {
		@Override
		public Object getDefaultValue() {
			return OnOffType.OFF;
		}
		@Override
		public void setIndex(Index index, Object value) {
			setIndexOnOffParams(index, value);
		}
		@Override
		public void setIndex(final ExResultSet rs, Index index) throws SQLException {
			setOnOffParams(rs, index);
		}
	},
	STATISTICS_NORECOMPUTE() {
		@Override
		public Object getDefaultValue() {
			return OnOffType.OFF;
		}
		@Override
		public void setIndex(Index index, Object value) {
			setIndexOnOffParams(index, value);
		}
		@Override
		public void setIndex(final ExResultSet rs, Index index) throws SQLException {
			setOnOffParams(rs, index);
		}
	},
	STATISTICS_INCREMENTAL(){
		@Override
		public Object getDefaultValue() {
			return OnOffType.OFF;
		}
		@Override
		public void setIndex(Index index, Object value) {
			setIndexOnOffParams(index, value);
		}
		@Override
		public void setIndex(final ExResultSet rs, Index index) throws SQLException {
			setOnOffParams(rs, index);
		}
	},
	ALLOW_ROW_LOCKS(){
		@Override
		public Object getDefaultValue() {
			return OnOffType.ON;
		}
		@Override
		public void setIndex(Index index, Object value) {
			setIndexOnOffParams(index, value);
		}
		@Override
		public void setIndex(final ExResultSet rs, Index index) throws SQLException {
			setOnOffParams(rs, index);
		}
	},
	ALLOW_PAGE_LOCKS(){
		@Override
		public Object getDefaultValue() {
			return OnOffType.ON;
		}
		@Override
		public void setIndex(Index index, Object value) {
			setIndexOnOffParams(index, value);
		}
		@Override
		public void setIndex(final ExResultSet rs, Index index) throws SQLException {
			setOnOffParams(rs, index);
		}
	},
	OPTIMIZE_FOR_SEQUENTIAL_KEY(){
		@Override
		public Object getDefaultValue() {
			return OnOffType.OFF;
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
	
	public Object getDefaultValue() {
		return null;
	}
	
	public void setIndex(Index index, Object value){
	}

	public void setTable(Table table, String value) {
	}

	public void setTable(final ExResultSet rs, Table table) throws SQLException {	
	}

	public void setIndex(final ExResultSet rs, Index index) throws SQLException {	
	}

	public boolean supports(SqlServer2000 dialect) {
		return true;
	}
	
	protected void setIndexOnOffParams(Index index, Object value) {
		OnOffType onOffType=OnOffType.parse(value);
		index.getSpecifics().put(this.toString(), onOffType);
	}

	protected void setOnOffParams(final ExResultSet rs, SpecificsProperty<?> obj) throws SQLException {
		if (!rs.contains(this.toString())) {
			return;
		}
		boolean bool = rs.getBoolean(this.toString());
		OnOffType onOffType=OnOffType.parse(bool);
		obj.getSpecifics().put(this.toString(), onOffType);
	}
}
