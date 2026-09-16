import { useId } from 'react';

/**
 * A per-instance id safe inside SVG `url(#...)` references. React's ids can
 * contain characters such as ':' that those references do not parse.
 */
export default function useSvgId() {
  return `svg${useId().replace(/[^a-zA-Z0-9_-]/g, '')}`;
}
