import useSvgId from './useSvgId.js';

/** The EIP stacked-layers mark with its wordmark. */
export default function BrandMark({ subtitle = 'Enterprise Knowledge Intelligence Platform', className = '' }) {
  const gradientId = useSvgId();

  return (
    <div className={`brand ${className}`}>
      <svg className="brand__mark" viewBox="0 0 48 50" aria-hidden="true" focusable="false">
        <defs>
          <linearGradient id={gradientId} x1="0" y1="0" x2="1" y2="1">
            <stop offset="0" stopColor="#60a5fa" />
            <stop offset="1" stopColor="#1d4ed8" />
          </linearGradient>
        </defs>
        <path d="M24 3 44 13.5 24 24 4 13.5z" fill={`url(#${gradientId})`} />
        <path d="M4 21.5 24 32l20-10.5v5.2L24 37.2 4 26.7z" fill={`url(#${gradientId})`} opacity="0.85" />
        <path d="M4 33l20 10.5L44 33v5.2L24 48.7 4 38.2z" fill={`url(#${gradientId})`} opacity="0.7" />
      </svg>
      <div className="brand__text">
        <span className="brand__name">EIP</span>
        {subtitle && <span className="brand__subtitle">{subtitle}</span>}
      </div>
    </div>
  );
}
