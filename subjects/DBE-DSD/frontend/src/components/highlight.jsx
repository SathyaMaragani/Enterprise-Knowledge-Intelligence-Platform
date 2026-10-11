/**
 * Splits `text` into plain and highlighted runs. Overlapping or touching
 * matches merge into one highlight, since a character is either marked or not.
 */
export function highlightSegments(text, matches) {
  const merged = [];
  for (const { start, end } of [...matches].sort((a, b) => a.start - b.start)) {
    const last = merged.at(-1);
    if (last && start <= last.end) {
      last.end = Math.max(last.end, end);
    } else {
      merged.push({ start, end });
    }
  }
  const segments = [];
  let cursor = 0;
  for (const { start, end } of merged) {
    if (start > cursor) segments.push({ text: text.slice(cursor, start), marked: false });
    segments.push({ text: text.slice(start, end), marked: true });
    cursor = end;
  }
  if (cursor < text.length) segments.push({ text: text.slice(cursor), marked: false });
  return segments;
}

/** `text` with the `matches` ranges (`start` inclusive, `end` exclusive) wrapped in <mark>. */
export function Highlighted({ text, matches }) {
  return highlightSegments(text, matches ?? []).map((segment, index) =>
    segment.marked ? <mark key={index}>{segment.text}</mark> : <span key={index}>{segment.text}</span>,
  );
}
