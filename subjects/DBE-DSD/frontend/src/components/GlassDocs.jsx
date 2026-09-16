const PILLS = [
  { label: 'Search', className: 'pill--search' },
  { label: 'Discover', className: 'pill--discover' },
  { label: 'Connect', className: 'pill--connect' },
];

/** The dashboard hero illustration: tilted glass pages over a glowing orbit ring. */
export default function GlassDocs({ className = '' }) {
  return (
    <div className={`glass-docs ${className}`} aria-hidden="true">
      <div className="glass-docs__ring" />
      <div className="glass-docs__ring glass-docs__ring--inner" />
      {[0, 1, 2].map((index) => (
        <div key={index} className="glass-doc" style={{ '--i': index }}>
          <span className="glass-doc__line glass-doc__line--title" />
          <span className="glass-doc__line" />
          <span className="glass-doc__line" />
          <span className="glass-doc__line glass-doc__line--short" />
          <span className="glass-doc__block" />
          <span className="glass-doc__line" />
          <span className="glass-doc__line glass-doc__line--short" />
        </div>
      ))}
      {PILLS.map(({ label, className: pillClass }) => (
        <span key={label} className={`orbit-pill ${pillClass}`}>
          <span className="orbit-pill__dot" />
          {label}
        </span>
      ))}
    </div>
  );
}
