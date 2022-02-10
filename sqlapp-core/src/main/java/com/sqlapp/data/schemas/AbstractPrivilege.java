/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
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
 * along with sqlapp-core.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.sqlapp.data.schemas;

import static com.sqlapp.util.CommonUtils.cast;
import static com.sqlapp.util.CommonUtils.compare;

import javax.xml.stream.XMLStreamException;

import com.sqlapp.data.schemas.properties.GrantableProperty;
import com.sqlapp.data.schemas.properties.GranteeNameProperty;
import com.sqlapp.data.schemas.properties.GrantorNameProperty;
import com.sqlapp.data.schemas.properties.PrivilegeProperty;
import com.sqlapp.data.schemas.properties.PrivilegeStateProperty;
import com.sqlapp.util.EqualsUtils;
import com.sqlapp.util.StaxWriter;
import com.sqlapp.util.ToStringBuilder;

abstract class AbstractPrivilege<T extends AbstractPrivilege<T>> extends
		AbstractDbObject<T> implements Comparable<T>
	, GrantorNameProperty<T> 
	, GranteeNameProperty<T> 
	, PrivilegeProperty<T> 
	, GrantableProperty<T>
	, PrivilegeStateProperty<T>
{
	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = 1L;
	/** 権限付与を実行したユーザー */
	private User grantor;
	/** アクセス権を付与されたユーザーまたはロール */
	private Principal<?> grantee;
	/** オブジェクトについての権限 */
	private String privilege;
	/** 許可ステート */
	private PrivilegeState state =null;
	/** 権限を与えられたユーザが、さらに他のユーザにも 同じ権限を与えることを許可 */
	private boolean grantable = (Boolean)SchemaProperties.GRANTABLE.getDefaultValue();

	/**
	 * 権限付与を実行したユーザーを取得します
	 */
	public User getGrantor() {
		return grantor;
	}

	public T setGrantor(User grantor) {
		this.grantor = getGrantorFromParent(grantor);
		return instance();
	}

	@Override
	public T setGrantorName(String grantor) {
		if (grantor == null) {
			return this.setGrantor(null);
		}
		return this.setGrantor(new User(grantor));
	}

	@Override
	protected AbstractDbObjectCollection<?> getParent() {
		return (AbstractDbObjectCollection<?>) super.getParent();
	}

	protected User getGrantorFromParent(User grantor) {
		if (grantor == null) {
			return grantor;
		}
		if (this.getParent() == null) {
			return grantor;
		}
		Catalog catalog = this.getAncestor(Catalog.class);
		if (catalog == null) {
			return grantor;
		}
		User getUser = catalog.getUsers().get(grantor.getName());
		if (getUser != null) {
			return getUser;
		}
		return grantor;
	}

	/**
	 * 権限付与者の名称を取得します
	 */
	@Override
	public String getGrantorName() {
		if (grantor == null) {
			return null;
		}
		return grantor.getName();
	}

	@SuppressWarnings("unchecked")
	public <S extends Principal<?>> S getGrantee() {
		return (S) grantee;
	}

	/**
	 * アクセス権を付与されたユーザーまたはロールの名前を取得します
	 */
	@Override
	public String getGranteeName() {
		if (grantee == null) {
			return null;
		}
		return grantee.getName();
	}

	public T setGrantee(Principal<?> grantee) {
		this.grantee = getGranteeFromParent(grantee);
		return instance();
	}

	@Override
	public T setGranteeName(String grantee) {
		return this.setGrantee(new DummyPrincipal(grantee));
	}

	protected Principal<?> getGranteeFromParent(Principal<?> grantee) {
		if (grantee == null) {
			return grantee;
		}
		if (this.getParent() == null) {
			return grantee;
		}
		Catalog catalog = this.getAncestor(Catalog.class);
		if (catalog == null) {
			return grantee;
		}
		User getUser = catalog.getUsers().get(grantee.getName());
		if (getUser != null) {
			return getUser;
		}
		Role getRole = catalog.getRoles().get(grantee.getName());
		if (getRole != null) {
			return getRole;
		}
		return grantee;
	}

	@Override
	public String getPrivilege() {
		return privilege;
	}

	@Override
	public T setPrivilege(String privilege) {
		this.privilege = privilege;
		return instance();
	}

	/**
	 * @return the grantable
	 */
	@Override
	public boolean isGrantable() {
		return grantable;
	}

	/**
	 * @param grantable
	 *            the grantable to set
	 */
	@Override
	public T setGrantable(boolean grantable) {
		this.grantable = grantable;
		return instance();
	}

	@Override
	public PrivilegeState getState() {
		return state;
	}

	@Override
	public T setState(PrivilegeState privilegeState) {
		this.state = privilegeState;
		return instance();
	}

	protected abstract void writeAttributeXml(StaxWriter stax)
			throws XMLStreamException;

	@Override
	protected void writeXmlOptionalAttributes(StaxWriter stax)
			throws XMLStreamException {
		super.writeXmlOptionalAttributes(stax);
		stax.writeAttribute(SchemaProperties.GRANTOR_NAME.getLabel(), this.getGrantorName());
		stax.writeAttribute(SchemaProperties.GRANTEE_NAME.getLabel(), this.getGranteeName());
		writeAttributeXml(stax);
		stax.writeAttribute(SchemaProperties.PRIVILEGE.getLabel(), this.getPrivilege());
		stax.writeAttribute(SchemaProperties.PRIVILEGE_STATE.getLabel(), this.getState());
		if (this.isGrantable()) {
			stax.writeAttribute(SchemaProperties.GRANTABLE.getLabel(), this.isGrantable());
		}
	}

	/**
	 * toString()で子クラスで追加したプロパティの設定
	 * 
	 * @param builder
	 */
	@Override
	protected void toString(ToStringBuilder builder) {
		builder.add(SchemaProperties.GRANTOR_NAME, getGrantorName());
		builder.add(SchemaProperties.GRANTEE_NAME, getGranteeName());
		builder.add(SchemaProperties.PRIVILEGE, getPrivilege());
		builder.add(SchemaProperties.PRIVILEGE_STATE, getState());
		builder.add(SchemaProperties.GRANTABLE, isGrantable());
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj, EqualsHandler equalsHandler) {
		if (!super.equals(obj, equalsHandler)) {
			return false;
		}
		if (!(obj instanceof AbstractPrivilege)) {
			return false;
		}
		T val = cast(obj);
		if (!equals(SchemaProperties.GRANTOR_NAME, val, equalsHandler)) {
			return false;
		}
		if (!equals(SchemaProperties.GRANTEE_NAME, val, equalsHandler)) {
			return false;
		}
		if (!equals(SchemaProperties.PRIVILEGE, val,
				equalsHandler, EqualsUtils.getEqualsIgnoreCaseSupplier(this.getPrivilege(), val.getPrivilege()))) {
			return false;
		}
		if (!equals(SchemaProperties.PRIVILEGE_STATE, val, equalsHandler)) {
			return false;
		}
		if (!equals(SchemaProperties.GRANTABLE, val, equalsHandler)) {
			return false;
		}
		return true;
	}

	/**
	 * like用のハンドラー
	 */
	private final static EqualsHandler LIKE_EQUALS_HANDLER = new IncludeFilterEqualsHandler(
			SchemaProperties.PRIVILEGE.getLabel(), SchemaProperties.GRANTEE_NAME.getLabel());

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.sqlapp.data.schemas.AbstractDbObject#like(com.sqlapp.data.schemas
	 * .AbstractDbObject)
	 */
	@Override
	public boolean like(Object obj) {
		if (!equals(obj, LIKE_EQUALS_HANDLER)) {
			return false;
		}
		return true;
	}

	@Override
	public int compareTo(T o) {
		int ret = compare(this.getGrantorName(), o.getGrantorName());
		if (ret != 0) {
			return ret;
		}
		ret = compare(this.getGranteeName(), o.getGranteeName());
		if (ret != 0) {
			return ret;
		}
		return compare(this.getPrivilege(), o.getPrivilege());
	}

	@Override
	protected void validate() {
		if (grantee != null && grantee.getParent() == null) {
			this.grantee = getGranteeFromParent(grantee);
		}
		if (grantor != null && grantor.getParent() == null) {
			this.grantor = getGrantorFromParent(grantor);
		}
	}
}
