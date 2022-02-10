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

import java.io.Serializable;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.sql.AbstractBean;
import com.sqlapp.util.CommonUtils;

/**
 * 製品バージョン情報
 * 
 * @author tatsuo satoh
 * 
 */
public class ProductVersionInfo extends AbstractBean implements Serializable {

	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = 6913182598920630655L;
	/**
	 * 製品名
	 */
	private String name = null;
	/**
	 * メジャーバージョン
	 */
	private Integer majorVersion = null;
	/**
	 * マイナーバージョン
	 */
	private Integer minorVersion = null;
	/**
	 * リビジョン
	 */
	private Integer revision = null;

	public Dialect toDialect(){
		if (name==null||majorVersion==null||minorVersion==null){
			return DialectResolver.getInstance().getDialect("standard", 0, 0, revision);
		}
		return DialectResolver.getInstance().getDialect(name, majorVersion, minorVersion, revision);
	}
	
	/**
	 * @return the name
	 */
	public String getName() {
		return name;
	}

	/**
	 * @param name
	 *            the name to set
	 */
	public ProductVersionInfo setName(String name) {
		this.name = name;
		return this;
	}

	/**
	 * @return the majorVersion
	 */
	public Integer getMajorVersion() {
		return majorVersion;
	}

	/**
	 * @param majorVersion
	 *            the majorVersion to set
	 */
	public ProductVersionInfo setMajorVersion(Integer majorVersion) {
		this.majorVersion = majorVersion;
		return this;
	}

	/**
	 * @return the minorVersion
	 */
	public Integer getMinorVersion() {
		return minorVersion;
	}

	/**
	 * @param minorVersion
	 *            the minorVersion to set
	 */
	public ProductVersionInfo setMinorVersion(Integer minorVersion) {
		this.minorVersion = minorVersion;
		return this;
	}

	/**
	 * @return the revision
	 */
	public Integer getRevision() {
		return revision;
	}

	/**
	 * @param revision
	 *            the revision to set
	 */
	public ProductVersionInfo setRevision(Integer revision) {
		this.revision = revision;
		return this;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		StringBuilder builder=new StringBuilder();
		builder.append(this.getName());
		builder.append(" ");
		if (this.getMajorVersion()!=null){
			builder.append(this.getMajorVersion());
			builder.append(".");
			builder.append(this.getMinorVersion());
		}
		if (this.getRevision()!=null){
			builder.append(".");
			builder.append(this.getRevision());
		}
		return builder.toString();
	}
	
	public String getVersionText(){
		StringBuilder builder=new StringBuilder();
		if (this.getMajorVersion()!=null){
			builder.append(this.getMajorVersion());
			builder.append(".");
			builder.append(this.getMinorVersion());
		}
		if (this.getRevision()!=null){
			builder.append(".");
			builder.append(this.getRevision());
		}
		return builder.toString();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Object#clone()
	 */
	@Override
	public ProductVersionInfo clone() {
		return (ProductVersionInfo) super.clone();
	}
	
	/**
	 * バージョンが等しいかを判定します
	 * 
	 * @param majorVersion
	 * @param minorVersion
	 * @param revision
	 */
	public boolean eq(Integer majorVersion, Integer minorVersion,
			Integer revision) {
		if (!CommonUtils.eq(this.getMajorVersion(), majorVersion)) {
			return false;
		}
		if (!CommonUtils.eq(this.getMinorVersion(), minorVersion)) {
			return false;
		}
		if (!CommonUtils.eq(this.getRevision(), revision)) {
			return false;
		}
		return true;
	}

	/**
	 * バージョンが指定されたもの以上かを判定します
	 * 
	 * @param majorVersion
	 * @param minorVersion
	 * @param minimumVersion
	 */
	public boolean gte(int majorVersion, int minorVersion,
			Integer minimumVersion) {
		if (eq(majorVersion, minorVersion, minimumVersion)) {
			return true;
		}
		return gt(majorVersion, minorVersion, minimumVersion);
	}

	/**
	 * バージョンが指定されたものより大きいかを判定します
	 * 
	 * @param majorVersion
	 * @param minorVersion
	 * @param revision
	 */
	public boolean gt(Integer majorVersion, Integer minorVersion,
			Integer revision) {
		if (gt(this.getMajorVersion(), majorVersion)) {
			return true;
		} else {
			if (!eq(this.getMajorVersion(), majorVersion)) {
				return false;
			}
		}
		if (gt(this.getMinorVersion(), minorVersion)) {
			return true;
		} else {
			if (!eq(this.getMinorVersion(), minorVersion)) {
				return false;
			}
		}
		return gt(this.getRevision(), revision);
	}

	protected boolean eq(Integer val1, Integer val2) {
		if (val1 != null) {
			return val1.equals(val2);
		} else {
			if (val2 == null) {
				return true;
			} else {
				return false;
			}
		}
	}

	protected boolean gt(Integer val1, Integer val2) {
		if (val1 == null) {
			return false;
		} else {
			if (val2 == null) {
				return true;
			} else {
				return val1.compareTo(val2) > 0;
			}
		}
	}

	protected boolean lt(Integer val1, Integer val2) {
		if (val1 == null) {
			return true;
		} else {
			if (val2 == null) {
				return false;
			} else {
				return val1.compareTo(val2) < 0;
			}
		}
	}

	/**
	 * バージョンが指定されたものより小さいかを判定します
	 * 
	 * @param majorVersion
	 * @param minorVersion
	 * @param revision
	 */
	public boolean lt(Integer majorVersion, Integer minorVersion,
			Integer revision) {
		if (lt(this.getMajorVersion(), majorVersion)) {
			return true;
		} else {
			if (!eq(this.getMajorVersion(), majorVersion)) {
				return false;
			}
		}
		if (lt(this.getMinorVersion(), minorVersion)) {
			return true;
		} else {
			if (!eq(this.getMinorVersion(), minorVersion)) {
				return false;
			}
		}
		return lt(this.getRevision(), revision);
	}

	/**
	 * バージョンが指定されたもの以下かを判定します
	 * 
	 * @param majorVersion
	 * @param minorVersion
	 * @param minimumVersion
	 */
	public boolean lte(int majorVersion, int minorVersion,
			Integer minimumVersion) {
		if (eq(majorVersion, minorVersion, revision)) {
			return true;
		}
		return lt(majorVersion, minorVersion, revision);
	}

}
