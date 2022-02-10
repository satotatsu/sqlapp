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

import static com.sqlapp.util.CommonUtils.compare;

import java.util.function.Supplier;

import javax.xml.stream.XMLStreamException;

import com.sqlapp.data.schemas.properties.AdminProperty;
import com.sqlapp.data.schemas.properties.DefaultProperty;
import com.sqlapp.data.schemas.properties.GranteeNameProperty;
import com.sqlapp.data.schemas.properties.complex.MemberRoleProperty;
import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.StaxWriter;

/**
 * ユーザーもしくはロールに割り当てられたロールに対応したオブジェクト
 * 
 * @author satoh
 * 
 */
public final class RoleMember extends AbstractDbObject<RoleMember> implements
		AdminProperty<RoleMember>, Comparable<RoleMember>,
		HasParent<RoleMemberCollection>
		, MemberRoleProperty<RoleMember>
		, GranteeNameProperty<RoleMember>
		, DefaultProperty<RoleMember>{
	/** serialVersionUID */
	private static final long serialVersionUID = 1L;
	/** 権限を受け取るユーザーまたはロール */
	private Principal<?> grantee = null;
	/** 付与されたロール */
	private Role memberRole = null;
	/** admin */
	private boolean admin;
	/**
	 * ロールがユーザーのデフォルトロールとして指定されたか？
	 */
	private boolean _default=(Boolean)SchemaProperties.DEFAULT.getDefaultValue();

	/**
	 * コンストラクタ
	 */
	public RoleMember() {
	}

	/**
	 * コンストラクタ
	 * 
	 * @param grantee
	 * @param memberRole
	 */
	public RoleMember(Principal<?> grantee, Role memberRole) {
		this(grantee, memberRole, false, false);
	}

	@Override
	protected Supplier<RoleMember> newInstance(){
		return ()->new RoleMember();
	}
	
	/**
	 * コンストラクタ
	 * 
	 * @param grantee
	 * @param memberRole
	 * @param admin
	 * @param _default
	 */
	public RoleMember(Principal<?> grantee, Role memberRole, boolean admin,
			boolean _default) {
		this.setGrantee(grantee);
		this.setMemberRole(memberRole);
		this.setAdmin(admin);
		this.setDefault(_default);
	}

	/**
	 * 権限を受け取るユーザーまたはロールを取得します
	 * 
	 */
	@SuppressWarnings("unchecked")
	public <S extends Principal<?>> S getGrantee() {
		if (grantee != null && grantee.getParent() == null) {
			setGranteeFromParent(grantee);
		}
		return (S) grantee;
	}

	protected void setGranteeFromParent(Principal<?> grantee) {
		if (this.grantee == grantee) {
			return;
		}
		Catalog catalog = this.getAncestor(Catalog.class);
		if (catalog == null) {
			this.grantee = grantee;
			return;
		}
		User getUser = catalog.getUsers().get(grantee.getName());
		if (getUser != null) {
			this.grantee = getUser;
			return;
		}
		Role getRole = catalog.getRoles().get(grantee.getName());
		if (getRole != null) {
			this.grantee = getRole;
			return;
		}
		this.grantee = grantee;
	}

	@Override
	public String getGranteeName() {
		if (grantee == null) {
			return null;
		}
		return grantee.getName();
	}

	public RoleMember setGrantee(Principal<?> grantee) {
		this.grantee = grantee;
		return this;
	}

	@Override
	public RoleMember setGranteeName(String grantee) {
		this.grantee = new DummyPrincipal(grantee);
		return this;
	}

	@Override
	public boolean isAdmin() {
		return admin;
	}

	@Override
	public RoleMember setAdmin(boolean admin) {
		this.admin = admin;
		return this;
	}

	@Override
	public boolean isDefault() {
		return _default;
	}

	@Override
	public RoleMember setDefault(boolean _default) {
		this._default = _default;
		return this;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj, EqualsHandler equalsHandler) {
		if (!(obj instanceof RoleMember)) {
			return false;
		}
		if (!super.equals(obj, equalsHandler)) {
			return false;
		}
		RoleMember val = (RoleMember) obj;
		if (!equals(SchemaProperties.GRANTEE_NAME, val, equalsHandler)) {
			return false;
		}
		if (!equals(SchemaProperties.MEMBER_ROLE_NAME, val, equalsHandler)) {
			return false;
		}
		if (!equals(SchemaProperties.ADMIN, val, equalsHandler)) {
			return false;
		}
		if (!equals(SchemaProperties.DEFAULT, val, equalsHandler)) {
			return false;
		}
		return equalsHandler.equalsResult(this, obj);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.schemas.AbstractNamedObject#like(java.lang.Object)
	 */
	@Override
	public boolean like(Object obj) {
		if (!(obj instanceof RoleMember)) {
			return false;
		}
		RoleMember cst = (RoleMember) obj;
		if (!CommonUtils.eq(this.getGranteeName(), cst.getGranteeName())) {
			return false;
		}
		if (!CommonUtils.eq(this.getMemberRoleName(), cst.getMemberRoleName())) {
			return false;
		}
		return true;
	}

	@Override
	protected void writeXmlOptionalAttributes(StaxWriter stax)
			throws XMLStreamException {
		super.writeXmlOptionalAttributes(stax);
		stax.writeAttribute(SchemaProperties.GRANTEE_NAME.getLabel(), this.getGranteeName());
		stax.writeAttribute(SchemaProperties.MEMBER_ROLE_NAME.getLabel(), this.getMemberRoleName());
		stax.writeAttribute(SchemaProperties.ADMIN.getLabel(), this.isAdmin());
		stax.writeAttribute(SchemaProperties.DEFAULT.getLabel(), this.isDefault());
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.schemas.AbstractDbObject#getParent()
	 */
	@Override
	public RoleMemberCollection getParent() {
		return (RoleMemberCollection) super.getParent();
	}

	@SuppressWarnings("rawtypes")
	@Override
	public int compareTo(RoleMember o) {
		int ret = compare((Principal) this.grantee, (Principal) o.grantee);
		if (ret != 0) {
			return ret;
		}
		return compare(this.memberRole, o.memberRole);
	}

}
