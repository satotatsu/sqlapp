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

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Argumentのコレクション
 * 
 */
public class NamedArgumentCollection<T extends ArgumentRoutine<?>> extends
		AbstractSchemaObjectCollection<NamedArgument> implements UnOrdered,
		HasParent<T>
, NewElement<NamedArgument, NamedArgumentCollection<T>>{
	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = 1L;

	/**
	 * コンストラクタ
	 */
	protected NamedArgumentCollection() {
	}

	
	
	/**
	 * コンストラクタ
	 */
	protected NamedArgumentCollection(ArgumentRoutine<?> routine) {
		super(routine);
	}

	@Override
	protected Supplier<NamedArgumentCollection<T>> newInstance(){
		return ()->new NamedArgumentCollection<T>();
	}
	
	@SuppressWarnings("unchecked")
	@Override
	public NamedArgumentCollection<T> clone(){
		return (NamedArgumentCollection<T>)super.clone();
	}
	
	@Override
	public boolean equals(Object obj, EqualsHandler equalsHandler) {
		if (!(obj instanceof NamedArgumentCollection)) {
			return false;
		}
		if (!super.equals(obj, equalsHandler)) {
			return false;
		}
		return equalsHandler.equalsResult(this, obj);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.schemas.AbstractDbObjectCollection#getSimpleName()
	 */
	@Override
	protected String getSimpleName() {
		return SchemaObjectProperties.ARGUMENTS.getLabel();
	}

	@SuppressWarnings("unchecked")
	@Override
	public T getParent() {
		return (T) super.getParent();
	}

	/**
	 * 新規の引数を取得します
	 * 
	 */
	public NamedArgument newArgument() {
		NamedArgument arg = new NamedArgument();
		arg.setParent(this);
		return arg;
	}

	/**
	 * 新規の引数を取得します
	 * 
	 * @param name
	 *            引数名
	 */
	public NamedArgument newArgument(String name) {
		NamedArgument arg = new NamedArgument(name);
		arg.setParent(this);
		return arg;
	}
	
	public NamedArgumentCollection<T> add(String name, Consumer<NamedArgument> cons){
		NamedArgument obj = new NamedArgument(name);
		this.add(obj);
		cons.accept(obj);
		return instance();
	}

	public NamedArgumentCollection<T> add(Consumer<NamedArgument> cons){
		NamedArgument obj = new NamedArgument();
		this.add(obj);
		cons.accept(obj);
		return instance();
	}

	private NamedArgumentCollection<T> instance(){
		return this;
	}
	
	/**
	 * 追加前のメソッド
	 */
	@Override
	protected boolean addSimple(NamedArgument e) {
		if (e.getName()==null){
			e.setName("$"+(this.size()+1));
		}
		return super.addSimple(e);
	}

	@Override
	public NamedArgument newElement() {
		return super.newElementInternal();
	}

	@Override
	protected Supplier<NamedArgument> getElementSupplier() {
		return ()->new NamedArgument();
	}

}
