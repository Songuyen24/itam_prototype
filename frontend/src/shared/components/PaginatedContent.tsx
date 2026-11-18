import { ReactNode, useState } from "react";
import { DocumentPagination } from "@/features/documents/components/DocumentPagination";

interface PaginatedContentProps<T> {
  items: T[];
  children: (page: T[]) => ReactNode;
}

/** Only slices rendering; callers retain the complete data for decisions and submission. */
export function PaginatedContent<T>({
  items,
  children,
}: PaginatedContentProps<T>) {
  const [requestedPage, setPage] = useState(0);
  const totalPages = Math.ceil(items.length / 20);
  const page = Math.min(requestedPage, Math.max(0, totalPages - 1));
  if (requestedPage !== page) setPage(page);
  return (
    <>
      {children(items.slice(page * 20, (page + 1) * 20))}
      {totalPages > 1 && (
        <DocumentPagination
          page={page}
          totalPages={totalPages}
          totalElements={items.length}
          loading={false}
          onPageChange={setPage}
        />
      )}
    </>
  );
}
