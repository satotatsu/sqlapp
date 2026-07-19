package com.sqlapp.data.schemas;

public enum RowOperation {
	DEFAULT {
		@Override
		public boolean isDefault() {
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
		public boolean isInsert() {
			return true;
		}

		@Override
		public boolean isMerge() {
			return true;
		}

		@Override
		public boolean isUpdate() {
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
		public boolean isInsert() {
			return true;
		}

		@Override
		public boolean isInsertIgnore() {
			return false;
		}
	},;

	public void setStatus(Row row) {
		row.setRowOperation(this);
	}

	public boolean isDefault() {
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
