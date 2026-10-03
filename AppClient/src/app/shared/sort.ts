/**
 * Comparator for the lists' default order: the most recent timestamp first, rows
 * without one last. ISO 8601 strings sort chronologically as plain strings, so no
 * Date parsing is needed. Used to pre-sort rows, which the tables keep as their
 * "unsorted" state - no column claims aria-sort for an order it does not show.
 */
export function byMostRecent<T>(key: keyof T): (a: T, b: T) => number {
  return (a, b) => {
    const x = a[key] as unknown as string | null | undefined;
    const y = b[key] as unknown as string | null | undefined;
    if (!x) return y ? 1 : 0;
    if (!y) return -1;
    return x < y ? 1 : x > y ? -1 : 0;
  };
}
