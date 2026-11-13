import type { WorkflowGroup } from '@/shared/types/workflowGroup';

interface Line { assetId: number; assetTag: string; name: string }

export function WorkflowGroupList({ groups, lines, selected, onSelect, statusLabel, reasonLabel, selectable = 'PENDING' }: {
  groups: WorkflowGroup[];
  lines: Line[];
  selected: string[];
  onSelect: (ids: string[]) => void;
  statusLabel: (status: WorkflowGroup['status']) => string;
  reasonLabel: string;
  selectable?: WorkflowGroup['status'] | null;
}) {
  return <div className="candidate-list">{groups.map(group => {
    const assets = group.assetIds.map(id => lines.find(line => line.assetId === id));
    const content = <><strong>{assets.map((line, index) => line ? `${line.assetTag} ${line.name}` : `#${group.assetIds[index]}`).join(', ') || group.groupId}</strong>
      {group.allocationIds.length > 0 && <small> #{group.allocationIds.join(', #')}</small>}
      <small>{statusLabel(group.status)}</small>{group.reason && <small>{reasonLabel}: {group.reason}</small>}</>;
    return group.status === selectable ? <label className="candidate-row" key={group.groupId}>
      <input type="checkbox" checked={selected.includes(group.groupId)} onChange={() => onSelect(selected.includes(group.groupId) ? selected.filter(id => id !== group.groupId) : [...selected, group.groupId])} />
      <span>{content}</span></label> : <div className="candidate-row" key={group.groupId}><span>{content}</span></div>;
  })}</div>;
}
