/**
 * Copyright (C) 2026-2026 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core.
 *
 * sqlapp-core is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.schemas;

import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.ToStringBuilder;

public class ColumnListForRelated extends ColumnList {

	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = 1947995004048906450L;

	private ForeignKeyConstraint parent;

	protected ColumnListForRelated(ForeignKeyConstraint parent) {
		this.parent = parent;
	}

	@Override
	protected void put(Column obj) {
		if (obj == null) {
			return;
		}
		if (parent != null) {
			if (parent.getTableName() == null && obj.getTableName() != null) {
				parent.setRelatedTableName(obj.getTableName());
			}
		}
		super.put(obj);
	}

	@Override
	public ColumnListForRelated clone() {
		final ColumnListForRelated clone = new ColumnListForRelated(this.parent);
		for (Column column : this) {
			clone.add(column.clone());
		}
		return clone;
	}

	@Override
	public String toString() {
		final ToStringBuilder builder = new ToStringBuilder("columns");
		builder.addColumnNames(this);
		return builder.toString();
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == null) {
			return false;
		}
		if (!(obj instanceof ColumnListForRelated)) {
			return false;
		}
		ColumnListForRelated val = (ColumnListForRelated) obj;
		if (this.size() != val.size()) {
			return false;
		}
		for (int i = 0; i < this.size(); i++) {
			Column col1 = this.get(i);
			Column col2 = val.get(i);
			if (!CommonUtils.eq(col1.getName(), col2.getName())) {
				return false;
			}
		}
		return true;
	}

}
