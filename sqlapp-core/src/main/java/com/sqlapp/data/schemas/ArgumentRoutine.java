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

import static com.sqlapp.util.CommonUtils.cast;
import static com.sqlapp.util.CommonUtils.isEmpty;

import javax.xml.stream.XMLStreamException;

import com.sqlapp.data.schemas.properties.DeterministicProperty;
import com.sqlapp.data.schemas.properties.ExecuteAsProperty;
import com.sqlapp.data.schemas.properties.MaxDynamicResultSetsProperty;
import com.sqlapp.data.schemas.properties.ParallelProperty;
import com.sqlapp.data.schemas.properties.SavepointLevelProperty;
import com.sqlapp.data.schemas.properties.SpecificNameProperty;
import com.sqlapp.data.schemas.properties.SqlDataAccessProperty;
import com.sqlapp.data.schemas.properties.SqlSecurityProperty;
import com.sqlapp.data.schemas.properties.object.ArgumentsProperty;
import com.sqlapp.util.StaxWriter;
import com.sqlapp.util.ToStringBuilder;

/**
 * Function、Procedureの親クラス
 * 
 * @author satoh
 * 
 */
public abstract class ArgumentRoutine<T extends ArgumentRoutine<T>> extends
		Routine<T> implements ExecuteAsProperty<T>, SpecificNameProperty<T>,DeterministicProperty<T>
	,ParallelProperty<T>
	,SqlDataAccessProperty<T>
	,SqlSecurityProperty<T>
	,SavepointLevelProperty<T>
	,MaxDynamicResultSetsProperty<T>
	,ArgumentsProperty<T>
	{

	/** serialVersionUID */
	private static final long serialVersionUID = 8421121636119616875L;

	/**
	 * コンストラクタ
	 */
	protected ArgumentRoutine() {
	}

	/**
	 * コンストラクタ
	 * 
	 * @param name
	 */
	protected ArgumentRoutine(String name) {
		super(name);
	}

	/**
	 * コンストラクタ
	 * 
	 * @param name
	 * @param specificName
	 */
	public ArgumentRoutine(String name, String specificName) {
		super(name, specificName);
	}

	/** 引数 */
	protected NamedArgumentCollection<T> arguments = new NamedArgumentCollection<T>(
			this);
	/** 決定性 */
	private Boolean deterministic = null;
	/** 平行実行 */
	private Boolean parallel = null;
	/** ステートメントでのSQLアクセスタイプ */
	private SqlDataAccess sqlDataAccess = null;
	/** SQLセキュリティ */
	private SqlSecurity sqlSecurity = null;
	/** 実行ユーザー・プリンシパル */
	private String executeAs = null;
	/** SAVE POINT LEVEL */
	private SavepointLevel savepointLevel = null;
	/** ルーチンによって返される動的結果セットの最大数 */
	private Integer maxDynamicResultSets = null;

	@Override
	public NamedArgumentCollection<T> getArguments() {
		return arguments;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.sqlapp.data.schemas.AbstractSchemaObject#equals(java.lang.Object,
	 * com.sqlapp.data.schemas.EqualsHandler)
	 */
	@Override
	public boolean equals(Object obj, EqualsHandler equalsHandler) {
		if (!(obj instanceof ArgumentRoutine)) {
			return false;
		}
		if (!super.equals(obj, equalsHandler)) {
			return false;
		}
		T val = cast(obj);
		if (!equals(SchemaProperties.DETERMINISTIC, val, equalsHandler)) {
			return false;
		}
		if (!equals(SchemaProperties.PARALLEL, val, equalsHandler)) {
			return false;
		}
		if (!equals(SchemaProperties.SQL_DATA_ACCESS, val, equalsHandler)) {
			return false;
		}
		if (!equals(SchemaProperties.SQL_SECURITY, val, equalsHandler)) {
			return false;
		}
		if (!equals(SchemaProperties.EXECUTE_AS, val, equalsHandler)) {
			return false;
		}
		if (!equals(SchemaProperties.SAVEPOINT_LEVEL, val, equalsHandler)) {
			return false;
		}
		if (!equals(SchemaProperties.MAX_DYNAMIC_RESULT_SETS, val,
				equalsHandler)) {
			return false;
		}
		if (!equals(SchemaObjectProperties.ARGUMENTS, val, equalsHandler)) {
			return false;
		}
		return equalsHandler.equalsResult(this, obj);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.sqlapp.data.schemas.AbstractNamedObject#toStringDetail(com.sqlapp
	 * .util.ToStringBuilder)
	 */
	@Override
	protected void toStringDetail(ToStringBuilder builder) {
		super.toStringDetail(builder);
		builder.add(SchemaProperties.DETERMINISTIC, this.getDeterministic());
		builder.add(SchemaProperties.PARALLEL, this.getParallel());
		builder.add(SchemaProperties.SQL_DATA_ACCESS, this.getSqlDataAccess());
		builder.add(SchemaProperties.SQL_SECURITY, this.getSqlSecurity());
		builder.add(SchemaProperties.EXECUTE_AS, this.getExecuteAs());
		builder.add(SchemaProperties.SAVEPOINT_LEVEL, this.getSavepointLevel());
		builder.add(SchemaProperties.MAX_DYNAMIC_RESULT_SETS, this.getMaxDynamicResultSets());
		builder.add(SchemaObjectProperties.ARGUMENTS, this.getArguments());
	}

	@Override
	protected void writeXmlOptionalAttributes(StaxWriter stax)
			throws XMLStreamException {
		super.writeXmlOptionalAttributes(stax);
		stax.writeAttribute(SchemaProperties.DETERMINISTIC.getLabel(), this.getDeterministic());
		stax.writeAttribute(SchemaProperties.PARALLEL.getLabel(), this.getParallel());
		stax.writeAttribute(SchemaProperties.SQL_DATA_ACCESS.getLabel(), this.getSqlDataAccess());
		stax.writeAttribute(SchemaProperties.SQL_SECURITY.getLabel(), this.getSqlSecurity());
		stax.writeAttribute(SchemaProperties.EXECUTE_AS.getLabel(), this.getExecuteAs());
		stax.writeAttribute(SchemaProperties.SAVEPOINT_LEVEL.getLabel(), this.getSavepointLevel());
		stax.writeAttribute(SchemaProperties.MAX_DYNAMIC_RESULT_SETS.getLabel(),
				this.getMaxDynamicResultSets());
	}

	@Override
	protected void writeXmlOptionalValues(StaxWriter stax)
			throws XMLStreamException {
		super.writeXmlOptionalValues(stax);
		if (!isEmpty(this.getArguments())) {
			this.getArguments().writeXml(stax);
		}
	}

	/**
	 * sepecificNameの再設定に合わせて、親をresetします
	 */
	protected void renewParent() {
		if (this.getParent() != null) {
			if (this.getParent() instanceof AbstractBaseDbObjectCollection){
				((AbstractBaseDbObjectCollection<?>)this.getParent()).renew();
			}
		}
	}

	/**
	 * @return the deterministic
	 */
	@Override
	public Boolean getDeterministic() {
		return deterministic;
	}

	/**
	 * @param deterministic
	 *            the deterministic to set
	 */
	@Override
	public T setDeterministic(Boolean deterministic) {
		this.deterministic = deterministic;
		return instance();
	}

	/**
	 * @return the parallel
	 */
	@Override
	public Boolean getParallel() {
		return parallel;
	}

	/**
	 * @param parallel the parallel to set
	 */
	@Override
	public T setParallel(Boolean parallel) {
		this.parallel = parallel;
		return instance();
	}

	/**
	 * @return the sqlDataAccess
	 */
	@Override
	public SqlDataAccess getSqlDataAccess() {
		return sqlDataAccess;
	}

	/**
	 * @param sqlDataAccess
	 *            the sqlDataAccess to set
	 */
	@Override
	public T setSqlDataAccess(SqlDataAccess sqlDataAccess) {
		this.sqlDataAccess = sqlDataAccess;
		return instance();
	}

	/**
	 * @return the sqlSecurity
	 */
	@Override
	public SqlSecurity getSqlSecurity() {
		return sqlSecurity;
	}

	/**
	 * @param sqlSecurity
	 *            the sqlSecurity to set
	 */
	@Override
	public T setSqlSecurity(SqlSecurity sqlSecurity) {
		this.sqlSecurity = sqlSecurity;
		return instance();
	}

	/**
	 * @return the executeAs
	 */
	@Override
	public String getExecuteAs() {
		return executeAs;
	}

	/**
	 * @param executeAs
	 *            the executeAs to set
	 */
	@Override
	public T setExecuteAs(String executeAs) {
		this.executeAs = executeAs;
		if (this.executeAs != null) {
			SqlSecurity sqlSecurity = SqlSecurity.parse(executeAs);
			if (sqlSecurity != null) {
				this.setSqlSecurity(sqlSecurity);
			}
		}
		return instance();
	}

	/**
	 * @return the savepointLevel
	 */
	@Override
	public SavepointLevel getSavepointLevel() {
		return savepointLevel;
	}

	/**
	 * @param savepointLevel
	 *            the savepointLevel to set
	 */
	@Override
	public T setSavepointLevel(SavepointLevel savepointLevel) {
		this.savepointLevel = savepointLevel;
		return instance();
	}

	/**
	 * @return the maxDynamicResultSets
	 */
	@Override
	public Integer getMaxDynamicResultSets() {
		return maxDynamicResultSets;
	}

	/**
	 * @param maxDynamicResultSets
	 *            the maxDynamicResultSets to set
	 */
	@Override
	public T setMaxDynamicResultSets(Number maxDynamicResultSets) {
		if (maxDynamicResultSets != null) {
			setMaxDynamicResultSets(maxDynamicResultSets.intValue());
		} else {
			this.maxDynamicResultSets = null;
		}
		return instance();
	}
	
	/**
	 * @param maxDynamicResultSets
	 *            the maxDynamicResultSets to set
	 */
	@Override
	public T setMaxDynamicResultSets(int maxDynamicResultSets) {
		this.maxDynamicResultSets = Integer.valueOf(maxDynamicResultSets);
		return instance();
	}

	@Override
	protected void validate(){
		this.arguments.validate();
	}
}
