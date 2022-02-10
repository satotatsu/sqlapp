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

import java.sql.Timestamp;
import java.util.function.Supplier;

import javax.xml.stream.XMLStreamException;

import com.sqlapp.data.schemas.properties.AdminProperty;
import com.sqlapp.data.schemas.properties.ExpiredAtProperty;
import com.sqlapp.data.schemas.properties.LockedAtProperty;
import com.sqlapp.data.schemas.properties.LoginUserNameProperty;
import com.sqlapp.data.schemas.properties.PasswordProperty;
import com.sqlapp.data.schemas.properties.complex.DefaultSchemaProperty;
import com.sqlapp.util.StaxWriter;
import com.sqlapp.util.ToStringBuilder;

/**
 * ユーザーに対応したオブジェクト
 * 
 * @author satoh
 * 
 */
public final class User extends Principal<User> implements
		HasParent<UserCollection>, PasswordProperty<User>, AdminProperty<User>
	, LockedAtProperty<User>
	, ExpiredAtProperty<User>
	, DefaultSchemaProperty<User>
	, LoginUserNameProperty<User>{
	/** serialVersionUID */
	private static final long serialVersionUID = 1L;
	/** パスワード */
	private String password = null;
	/** デフォルトスキーマ */
	@SuppressWarnings("unused")
	private Schema defaultSchema = null;
	/** ログインユーザー名 */
	private String loginUserName = null;
	/** ロックされた日時 */
	private Timestamp lockedAt;
	/** expireされた日時 */
	private Timestamp expiredAt;
	/** 管理者 */
	private boolean admin = false;

	public boolean isAdmin() {
		return admin;
	}

	public User setAdmin(boolean admin) {
		this.admin = admin;
		return this;
	}

	public User() {
	}

	public User(String userName) {
		super(userName);
	}
	
	@Override
	protected Supplier<User> newInstance(){
		return ()->new User();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj, EqualsHandler equalsHandler) {
		if (!(obj instanceof User)) {
			return false;
		}
		if (!super.equals(obj, equalsHandler)) {
			return false;
		}
		User val = (User) obj;
		if (!equals(SchemaProperties.DEFAULT_SCHEMA_NAME, val, equalsHandler)) {
			return false;
		}
		if (!equals(SchemaProperties.LOGIN_USER_NAME, val, equalsHandler)) {
			return false;
		}
		if (!equals(SchemaProperties.LOCKED_AT, val, equalsHandler)) {
			return false;
		}
		if (!equals(SchemaProperties.EXPIRED_AT, val, equalsHandler)) {
			return false;
		}
		if (!equals(SchemaProperties.PASSWORD, val, equalsHandler)) {
			return false;
		}
		if (!equals(SchemaProperties.ADMIN, val, equalsHandler)) {
			return false;
		}
		return equalsHandler.equalsResult(this, obj);
	}

	@Override
	protected void writeXmlOptionalAttributes(StaxWriter stax)
			throws XMLStreamException {
		stax.writeAttribute(SchemaProperties.DEFAULT_SCHEMA_NAME.getLabel(), this.getDefaultSchemaName());
		stax.writeAttribute(SchemaProperties.LOGIN_USER_NAME.getLabel(), this.getLoginUserName());
		stax.writeAttribute(SchemaProperties.PASSWORD.getLabel(), this.getPassword());
		stax.writeAttribute(SchemaProperties.LOCKED_AT.getLabel(), this.getLockedAt());
		stax.writeAttribute(SchemaProperties.EXPIRED_AT.getLabel(), this.getExpiredAt());
		stax.writeAttribute(SchemaProperties.ADMIN.getLabel(), this.isAdmin());
		super.writeXmlOptionalAttributes(stax);
	}

	@Override
	protected void toStringDetail(ToStringBuilder builder) {
		builder.add(SchemaProperties.DEFAULT_SCHEMA_NAME.getLabel(), this.getDefaultSchemaName());
		builder.add(SchemaProperties.LOGIN_USER_NAME, this.getLoginUserName());
		builder.add(SchemaProperties.PASSWORD, this.getPassword());
		builder.add(SchemaProperties.LOCKED_AT, this.getLockedAt());
		builder.add(SchemaProperties.EXPIRED_AT, this.getExpiredAt());
		builder.add(SchemaProperties.ADMIN, this.isAdmin());
	}

	@Override
	public String getPassword() {
		return password;
	}

	@Override
	public User setPassword(String password) {
		this.password = password;
		return this;
	}

	/**
	 * @return the lockedAt
	 */
	@Override
	public Timestamp getLockedAt() {
		return lockedAt;
	}

	/**
	 * @param lockedAt the lockedAt to set
	 */
	@Override
	public User setLockedAt(Timestamp lockedAt) {
		this.lockedAt = lockedAt;
		return instance();
	}


	@Override
	public Timestamp getExpiredAt() {
		return this.expiredAt;
	}

	@Override
	public User setExpiredAt(Timestamp value) {
		this.expiredAt=value;
		return instance();
	}

	@Override
	public String getLoginUserName() {
		return loginUserName;
	}

	@Override
	public User setLoginUserName(String loginUserName) {
		this.loginUserName = loginUserName;
		return this;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.schemas.AbstractNamedObject#getParent()
	 */
	@Override
	public UserCollection getParent() {
		return (UserCollection) super.getParent();
	}


}
