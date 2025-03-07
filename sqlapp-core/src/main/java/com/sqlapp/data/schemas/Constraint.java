/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
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

import static com.sqlapp.util.CommonUtils.coalesce;
import static com.sqlapp.util.CommonUtils.compare;

import javax.xml.stream.XMLStreamException;

import com.sqlapp.data.schemas.properties.DeferrabilityProperty;
import com.sqlapp.data.schemas.properties.TableNameProperty;
import com.sqlapp.data.schemas.properties.EnableProperty;
import com.sqlapp.util.StaxWriter;
import com.sqlapp.util.ToStringBuilder;

/**
 * 制約クラス
 * 
 * @author satoh
 * 
 */
public abstract class Constraint extends AbstractSchemaObject<Constraint>
		implements Comparable<Constraint>, TableNameProperty<Constraint>, EnableProperty<Constraint>
		,DeferrabilityProperty<Constraint>, HasParent<ConstraintCollection>{
	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = -5534603682647428950L;
	/**
	 * テーブル名
	 */
	private String tableName = null;
	/** 制約が有効かを表す */
	private boolean enable = (Boolean)SchemaProperties.ENABLE.getDefaultValue();
	/** 制約のチェックの遅延 */
	private Deferrability deferrability =null;

	@Override
	public Deferrability getDeferrability() {
		return deferrability;
	}

	@Override
	public Constraint setDeferrability(Deferrability deferrability) {
		this.deferrability = deferrability;
		return this;
	}

	@Override
	public boolean isEnable() {
		return enable;
	}

	@Override
	public Constraint setEnable(boolean enable) {
		this.enable = enable;
		return this;
	}

	@Override
	public String getTableName() {
		Table table = this.getAncestor(Table.class);
		if (table != null) {
			return coalesce(table.getName(), tableName);
		}
		return tableName;
	}

	@Override
	public Constraint setTableName(String tableName) {
		this.tableName = tableName;
		return instance();
	}

	protected void setTableNameInternal(String tableName) {
		this.tableName = tableName;
	}

	protected Constraint() {
	};

	/**
	 * コンストラクタ
	 * 
	 * @param constraintName
	 */
	protected Constraint(String constraintName) {
		super(constraintName);
	}

	@Override
	public ConstraintCollection getParent() {
		return (ConstraintCollection) super.getParent();
	}

	protected void setConstraints(ConstraintCollection constraints) {
		this.setParent(constraints);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.dataset.AbstractNamedObject#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj, EqualsHandler equalsHandler) {
		if (!super.equals(obj, equalsHandler)) {
			return false;
		}
		if (!(obj instanceof Constraint)) {
			return false;
		}
		Constraint val = (Constraint) obj;
		if (!equals(SchemaProperties.ENABLE, val, equalsHandler)) {
			return false;
		}
		if (!equals(SchemaProperties.DEFERRABILITY, val, equalsHandler)) {
			return false;
		}
		return true;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.sqlapp.data.schemas.AbstractDbObject#like(com.sqlapp.data.schemas
	 * .AbstractDbObject)
	 */
	@Override
	public boolean like(Object obj) {
		if (equals(obj, IncludeFilterEqualsHandler.EQUALS_NAME_HANDLER)) {
			return true;
		} else {
			if (!equals(obj,
					ExcludeFilterEqualsHandler.EQUALS_WITHOUT_NAME_HANDLER)) {
				return false;
			}
			return true;
		}
	}

	/**
	 * @return カタログ名を取得します
	 */
	@Override
	public String getCatalogName() {
		Table table = this.getAncestor(Table.class);
		if (table != null) {
			return coalesce(table.getCatalogName(),
					super.getCatalogName());
		}
		return super.getCatalogName();
	}

	/**
	 * @return スキーマ名を取得します
	 */
	@Override
	public String getSchemaName() {
		Table table = this.getAncestor(Table.class);
		if (table != null) {
			return coalesce(table.getSchemaName(),
					super.getSchemaName());
		}
		return super.getSchemaName();
	}

	@Override
	public int compareTo(Constraint o) {
		if (o == null) {
			return -1;
		}
		if (this instanceof UniqueConstraint) {
			if (o instanceof UniqueConstraint) {
				return compareTo((UniqueConstraint) this, (UniqueConstraint) o);
			} else {
				return -1;
			}
		} else if (this instanceof ExcludeConstraint) {
			if (o instanceof UniqueConstraint) {
				return 1;
			} else if (o instanceof ExcludeConstraint) {
				return compareTo((Constraint) this, o);
			} else {
				return -1;
			}
		} else if (this instanceof CheckConstraint) {
			if (o instanceof UniqueConstraint) {
				return 1;
			} else if (o instanceof ExcludeConstraint) {
				return 1;
			} else if (o instanceof CheckConstraint) {
				return compareTo((Constraint) this, o);
			} else {
				return -1;
			}
		} else if (this instanceof ForeignKeyConstraint) {
			if (o instanceof ForeignKeyConstraint) {
				return compareTo((Constraint) this, o);
			} else {
				return 1;
			}
		}
		return 0;
	}

	protected int compareTo(UniqueConstraint o1, UniqueConstraint o2) {
		if (o1 == null) {
			if (o2 == null) {
				return 0;
			}
			return -1;
		} else if (o2 == null) {
			return 1;
		}
		if (o1.isPrimaryKey()) {
			return -1;
		} else {
			if (o2.isPrimaryKey()) {
				return 1;
			}
		}
		return compare(o1.getName(), o2.getName());
	}

	protected int compareTo(Constraint o1, Constraint o2) {
		if (o1 == null) {
			if (o2 == null) {
				return 0;
			}
			return compare(null, o2.getName());
		} else if (o2 == null) {
			return compare(o1.getName(), null);
		}
		return compare(o1.getName(), o2.getName());
	}

	@Override
	protected void toStringDetail(ToStringBuilder builder) {
		builder.add(SchemaProperties.ENABLE, this.isEnable());
		builder.add(SchemaProperties.DEFERRABILITY, this.getDeferrability());
	}

	/**
	 * XML書き込みでオプション属性を書き込みます
	 * 
	 * @param stax
	 * @throws XMLStreamException
	 */
	@Override
	protected void writeXmlOptionalAttributes(StaxWriter stax)
			throws XMLStreamException {
		super.writeXmlOptionalAttributes(stax);
		if (!this.isEnable()){
			stax.writeAttribute(SchemaProperties.ENABLE.getLabel(), this.isEnable());
		}
		stax.writeAttribute(SchemaProperties.DEFERRABILITY.getLabel(), this.getDeferrability());
	}

	/**
	 * XML書き込みでオプションの値を書き込みます
	 * 
	 * @param stax
	 * @throws XMLStreamException
	 */
	@Override
	protected void writeXmlOptionalValues(StaxWriter stax)
			throws XMLStreamException {
	}
}
