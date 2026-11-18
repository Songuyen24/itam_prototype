import type { WorkflowGroup } from "@/shared/types/workflowGroup";

interface Line {
  assetId: number;
  assetTag: string;
  name: string;
}

interface WorkflowGroupListProps {
  groups: WorkflowGroup[];
  lines: Line[];
  selected: string[];
  onSelect: (ids: string[]) => void;
  statusLabel: (status: WorkflowGroup["status"]) => string;
  reasonLabel: string;
  selectable?: WorkflowGroup["status"] | null;
}

export function WorkflowGroupList({
  groups,
  lines,
  selected,
  onSelect,
  statusLabel,
  reasonLabel,
  selectable = "PENDING",
}: WorkflowGroupListProps) {
  return (
    <div className="candidate-list">
      {groups.map((group) => {
        const assets = group.assetIds.map((id) =>
          lines.find((line) => line.assetId === id),
        );
        const assetSummary =
          assets
            .map((line, index) =>
              line
                ? `${line.assetTag} ${line.name}`
                : `#${group.assetIds[index]}`,
            )
            .join(", ") || group.groupId;

        const content = (
          <>
            <strong>{assetSummary}</strong>
            {group.allocationIds.length > 0 && (
              <small> #{group.allocationIds.join(", #")}</small>
            )}
            <small>{statusLabel(group.status)}</small>
            {group.reason && (
              <small>
                {reasonLabel}: {group.reason}
              </small>
            )}
          </>
        );

        const isChecked = selected.includes(group.groupId);
        const handleToggle = () => {
          onSelect(
            isChecked
              ? selected.filter((id) => id !== group.groupId)
              : [...selected, group.groupId],
          );
        };

        if (group.status === selectable) {
          return (
            <label className="candidate-row" key={group.groupId}>
              <input
                type="checkbox"
                checked={isChecked}
                onChange={handleToggle}
              />
              <span>{content}</span>
            </label>
          );
        }

        return (
          <div className="candidate-row" key={group.groupId}>
            <span>{content}</span>
          </div>
        );
      })}
    </div>
  );
}
