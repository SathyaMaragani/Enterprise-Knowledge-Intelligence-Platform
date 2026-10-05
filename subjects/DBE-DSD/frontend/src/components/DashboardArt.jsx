// Decorative illustrations for the dashboard and sign-in page: plain SVG, no
// images to fetch. All are aria-hidden; the text around them carries the meaning.
import useSvgId from './useSvgId.js';

/** Documents turning into search: a green document, a search bar and floating tiles. */
export function HeroArt({ className = '' }) {
  const id = useSvgId();

  return (
    <svg className={`hero-art ${className}`} viewBox="0 0 420 300" aria-hidden="true" focusable="false">
      <defs>
        <linearGradient id={`${id}green`} x1="0" y1="0" x2="1" y2="1">
          <stop offset="0" stopColor="#3fd18d" />
          <stop offset="1" stopColor="#0c8150" />
        </linearGradient>
        <linearGradient id={`${id}paper`} x1="0" y1="0" x2="0" y2="1">
          <stop offset="0" stopColor="#ffffff" />
          <stop offset="0.7" stopColor="#f7fbf9" />
          <stop offset="1" stopColor="#fde4e1" />
        </linearGradient>
        <linearGradient id={`${id}coral`} x1="0" y1="0" x2="1" y2="1">
          <stop offset="0" stopColor="#ffb08a" />
          <stop offset="1" stopColor="#f06548" />
        </linearGradient>
        <radialGradient id={`${id}floor`} cx="0.5" cy="0.5" r="0.5">
          <stop offset="0" stopColor="#21a36a" stopOpacity="0.28" />
          <stop offset="1" stopColor="#21a36a" stopOpacity="0" />
        </radialGradient>
        <filter id={`${id}shadow`} x="-30%" y="-30%" width="160%" height="170%">
          <feDropShadow dx="0" dy="10" stdDeviation="10" floodColor="#0f5133" floodOpacity="0.16" />
        </filter>
      </defs>

      <ellipse cx="215" cy="262" rx="150" ry="22" fill={`url(#${id}floor)`} />

      {/* Back pages, tilted away. */}
      <g filter={`url(#${id}shadow)`}>
        <rect x="205" y="18" width="140" height="196" rx="16" fill={`url(#${id}paper)`} transform="rotate(9 275 116)" />
        <g transform="rotate(9 275 116)" stroke="#bfe6d1" strokeWidth="5" strokeLinecap="round">
          <path d="M228 52h62M228 68h84M228 84h70" />
        </g>
        <rect x="178" y="34" width="132" height="190" rx="16" fill="#ffffff" transform="rotate(3 244 129)" />
        <g transform="rotate(3 244 129)" stroke="#e3ebe7" strokeWidth="5" strokeLinecap="round">
          <path d="M198 66h56M198 82h84M198 98h70M198 114h80" />
        </g>
      </g>

      {/* The green document in front. */}
      <g filter={`url(#${id}shadow)`}>
        <rect x="118" y="58" width="132" height="182" rx="18" fill={`url(#${id}green)`} />
        <rect x="118" y="58" width="132" height="182" rx="18" fill="none" stroke="#ffffff" strokeOpacity="0.35" />
        <path d="M156 104h34l18 18v50a6 6 0 0 1-6 6h-46a6 6 0 0 1-6-6v-62a6 6 0 0 1 6-6z" fill="#ffffff" fillOpacity="0.92" />
        <path d="M190 104v14a4 4 0 0 0 4 4h14" fill="#c9f1dc" />
        <path d="M160 134h36M160 146h36M160 158h24" stroke="#1d9b63" strokeWidth="4" strokeLinecap="round" />
      </g>

      {/* Search bar crossing the documents. */}
      <g filter={`url(#${id}shadow)`}>
        <rect x="64" y="176" width="236" height="38" rx="19" fill="#ffffff" />
        <circle cx="86" cy="195" r="7" fill="none" stroke="#f08a2c" strokeWidth="3" />
        <path d="m91 200 5 5" stroke="#f08a2c" strokeWidth="3" strokeLinecap="round" />
        <path d="M106 195h70" stroke="#dfe7e3" strokeWidth="6" strokeLinecap="round" />
      </g>

      {/* Floating tiles. */}
      <g filter={`url(#${id}shadow)`}>
        <rect x="40" y="44" width="56" height="56" rx="16" fill="#dcf6e8" transform="rotate(-10 68 72)" />
        <circle cx="66" cy="70" r="10" fill="none" stroke="#11865a" strokeWidth="3.5" />
        <path d="m73.5 77.5 7 7" stroke="#11865a" strokeWidth="3.5" strokeLinecap="round" />

        <rect x="338" y="96" width="60" height="60" rx="18" fill="#d5f2f1" transform="rotate(8 368 126)" />
        <path
          d="M362 112a7 7 0 0 0-10 6 6 6 0 0 0-2 10 6 6 0 0 0 4 9 6 6 0 0 0 8 3zM374 112a7 7 0 0 1 10 6 6 6 0 0 1 2 10 6 6 0 0 1-4 9 6 6 0 0 1-8 3z"
          fill="none"
          stroke="#169a97"
          strokeWidth="3"
          strokeLinejoin="round"
        />
        <path d="M368 110v32" stroke="#169a97" strokeWidth="3" />

        <rect x="300" y="214" width="58" height="58" rx="17" fill={`url(#${id}coral)`} transform="rotate(-12 329 243)" />
        <path d="M316 256v-8M325 256v-15M334 256v-11M343 256v-19" stroke="#ffffff" strokeWidth="4" strokeLinecap="round" />

        <circle cx="178" cy="256" r="15" fill="#ffffff" />
        <path d="M171 253a7.5 7.5 0 0 1 13.5-2.5M185 259a7.5 7.5 0 0 1-13.5 2.5" fill="none" stroke="#18a065" strokeWidth="2.6" strokeLinecap="round" />
        <path d="M184.5 245.5v5h-5M171.5 266.5v-5h5" fill="none" stroke="#18a065" strokeWidth="2.6" strokeLinecap="round" strokeLinejoin="round" />
      </g>
    </svg>
  );
}

/** "Nothing here yet": a stack of pages under a magnifier, with a little confetti. */
export function EmptyDocumentsArt() {
  const id = useSvgId();

  return (
    <svg className="empty-art" viewBox="0 0 320 170" aria-hidden="true" focusable="false">
      <defs>
        <linearGradient id={`${id}page`} x1="0" y1="0" x2="1" y2="1">
          <stop offset="0" stopColor="#8a96ad" />
          <stop offset="1" stopColor="#4f5b73" />
        </linearGradient>
        <linearGradient id={`${id}lens`} x1="0" y1="0" x2="1" y2="1">
          <stop offset="0" stopColor="#2fc27d" />
          <stop offset="1" stopColor="#0c7d4c" />
        </linearGradient>
        <filter id={`${id}shadow`} x="-30%" y="-30%" width="160%" height="170%">
          <feDropShadow dx="0" dy="6" stdDeviation="6" floodColor="#1f2a3d" floodOpacity="0.18" />
        </filter>
      </defs>

      <path d="M40 120c30 30 70 32 100 10M190 132c40 20 80 6 96-26" fill="none" stroke="#9fd8bb" strokeWidth="1.6" strokeDasharray="4 5" />

      <g filter={`url(#${id}shadow)`}>
        <rect x="150" y="12" width="78" height="104" rx="9" fill={`url(#${id}page)`} transform="rotate(10 189 64)" />
        <rect x="134" y="18" width="78" height="106" rx="9" fill="#f4f6fa" transform="rotate(4 173 71)" />
        <g transform="rotate(4 173 71)" stroke="#b8c2d3" strokeWidth="4" strokeLinecap="round">
          <path d="M148 40h50M148 54h50M148 68h40M148 82h46" />
        </g>
        <rect x="56" y="62" width="38" height="44" rx="6" fill="#bfeed6" transform="rotate(-14 75 84)" />
        <path d="M66 78h18M66 87h14" stroke="#41b07a" strokeWidth="3" strokeLinecap="round" transform="rotate(-14 75 84)" />
        <rect x="232" y="104" width="30" height="36" rx="5" fill="#dbe9fb" transform="rotate(12 247 122)" />
        <rect x="112" y="76" width="70" height="70" rx="18" fill={`url(#${id}lens)`} />
        <circle cx="144" cy="108" r="14" fill="none" stroke="#ffffff" strokeWidth="5" />
        <path d="m154 118 10 10" stroke="#ffffff" strokeWidth="5" strokeLinecap="round" />
      </g>

      <g>
        <rect x="88" y="34" width="6" height="6" rx="1" fill="#f6b23c" transform="rotate(45 91 37)" />
        <rect x="46" y="98" width="7" height="7" rx="1" fill="#f0754f" transform="rotate(45 49 101)" />
        <rect x="262" y="58" width="7" height="7" rx="1" fill="#f0754f" transform="rotate(45 265 61)" />
        <circle cx="250" cy="42" r="3.5" fill="#5b9cf0" />
        <circle cx="244" cy="96" r="3.5" fill="#f6b23c" />
        <circle cx="236" cy="20" r="3" fill="#49c4b4" />
        <circle cx="70" cy="150" r="3" fill="#5b9cf0" />
      </g>
    </svg>
  );
}

/** The soft wave along the bottom of a tinted card; colored by the card's tone. */
export function Wave() {
  return (
    <svg className="wave" viewBox="0 0 300 60" preserveAspectRatio="none" aria-hidden="true" focusable="false">
      <path d="M0 34C46 14 84 46 132 36s84-30 120-14 34 4 48-4v48H0z" fill="currentColor" opacity="0.45" />
      <path d="M0 44c40-14 78 10 124 2s90-26 130-6 32 6 46-2v22H0z" fill="currentColor" opacity="0.7" />
    </svg>
  );
}

/** A hand-drawn curved arrow pointing back up at the upload button. */
export function ScribbleArrow() {
  return (
    <svg className="scribble-arrow" viewBox="0 0 60 44" aria-hidden="true" focusable="false">
      <path d="M54 34C40 42 16 40 8 14" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" />
      <path d="M3 21 8 13l7 6" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}
