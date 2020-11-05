/*create table comment*/CREATE TABLE employee (id INT, 
                       name VARCHAR(10), 
                       salary DECIMAL(9,2));

INSERT INTO employee VALUES (1, 'aaaa', 35000), 
                            (2, 'bbbb', 35000);

/*comment1*/                          
                            

set term !! ;
create trigger set_foo_primary for foo
  before insert
  as begin
    new.a = gen_id(gen_foo,1);
  end
!!
set term ; !! 

/*comment2
 * 
 * 
 */                          


SELECT * FROM employee;

SELECT * FROM former_employee;
