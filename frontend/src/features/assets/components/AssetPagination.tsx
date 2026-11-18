import type { Dispatch, SetStateAction } from "react";
import { useTranslation } from "react-i18next";
import ChevronLeftOutlinedIcon from "@mui/icons-material/ChevronLeftOutlined";
import ChevronRightOutlinedIcon from "@mui/icons-material/ChevronRightOutlined";

type Props = {
  currentPage: number;
  totalPages: number;
  totalElements: number;
  visibleCount: number;
  isLoading: boolean;
  onPageChange: Dispatch<SetStateAction<number>>;
};

export function AssetPagination({
  currentPage,
  totalPages,
  totalElements,
  visibleCount,
  isLoading,
  onPageChange,
}: Props) {
  const { t } = useTranslation(["assets", "common"]);
  if (totalPages <= 1) return null;

  return (
    <div
      className="pagination-container"
      style={{
        padding: "16px",
        borderTop: "1px solid var(--border-color)",
        display: "flex",
        justifyContent: "space-between",
        alignItems: "center",
      }}
    >
      <div style={{ fontSize: "13px", color: "var(--text-muted)" }}>
        {t("assets:list.showing", {
          count: visibleCount,
          total: totalElements,
        })}
      </div>

      <div style={{ display: "flex", gap: "6px" }}>
        <button
          type="button"
          className="btn btn-secondary"
          disabled={currentPage === 0 || isLoading}
          onClick={() => onPageChange((page) => Math.max(0, page - 1))}
        >
          <ChevronLeftOutlinedIcon fontSize="small" aria-hidden="true" />{" "}
          {t("common:pagination.previous")}
        </button>

        {Array.from({ length: totalPages }, (_, i) => i).map((pageIdx) => {
          if (
            pageIdx === 0 ||
            pageIdx === totalPages - 1 ||
            Math.abs(pageIdx - currentPage) <= 2
          ) {
            return (
              <button
                key={pageIdx}
                type="button"
                className={`btn ${currentPage === pageIdx ? "btn-primary" : "btn-secondary"}`}
                style={{ minWidth: "36px" }}
                onClick={() => onPageChange(pageIdx)}
              >
                {pageIdx + 1}
              </button>
            );
          }
          if (
            (pageIdx === 1 && currentPage > 3) ||
            (pageIdx === totalPages - 2 && currentPage < totalPages - 4)
          ) {
            return (
              <span
                key={pageIdx}
                style={{ padding: "6px 4px", color: "var(--text-muted)" }}
              >
                ...
              </span>
            );
          }
          return null;
        })}

        <button
          type="button"
          className="btn btn-secondary"
          disabled={currentPage >= totalPages - 1 || isLoading}
          onClick={() =>
            onPageChange((page) => Math.min(totalPages - 1, page + 1))
          }
        >
          {t("common:pagination.next")}{" "}
          <ChevronRightOutlinedIcon fontSize="small" aria-hidden="true" />
        </button>
      </div>
    </div>
  );
}
