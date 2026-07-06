package com.sqlapp.data.db.sql;

public enum SqlExecuteType {
	ROW {
		@Override
		public boolean isRow() {
			return true;
		}
	},
	ROWS {
		@Override
		public boolean isRows() {
			return true;
		}
	},
	TABLE {
		@Override
		public boolean isTable() {
			return true;
		}
	},
	SIMPLE {
		@Override
		public boolean isSimple() {
			return true;
		}
	};

	public boolean isSimple() {
		return false;
	}

	public boolean isTable() {
		return false;
	}

	public boolean isRow() {
		return false;
	}

	public boolean isRows() {
		return false;
	}
}
