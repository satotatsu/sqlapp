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

import java.util.Comparator;
import java.util.List;
import java.util.Set;

import com.sqlapp.util.CommonUtils;

/**
 * ORDER FOR drop table
 * 
 * @author tatsuo satoh
 * 
 */
class TableDropOrderComparator implements Comparator<Table> {

	@Override
	public int compare(Table table1, Table table2) {
		if (table1.getSchema() == null) {
			if (table2.getSchema() == null) {
				return compareName(table1, table2);
			} else {
				return 1;
			}
		} else {
			if (table2.getSchema() == null) {
				return -1;
			}
		}
		if ("flows".equals(table1.getName())||"flows".equals(table2.getName())){
			if ("operations".equals(table1.getName())||"operations".equals(table2.getName())){
				System.out.println("*");
			}
		}
		return compareRelation(table1, table2);
	}

	protected int compareRelation(Table table1, Table table2) {
		List<ForeignKeyConstraint> fks1 = table1.getConstraints()
				.getForeinKeyConstraints(fk->!fk.getRelatedTable().equals(table1));
		List<ForeignKeyConstraint> fks2 = table2.getConstraints()
				.getForeinKeyConstraints(fk->!fk.getRelatedTable().equals(table2));
		List<ForeignKeyConstraint> cfks1 = table1.getChildRelations(fk->!fk.getRelatedTable().equals(table1));
		List<ForeignKeyConstraint> cfks2 = table2.getChildRelations(fk->!fk.getRelatedTable().equals(table2));
		if (CommonUtils.isEmpty(cfks1)) {
			if (CommonUtils.isEmpty(cfks2)) {
				return -Table.TableOrder.CREATE.getComparator().compare(table1, table2);
			} else {
				//table2に子リレーションがあるのでtable2の方が大きい
				return cfks2.size()*noRelationMultiply();
			}
		} else {
			if (CommonUtils.isEmpty(cfks2)) {
				//table1に子リレーションがあるのでtable1の方が大きい
				return -cfks1.size()*noRelationMultiply();
			}
		}
		int level=5000000;
		for (ForeignKeyConstraint fk : fks1) {
			Set<ForeignKeyConstraint> evaluated=CommonUtils.set();
			int ret = isRelated(evaluated, fk, table1, table2, level);
			if (ret!=0) {
				//table1→table2のリレーションがあるのでtable1の方が大きい
				return -ret;
			}
		}
		for (ForeignKeyConstraint fk : fks2) {
			Set<ForeignKeyConstraint> evaluated=CommonUtils.set();
			int ret = isRelated(evaluated, fk, table2, table1, level);
			if (ret!=0) {
				return ret;
			}
		}
		int point1=countDependent(fks1);
		int point2=countDependent(fks2);
		if (point1!=point2) {
			return -(point1-point2)*relationMultiply();
		}
		if (fks1.size()!=fks2.size()){
			return -(fks1.size()-fks2.size())*relationMultiply();
		}
		return compareName(table1, table2);
	}

	private int noRelationMultiply(){
		return 50000000;
	}
	
	private int schemaMultiply(){
		return 10;
	}

	private int relationMultiply(){
		return 5000;
	}

	private int compareName(Table table1, Table table2){
		int count=compareName(table1.getSchemaName(), table2.getSchemaName());
		if (count!=0){
			return -count*schemaMultiply();
		}
		count=compareName(table1.getName(), table2.getName());
		if (count!=0){
			return -count;
		}
		return 0;
	}

	private int compareName(String name1, String name2){
		if (name1==null){
			if (name2==null){
				return 0;
			} else{
				int count=CommonUtils.compare(name2, name1);
				if (count>0){
					return -1;
				}else if (count<0){
					return 1;
				}
			}
		} else{
			int count=CommonUtils.compare(name1, name2);
			if (count>0){
				return 1;
			}else if (count<0){
				return -1;
			}
		}
		return 0;
	}
	
	protected int isRelated(Set<ForeignKeyConstraint> evaluated, ForeignKeyConstraint fk, Table table1,
			Table table2, int level) {
		if (evaluated.contains(fk)){
			return level;
		}
		evaluated.add(fk);
		if (CommonUtils.eq(fk.getRelatedTable().getName(), table2.getName())) {
			if (CommonUtils.eq(fk.getRelatedTable().getSchemaName(),
					table2.getSchemaName())||fk.getRelatedTable().getSchemaName()==null) {
				return level;
			}
		}
		if (CommonUtils.eq(fk.getRelatedTable().getName(), table1.getName())) {
			if (CommonUtils.eq(fk.getRelatedTable().getSchemaName(),
					table1.getSchemaName())||fk.getRelatedTable().getSchemaName()==null) {
				return 0;
			}
		}
		List<ForeignKeyConstraint> fkParents = fk.getRelatedTable().getChildRelations();
		for (ForeignKeyConstraint fkParent : fkParents) {
			int ret;
			if (isDependent(fkParent)){
				ret = isRelated(evaluated, fkParent, table1, table2, level-relationMultiply());
			} else{
				ret = isRelated(evaluated, fkParent, table1, table2, level-(relationMultiply()/10));
			}
			if (ret!=0) {
				return ret;
			}
		}
		return 0;
	}

	private int countDependent(List<ForeignKeyConstraint> fks){
		return (int)fks.stream().filter(fk->isDependent(fk)).count();
	}

	private boolean isDependent(ForeignKeyConstraint fk){
		for(Column column:fk.getColumns()){
			if (!column.isNotNull()){
				return false;
			}
		}
		return true;
	}
	
}
