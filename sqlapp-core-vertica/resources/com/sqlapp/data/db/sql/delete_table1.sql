DELETE FROM "tableA"
WHERE 1=1
	/*if isNotEmpty(colA) */
	AND "colA" IN /*colA*/(0)
	/*end*/
	/*if isNotEmpty(colA_neq) */
	AND "colA" NOT IN /*colA_neq*/(0)
	/*end*/
	/*if isNotEmpty(colA_gt) */
	AND "colA" > /*colA_gt*/0
	/*end*/
	/*if isNotEmpty(colA_lt) */
	AND "colA" < /*colA_lt*/0
	/*end*/
	/*if isNotEmpty(colA_gte) */
	AND "colA" >= /*colA_gte*/0
	/*end*/
	/*if isNotEmpty(colA_lte) */
	AND "colA" <= /*colA_lte*/0
	/*end*/
	/*if isNotEmpty(colB) */
	AND "colB" IN /*colB*/(0)
	/*end*/
	/*if isNotEmpty(colB_neq) */
	AND "colB" NOT IN /*colB_neq*/(0)
	/*end*/
	/*if isNotEmpty(colB_gt) */
	AND "colB" > /*colB_gt*/0
	/*end*/
	/*if isNotEmpty(colB_lt) */
	AND "colB" < /*colB_lt*/0
	/*end*/
	/*if isNotEmpty(colB_gte) */
	AND "colB" >= /*colB_gte*/0
	/*end*/
	/*if isNotEmpty(colB_lte) */
	AND "colB" <= /*colB_lte*/0
	/*end*/
	/*if isNotEmpty(colC) */
	AND "colC" IN /*colC*/('')
	/*end*/
	/*if isNotEmpty(colC_neq) */
	AND "colC" NOT IN /*colC_neq*/('')
	/*end*/
	/*if isNotEmpty(colC_startsWith) */
	AND "colC" like /*colC_startsWith + '%'*/''
	/*end*/
	/*if isNotEmpty(colC_endsWith) */
	AND "colC" like /*'%' + colC_endsWith*/''
	/*end*/
	/*if isNotEmpty(colC_contains) */
	AND "colC" like /*'%' + colC_contains + '%'*/''
	/*end*/
	/*if isNotEmpty(lock_version) */
	AND "lock_version" IN /*lock_version*/(0)
	/*end*/
	/*if isNotEmpty(lock_version_neq) */
	AND "lock_version" NOT IN /*lock_version_neq*/(0)
	/*end*/
	/*if isNotEmpty(lock_version_gt) */
	AND "lock_version" > /*lock_version_gt*/0
	/*end*/
	/*if isNotEmpty(lock_version_lt) */
	AND "lock_version" < /*lock_version_lt*/0
	/*end*/
	/*if isNotEmpty(lock_version_gte) */
	AND "lock_version" >= /*lock_version_gte*/0
	/*end*/
	/*if isNotEmpty(lock_version_lte) */
	AND "lock_version" <= /*lock_version_lte*/0
	/*end*/