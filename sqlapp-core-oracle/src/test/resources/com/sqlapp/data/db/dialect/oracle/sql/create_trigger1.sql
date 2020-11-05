CREATE OR REPLACE TRIGGER "triggerA"
ON "tableA"
FOR EACH ROW
BEGIN
  if inserting then
    update cnt_tbl set i_cnt = i_cnt + 1;
  elsif updating then
    update cnt_tbl set u_cnt = u_cnt + 1;
  else
    update cnt_tbl set d_cnt = d_cnt + 1;
  end if;
END;