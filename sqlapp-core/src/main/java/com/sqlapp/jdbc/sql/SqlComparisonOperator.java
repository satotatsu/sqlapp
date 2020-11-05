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
package com.sqlapp.jdbc.sql;

import java.util.Locale;

import com.sqlapp.data.schemas.EnumProperties;
/**
 * SQLの関係演算子
 * @author tatsuo satoh
 *
 */
public enum SqlComparisonOperator implements EnumProperties{
	/*=*/
	EQ(){
		@Override
		public String getSqlValue(){
			return "=";
		}
		
		@Override
		public SqlComparisonOperator getMultipleOperator(){
			return IN;
		}
		
		@Override
		public SqlComparisonOperator reverse(){
			return NEQ;
		}
	},
	/*<>*/
	NEQ(){
		@Override
		public String getSqlValue(){
			return "<>";
		}

		@Override
		public SqlComparisonOperator getMultipleOperator(){
			return NOT_IN;
		}
		
		@Override
		public boolean isNegationOperator(){
			return true;
		}

		@Override
		public SqlComparisonOperator reverse(){
			return EQ;
		}
	}
	, 
	/*IN*/
	IN(){
		@Override
		public String getSqlValue(){
			return "IN";
		}

		@Override
		public boolean isArray() {
			return true;
		}
		@Override
		public boolean allowMultiple(){
			return true;
		}

		@Override
		public SqlComparisonOperator reverse(){
			return NOT_IN;
		}
	}
	, 
	/*IN*/
	NOT_IN(){
		@Override
		public String getSqlValue(){
			return "NOT IN";
		}

		@Override
		public boolean isArray() {
			return true;
		}

		@Override
		public boolean allowMultiple(){
			return true;
		}

		@Override
		public boolean isNegationOperator(){
			return true;
		}

		@Override
		public SqlComparisonOperator reverse(){
			return IN;
		}
	}
	, 
	/*LIKE*/
	NOT_LIKE(){
		@Override
		public String getSqlValue(){
			return "NOT LIKE";
		}

		@Override
		public boolean isNegationOperator(){
			return true;
		}

		@Override
		public SqlComparisonOperator reverse(){
			return LIKE;
		}
	}
	, 
	/*LIKE*/
	LIKE(){
		@Override
		public String getSqlValue(){
			return "LIKE";
		}

		@Override
		public SqlComparisonOperator reverse(){
			return NOT_LIKE;
		}
	},
	/*STARTS_WITH*/
	STARTS_WITH(){
		@Override
		public String getDisplayName() {
			return "STARTS WITH";
		}
		
		@Override
		public String getSqlValue(){
			return LIKE.getSqlValue();
		}

		@Override
		public java.util.function.Function<Object, Object> getConverter(){
			return (obj)->{
				if (obj instanceof String){
					return (String)obj+"%";
				}
				return obj;
			};
		}
	},
	/*ENDS_WITH*/
	ENDS_WITH(){
		@Override
		public String getDisplayName() {
			return "ENDS WITH";
		}

		@Override
		public String getSqlValue(){
			return LIKE.getSqlValue();
		}
		@Override
		public java.util.function.Function<Object, Object> getConverter(){
			return (obj)->{
				if (obj instanceof String){
					return "%"+(String)obj;
				}
				return obj;
			};
		}
	},
	/*CONTAINS*/
	CONTAINS(){
		@Override
		public String getDisplayName() {
			return "CONTAINS";
		}

		@Override
		public String getSqlValue(){
			return LIKE.getSqlValue();
		}
		@Override
		public java.util.function.Function<Object, Object> getConverter(){
			return (obj)->{
				if (obj instanceof String){
					return "%"+(String)obj+"%";
				}
				return obj;
			};
		}
	}
	, 
	/*>=*/
	GTE(){
		@Override
		public String getSqlValue(){
			return ">=";
		}

		@Override
		public SqlComparisonOperator reverse(){
			return LT;
		}
	}
	, 
	/*>*/
	GT(){
		@Override
		public String getSqlValue(){
			return ">";
		}

		@Override
		public SqlComparisonOperator reverse(){
			return LTE;
		}
	}
	, 
	/*<=*/
	LTE(){
		@Override
		public String getSqlValue(){
			return "<=";
		}

		@Override
		public SqlComparisonOperator reverse(){
			return GT;
		}
	}
	, 
	/*<*/
	LT(){
		@Override
		public String getSqlValue(){
			return "<";
		}

		@Override
		public SqlComparisonOperator reverse(){
			return GTE;
		}
	},
	/*BETWEEN*/
	BETWEEN(){
		@Override
		public String getSqlValue(){
			return "BETWEEN";
		}

		@Override
		public String conjuction(){
			return " AND ";
		}

		@Override
		public Integer getParameterCount(){
			return 2;
		}
		
		@Override
		public SqlComparisonOperator reverse(){
			return NOT_BETWEEN;
		}
	},
	/*NOT BETWEEN*/
	NOT_BETWEEN(){
		@Override
		public String getSqlValue(){
			return "NOT BETWEEN";
		}

		@Override
		public String conjuction(){
			return " AND ";
		}

		@Override
		public Integer getParameterCount(){
			return 2;
		}
		
		@Override
		public SqlComparisonOperator reverse(){
			return BETWEEN;
		}
	},
	/*a<x<b*/
	GT_AND_LT(){
		@Override
		public String getSqlValue(){
			return "a< x <b";
		}

		@Override
		public String conjuction(){
			return " AND ";
		}

		@Override
		public SqlComparisonOperator[] getOperaterElements(){
			return new SqlComparisonOperator[]{GT, LT};
		}

		@Override
		public SqlComparisonOperator reverse(){
			return LTE_OR_GTE;
		}
	},
	/*a<=x<b*/
	GTE_AND_LT(){
		@Override
		public String getSqlValue(){
			return "a<= x <b";
		}

		@Override
		public String conjuction(){
			return " AND ";
		}

		@Override
		public SqlComparisonOperator[] getOperaterElements(){
			return new SqlComparisonOperator[]{GTE, LT};
		}

		@Override
		public SqlComparisonOperator reverse(){
			return LT_OR_GTE;
		}
	},
	/*a<x<=b*/
	GT_AND_LTE(){
		@Override
		public String getSqlValue(){
			return "a< x <=b";
		}

		@Override
		public String conjuction(){
			return " AND ";
		}
	
		@Override
		public SqlComparisonOperator[] getOperaterElements(){
			return new SqlComparisonOperator[]{GT, LTE};
		}

		@Override
		public SqlComparisonOperator reverse(){
			return LTE_OR_GT;
		}
	},
	/*a<=x<=b*/
	GTE_AND_LTE(){
		@Override
		public String getSqlValue(){
			return "a<= x <=b";
		}

		@Override
		public String conjuction(){
			return " AND ";
		}
		
		@Override
		public SqlComparisonOperator[] getOperaterElements(){
			return new SqlComparisonOperator[]{GTE, LTE};
		}

		@Override
		public SqlComparisonOperator reverse(){
			return LT_OR_GT;
		}
	},
	/*x<=a OR x>=b*/
	LTE_OR_GTE(){
		@Override
		public String getSqlValue(){
			return "x<=a OR x>=b*";
		}

		@Override
		public String conjuction(){
			return " OR ";
		}

		@Override
		public SqlComparisonOperator[] getOperaterElements(){
			return new SqlComparisonOperator[]{LTE, GTE};
		}
		
		@Override
		public SqlComparisonOperator reverse(){
			return GT_AND_LT;
		}
	},
	/*x>a OR x<=b*/
	LT_OR_GTE(){
		@Override
		public String getSqlValue(){
			return "x>a OR x<=b";
		}

		@Override
		public String conjuction(){
			return " OR ";
		}

		@Override
		public SqlComparisonOperator[] getOperaterElements(){
			return new SqlComparisonOperator[]{LT, GTE};
		}

		@Override
		public SqlComparisonOperator reverse(){
			return GTE_AND_LT;
		}
	},
	/*x>=a OR x<b*/
	LTE_OR_GT(){
		@Override
		public String getSqlValue(){
			return "x>=a OR x<b";
		}

		@Override
		public String conjuction(){
			return " OR ";
		}

		@Override
		public SqlComparisonOperator[] getOperaterElements(){
			return new SqlComparisonOperator[]{LTE, GT};
		}

		@Override
		public SqlComparisonOperator reverse(){
			return GT_AND_LTE;
		}
	},
	/*x>a OR x<b*/
	LT_OR_GT(){
		@Override
		public String getSqlValue(){
			return "x>a OR x<b";
		}

		@Override
		public String conjuction(){
			return " OR ";
		}

		@Override
		public SqlComparisonOperator[] getOperaterElements(){
			return new SqlComparisonOperator[]{LT, GT};
		}

		@Override
		public SqlComparisonOperator reverse(){
			return GTE_AND_LTE;
		}
	},
	;

	public boolean allowMultiple(){
		return false;
	}

	public boolean isNegationOperator(){
		return false;
	}

	public SqlComparisonOperator reverse(){
		return null;
	}

	public SqlComparisonOperator getMultipleOperator(){
		return null;
	}

	public String conjuction(){
		return " OR ";
	}

	public java.util.function.Function<Object, Object> getConverter(){
		return (obj)->obj;
	}

	public Integer getParameterCount(){
		if (getOperaterElements()!=null){
			return getOperaterElements().length;
		}
		return null;
	}
	
	public SqlComparisonOperator[] getOperaterElements(){
		return null;
	}
	
	@Override
	public String getDisplayName() {
		return getSqlValue();
	}

	public boolean isArray() {
		return false;
	}
	
	@Override
	public String getDisplayName(Locale locale) {
		return this.toString();
	}

	@Override
	public String getSqlValue() {
		return null;
	}
	
	public static SqlComparisonOperator parse(String value) {
		if (value == null) {
			return null;
		}
		for(SqlComparisonOperator enm:values()){
			if (enm.getDisplayName().equalsIgnoreCase(value)){
				return enm;
			}
			if (enm.getSqlValue().equalsIgnoreCase(value)){
				return enm;
			}
			if (enm.toString().equalsIgnoreCase(value)){
				return enm;
			}
		}
		return null;
	}
	
}
