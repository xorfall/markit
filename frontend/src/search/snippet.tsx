import { Fragment, type ReactNode } from 'react';

/**
 * Render an API snippet safely. The server returns plain text with matched
 * spans wrapped in <em>…</em> (api-contract §5). We never inject raw HTML: we
 * tokenize on the <em> markers and render matches as a marker <mark>, decoding
 * only the standard HTML entities the server may have escaped.
 */

const ENTITIES: Record<string, string> = {
  '&amp;': '&',
  '&lt;': '<',
  '&gt;': '>',
  '&quot;': '"',
  '&#39;': "'",
  '&apos;': "'",
};

function decode(text: string): string {
  return text.replace(/&(amp|lt|gt|quot|#39|apos);/g, (m) => ENTITIES[m] ?? m);
}

export function renderSnippet(snippet: string): ReactNode {
  // Split on <em>…</em>, keeping the captured inner text (odd indices = matches).
  const parts = snippet.split(/<em>([\s\S]*?)<\/em>/g);
  return parts.map((part, index) => {
    const decoded = decode(part);
    if (index % 2 === 1) {
      return (
        <mark className="marker" key={index}>
          {decoded}
        </mark>
      );
    }
    return <Fragment key={index}>{decoded}</Fragment>;
  });
}
