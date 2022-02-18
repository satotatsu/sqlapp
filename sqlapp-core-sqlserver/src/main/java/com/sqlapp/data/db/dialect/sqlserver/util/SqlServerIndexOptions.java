package com.sqlapp.data.db.dialect.sqlserver.util;

import java.sql.SQLException;
import java.util.function.Consumer;

import com.sqlapp.data.schemas.Table;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.util.OnOffType;

public enum SqlServerIndexOptions {
	PAD_INDEX() {
		@Override
		public Object getDefaultValue() {
			return OnOffType.OFF;
		}
	},
	FILLFACTOR,
	IGNORE_DUP_KEY() {
		@Override
		public Object getDefaultValue() {
			return OnOffType.OFF;
		}
	},
	STATISTICS_NORECOMPUTE() {
		@Override
		public Object getDefaultValue() {
			return OnOffType.OFF;
		}
	},
	STATISTICS_INCREMENTAL(){
		@Override
		public Object getDefaultValue() {
			return OnOffType.OFF;
		}
	},
	ALLOW_ROW_LOCKS(){
		@Override
		public Object getDefaultValue() {
			return OnOffType.ON;
		}
	},
	ALLOW_PAGE_LOCKS(){
		@Override
		public Object getDefaultValue() {
			return OnOffType.ON;
		}
	},
	OPTIMIZE_FOR_SEQUENTIAL_KEY(){
		@Override
		public Object getDefaultValue() {
			return OnOffType.OFF;
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
	
	public void setTable(final ExResultSet rs, Table table) throws SQLException {
		
	}

	protected void setTableOnOffParams(final ExResultSet rs, Table table, Consumer<OnOffType> cons) throws SQLException {
		if (!rs.contains(this.toString())) {
			return;
		}
		boolean bool = rs.getBoolean(this.toString());
		OnOffType onOffType=OnOffType.parse(bool);
		cons.accept(onOffType);
	}
}
