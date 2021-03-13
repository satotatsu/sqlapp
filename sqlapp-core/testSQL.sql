CREATE TABLE EMP
(
    id int primary key
  , name varchar(50)
  , org_id int
);
CREATE TABLE DEPT
(
    id int primary key
  , name varchar(50)
);
ALTER TABLE EMP ADD CONSTRAINT EMP_FK Foreign Key (org_id) REFERENCES DEPT (id);
