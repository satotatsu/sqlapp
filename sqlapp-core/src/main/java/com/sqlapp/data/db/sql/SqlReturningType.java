package com.sqlapp.data.db.sql;

public enum SqlReturningType {
	NONE {
		@Override
		public boolean isNone() {
			return true;
		}
	},
	RESULT_SET {
		@Override
		public boolean isResultSet() {
			return true;
		}
	},
	GENERATED_KEYS {
		@Override
		public boolean isGeneratedKeys() {
			return true;
		}
	};

	public boolean isNone() {
		return false;
	}

	public boolean isResultSet() {
		return false;
	}

	public boolean isGeneratedKeys() {
		return false;
	}
}
