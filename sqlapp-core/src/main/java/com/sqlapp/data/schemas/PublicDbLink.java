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

import static com.sqlapp.util.CommonUtils.isEmpty;

import java.util.function.Supplier;

import javax.xml.stream.XMLStreamException;

import com.sqlapp.data.schemas.properties.ObjectLinkProperties;
import com.sqlapp.util.StaxWriter;
import com.sqlapp.util.ToStringBuilder;

/**
 * Public DBリンク
 * 
 * @author satoh
 * 
 */
public final class PublicDbLink extends AbstractNamedObject<PublicDbLink>
		implements HasParent<PublicDbLinkCollection>,
		ObjectLinkProperties<PublicDbLink> {
	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = -5594263228976491676L;
	/** ドライバクラス名 */
	private String driverClassName = null;
	/** ユーザーID */
	private String userId = null;
	/** パスワード */
	private String password = null;
	/** 接続先 */
	private String dataSource = null;
	/** 接続先カタログ */
	private String connectionCatalog = null;
	/** パスワードが暗号化されているか */
	private boolean passwordEncrypted = false;

	/**
	 * コンストラクタ
	 */
	protected PublicDbLink() {
	}

	/**
	 * コンストラクタ
	 * 
	 * @param name
	 */
	public PublicDbLink(String name) {
		super(name);
	}

	@Override
	protected Supplier<PublicDbLink> newInstance(){
		return ()->new PublicDbLink();
	}
	
	@Override
	protected void toStringDetail(ToStringBuilder builder) {
		builder.add(SchemaProperties.USER_ID, this.getUserId());
		builder.add(SchemaProperties.PASSWORD, this.getPassword());
		builder.add(SchemaProperties.PASSWORD_ENCRYPTED, this.isPasswordEncrypted());
		builder.add(SchemaProperties.DRIVER_CLASS_NAME, this.getDriverClassName());
		builder.add(SchemaProperties.DATA_SOURCE, this.getDataSource());
		builder.add(SchemaProperties.CONNECTION_CATALOG, this.getConnectionCatalog());
		toStringChild(builder);
	}

	protected void toStringChild(ToStringBuilder builder) {
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.schemas.AbstractNamedObject#equals(java.lang.Object,
	 * com.sqlapp.data.schemas.EqualsHandler)
	 */
	@Override
	public boolean equals(Object obj, EqualsHandler equalsHandler) {
		if (!super.equals(obj, equalsHandler)) {
			return false;
		}
		if (!(obj instanceof PublicDbLink)) {
			return false;
		}
		PublicDbLink val = (PublicDbLink) obj;
		if (!equals(SchemaProperties.USER_ID, val, equalsHandler)) {
			return false;
		}
		if (!equals(SchemaProperties.PASSWORD, val, equalsHandler)) {
			return false;
		}
		if (!equals(SchemaProperties.PASSWORD_ENCRYPTED, val, equalsHandler)) {
			return false;
		}
		if (!equals(SchemaProperties.DRIVER_CLASS_NAME, val, equalsHandler)) {
			return false;
		}
		if (!equals(SchemaProperties.DATA_SOURCE, val, equalsHandler)) {
			return false;
		}
		if (!equals(SchemaProperties.CONNECTION_CATALOG, val, equalsHandler)) {
			return false;
		}
		return true;
	}

	@Override
	public String getUserId() {
		return userId;
	}

	@Override
	public PublicDbLink setUserId(String userId) {
		this.userId = userId;
		return instance();
	}

	@Override
	public String getPassword() {
		return password;
	}

	@Override
	public PublicDbLink setPassword(String password) {
		this.password = password;
		return instance();
	}

	@Override
	public String getDataSource() {
		return dataSource;
	}

	@Override
	public PublicDbLink setDataSource(String dataSource) {
		this.dataSource = dataSource;
		return instance();
	}

	@Override
	public boolean isPasswordEncrypted() {
		return passwordEncrypted;
	}

	@Override
	public PublicDbLink setPasswordEncrypted(boolean passwordEncrypted) {
		this.passwordEncrypted = passwordEncrypted;
		return instance();
	}

	@Override
	public String getDriverClassName() {
		return driverClassName;
	}

	@Override
	public PublicDbLink setDriverClassName(String driverClassName) {
		this.driverClassName = driverClassName;
		return instance();
	}

	/**
	 * @return the connectionCatalog
	 */
	public String getConnectionCatalog() {
		return connectionCatalog;
	}

	/**
	 * @param connectionCatalog
	 *            the connectionCatalog to set
	 */
	public PublicDbLink setConnectionCatalog(String connectionCatalog) {
		this.connectionCatalog = connectionCatalog;
		return instance();
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
		stax.writeAttribute(SchemaProperties.DRIVER_CLASS_NAME.getLabel(), this.getDriverClassName());
		stax.writeAttribute(SchemaProperties.USER_ID.getLabel(), this.getUserId());
		stax.writeAttribute(SchemaProperties.PASSWORD.getLabel(), this.getPassword());
		if (!isEmpty(this.getPassword())) {
			stax.writeAttribute(SchemaProperties.PASSWORD_ENCRYPTED.getLabel(), this.isPasswordEncrypted());
		}
		stax.writeAttribute(SchemaProperties.DATA_SOURCE.getLabel(), this.getDataSource());
		stax.writeAttribute(SchemaProperties.CONNECTION_CATALOG.getLabel(), this.getConnectionCatalog());
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.schemas.AbstractSchemaObject#getParent()
	 */
	@Override
	public PublicDbLinkCollection getParent() {
		return (PublicDbLinkCollection) super.getParent();
	}
}
