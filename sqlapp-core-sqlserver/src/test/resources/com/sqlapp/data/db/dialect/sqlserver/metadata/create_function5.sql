-- 'FT' アセンブリ (CLR) テーブル値関数
CREATE FUNCTION ReadEventLog(@logname nvarchar(100))
RETURNS TABLE 
(logTime datetime
,Message nvarchar(4000)
,Category nvarchar(4000)
,InstanceId bigint)
AS 
EXTERNAL NAME tvfEventLog.TabularEventLog.InitMethod;