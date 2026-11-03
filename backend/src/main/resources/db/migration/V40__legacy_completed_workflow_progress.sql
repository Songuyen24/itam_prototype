-- Historical workflows completed atomically. Preserve that result as one historical group;
-- do not infer independently confirmed lines from today's relationship topology.
INSERT INTO workflow_progress(transaction_id,groups)
SELECT t.transaction_id,jsonb_build_array(jsonb_build_object(
    'groupId','legacy-completed','status','COMPLETED',
    'assetIds',COALESCE((SELECT jsonb_agg(ta.asset_id ORDER BY ta.asset_id)
        FROM transaction_assets ta WHERE ta.transaction_id=t.transaction_id),'[]'::jsonb),
    'allocationIds','[]'::jsonb))
FROM transactions t WHERE t.type IN ('HANDOVER','RECOVERY','DISPOSAL') AND t.status='COMPLETED'
AND NOT EXISTS(SELECT 1 FROM workflow_progress p WHERE p.transaction_id=t.transaction_id);
