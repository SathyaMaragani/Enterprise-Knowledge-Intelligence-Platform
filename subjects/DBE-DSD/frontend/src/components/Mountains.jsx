import useSvgId from './useSvgId.js';

/** Layered dusk mountain silhouettes with a snow-lit ridge and low mist. */
export default function Mountains({ className = '' }) {
  const id = useSvgId();

  return (
    <svg
      className={`mountains ${className}`}
      viewBox="0 0 960 420"
      preserveAspectRatio="xMidYMax slice"
      aria-hidden="true"
      focusable="false"
    >
      <defs>
        <linearGradient id={`${id}far`} x1="0" y1="0" x2="0" y2="1">
          <stop offset="0" stopColor="#5b4a8f" stopOpacity="0.75" />
          <stop offset="1" stopColor="#1c1b3d" stopOpacity="0.9" />
        </linearGradient>
        <linearGradient id={`${id}mid`} x1="0" y1="0" x2="0" y2="1">
          <stop offset="0" stopColor="#2c2d5a" />
          <stop offset="1" stopColor="#12152e" />
        </linearGradient>
        <linearGradient id={`${id}near`} x1="0" y1="0" x2="0" y2="1">
          <stop offset="0" stopColor="#1a2140" />
          <stop offset="1" stopColor="#080c1a" />
        </linearGradient>
        <radialGradient id={`${id}mist`} cx="0.5" cy="0.5" r="0.5">
          <stop offset="0" stopColor="#c9d2f5" stopOpacity="0.28" />
          <stop offset="1" stopColor="#c9d2f5" stopOpacity="0" />
        </radialGradient>
      </defs>

      <polygon
        fill={`url(#${id}far)`}
        points="0,250 60,228 120,242 190,196 250,222 320,176 380,206 450,168 520,202 600,156 660,196 740,172 820,212 900,186 960,206 960,420 0,420"
      />
      <polygon
        fill={`url(#${id}mid)`}
        points="0,322 80,286 140,298 210,238 260,268 330,204 370,232 420,216 470,258 540,228 600,278 680,222 720,246 790,212 850,258 910,238 960,266 960,420 0,420"
      />
      <polygon
        fill={`url(#${id}near)`}
        points="0,378 90,350 170,362 260,320 330,338 420,290 482,248 540,186 580,228 612,212 662,268 722,298 800,326 880,318 960,346 960,420 0,420"
      />
      <path
        d="M482 248 540 186l40 42M540 186l-18 52-16 10M612 212l-14 30"
        fill="none"
        stroke="#e2e8ff"
        strokeOpacity="0.5"
        strokeWidth="2"
        strokeLinejoin="round"
      />
      <ellipse cx="300" cy="372" rx="360" ry="46" fill={`url(#${id}mist)`} />
      <ellipse cx="760" cy="384" rx="300" ry="40" fill={`url(#${id}mist)`} />
      <polygon
        fill="#060a14"
        points="0,402 120,384 240,396 360,374 480,392 600,378 720,398 840,382 960,394 960,420 0,420"
      />
    </svg>
  );
}
