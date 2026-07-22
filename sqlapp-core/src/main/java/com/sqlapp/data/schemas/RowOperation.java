package com.sqlapp.data.schemas;

public enum RowOperation {
	DEFAULT {
		@Override
		public boolean isDefault() {
			return true;
		}
	},
	UNCHANGED {
		@Override
		public boolean isUnchanged() {
			return true;
		}
	},
	INSERT {
		@Override
		public boolean isInsert() {
			return true;
		}
	},
	UPDATE {
		@Override
		public boolean isUpdate() {
			return true;
		}
	},
	MERGE {
		@Override
		public boolean isMerge() {
			return true;
		}
	},
	DELETE {
		@Override
		public boolean isDelete() {
			return true;
		}
	},
	INSERT_IGNORE {
		@Override
		public boolean isInsertIgnore() {
			return true;
		}
	},;

	public void setStatus(Row row) {
		row.setRowOperation(this);
	}

	public boolean isDefault() {
		return false;
	}

	public boolean isUnchanged() {
		return false;
	}

	public boolean isDelete() {
		return false;
	}

	public boolean isInsert() {
		return false;
	}

	public boolean isUpdate() {
		return false;
	}

	public boolean isMerge() {
		return false;
	}

	public boolean isInsertIgnore() {
		return false;
	}

}
