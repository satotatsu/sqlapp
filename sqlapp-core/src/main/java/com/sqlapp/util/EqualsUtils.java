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
package com.sqlapp.util;

import java.util.Arrays;
import java.util.function.BooleanSupplier;

public class EqualsUtils {

	private static class TrueSupplier implements BooleanSupplier{
		@Override
		public boolean getAsBoolean() {
			return true;
		}
	}

	private static class FalseSupplier implements BooleanSupplier{
		@Override
		public boolean getAsBoolean() {
			return false;
		}
	}

	private static class SimpleEqualsSupplier<T> implements BooleanSupplier{

		protected final T o1;
		protected final T o2;
		
		public SimpleEqualsSupplier(final T o1,final T o2){
			this.o1=o1;
			this.o2=o2;
		}
		
		protected boolean test(final T t, final T u) {
			if (t==u){
				return true;
			}
			if (t!=null){
				if (u!=null){
					return t.equals(u);
				}
			}
			return false;
		}

		@Override
		public boolean getAsBoolean() {
			return test(o1,o2);
		}
	}
	
	private static class CachedEqualsSupplier<T> extends SimpleEqualsSupplier<T>{
		
		private boolean evaluated=false;
		
		private boolean value=false;
		
		public CachedEqualsSupplier(final T o1,final T o2){
			super(o1,o2);
		}

		@Override
		public boolean getAsBoolean() {
			if (evaluated){
				return value;
			}
			value=test(o1,o2);
			evaluated=true;
			return value;
		}
	}
	
	private static class ByteArraySupplier extends CachedEqualsSupplier<byte[]>{

		public ByteArraySupplier(final byte[] o1,final byte[] o2){
			super(o1,o2);
		}

		@Override
		protected boolean test(final byte[] t, final byte[] u) {
			return CommonUtils.eq(t,u);
		}
	}

	private static class ObjectArraySupplier extends CachedEqualsSupplier<Object[]>{

		public ObjectArraySupplier(final Object[] o1, final Object[] o2){
			super(o1,o2);
		}

		@Override
		protected boolean test(final Object[] t, final Object[] u) {
			return Arrays.deepEquals(t, u);
		}
	}

	private static class StringArraySupplier extends CachedEqualsSupplier<String[]>{

		public StringArraySupplier(final String[] o1, final String[] o2){
			super(o1,o2);
		}

		@Override
		protected boolean test(final String[] t, final String[] u) {
			return Arrays.deepEquals(t, u);
		}
	}

	private static class EqualsIgnoreCaseSupplier extends CachedEqualsSupplier<String>{

		public EqualsIgnoreCaseSupplier(final String o1,final String o2){
			super(o1,o2);
		}

		@Override
		protected boolean test(final String t, final String u) {
			return CommonUtils.eqIgnoreCase(o1, o2);
		}
	}

	private static final BooleanSupplier TRUE_SUPPLIER=new TrueSupplier();

	private static final BooleanSupplier FALSE_SUPPLIER=new FalseSupplier();

	/**
	 * オブジェクトの比較結果を返すSupplierを取得します。
	 * @param o1
	 * @param o2
	 */
	public static BooleanSupplier getEqualsSupplier(final Object o1, final Object o2){
		return new CachedEqualsSupplier<Object>(o1, o2);
	}

	/**
	 * オブジェクトの比較結果を返すSupplierを取得します。
	 * @param o1
	 * @param o2
	 */
	public static BooleanSupplier getEqualsSupplier(final Object[] o1, final Object[] o2){
		return new ObjectArraySupplier(o1, o2);
	}

	/**
	 * オブジェクトの比較結果を返すSupplierを取得します。
	 * @param o1
	 * @param o2
	 */
	public static BooleanSupplier getEqualsSupplier(final String[] o1, final String[] o2){
		return new StringArraySupplier(o1, o2);
	}
	
	public static BooleanSupplier getEqualsSupplier(final String o1, final String o2){
		return new CachedEqualsSupplier<String>(o1, o2);
	}

	public static BooleanSupplier getEqualsIgnoreCaseSupplier(final String o1, final String o2){
		return new EqualsIgnoreCaseSupplier(o1, o2);
	}

	public static BooleanSupplier getEqualsSupplier(final Integer o1, final Integer o2){
		return new CachedEqualsSupplier<Integer>(o1, o2);
	}

	public static BooleanSupplier getEqualsSupplier(final short o1, final short o2){
		if (o1==o2){
			return TRUE_SUPPLIER;
		}
		return FALSE_SUPPLIER;
	}

	public static BooleanSupplier getEqualsSupplier(final int o1, final int o2){
		if (o1==o2){
			return TRUE_SUPPLIER;
		}
		return FALSE_SUPPLIER;
	}

	public static BooleanSupplier getEqualsSupplier(final long o1, final long o2){
		if (o1==o2){
			return TRUE_SUPPLIER;
		}
		return FALSE_SUPPLIER;
	}

	public static BooleanSupplier getEqualsSupplier(final boolean o1, final boolean o2){
		if (o1==o2){
			return TRUE_SUPPLIER;
		}
		return FALSE_SUPPLIER;
	}

	public static BooleanSupplier getEqualsSupplier(final byte[] o1, final byte[] o2){
		return new ByteArraySupplier(o1, o2);
	}

	public static BooleanSupplier getEqualsSupplier(final Boolean o1, final Boolean o2){
		return new CachedEqualsSupplier<Boolean>(o1, o2);
	}

	public static BooleanSupplier getEqualsSupplier(final boolean bool){
		if (bool){
			return TRUE_SUPPLIER;
		}
		return FALSE_SUPPLIER;
	}

	public static BooleanSupplier getEqualsSupplier(final BooleanSupplier booleanSupplier){
		return booleanSupplier;
	}

}
