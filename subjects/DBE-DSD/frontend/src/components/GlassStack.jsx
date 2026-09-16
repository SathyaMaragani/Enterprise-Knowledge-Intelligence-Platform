import useSvgId from './useSvgId.js';
import { CubeIcon, FileTextIcon, SparkleIcon, UsersIcon } from './icons.jsx';

const SLABS = [
  { label: 'Documents', Icon: FileTextIcon, tone: 'blue' },
  { label: 'People', Icon: UsersIcon, tone: 'cyan' },
  { label: 'Knowledge', Icon: CubeIcon, tone: 'indigo' },
  { label: 'Possibilities', Icon: SparkleIcon, tone: 'violet' },
];

const ORBIT_LABELS = [
  { text: ['Search', 'faster'], className: 'orbit-label--search' },
  { text: ['Connect', 'ideas'], className: 'orbit-label--connect' },
  { text: ['Unlock', 'insights'], className: 'orbit-label--unlock' },
  { text: ['Build', 'tomorrow'], className: 'orbit-label--build' },
];

/** The sign-in illustration: four glass slabs stacked in 3D, ringed by orbits. */
export default function GlassStack({ className = '' }) {
  const id = useSvgId();

  return (
    <div className={`glass-stack ${className}`} aria-hidden="true">
      <svg className="glass-stack__orbits" viewBox="0 0 420 600" focusable="false">
        <defs>
          <linearGradient id={`${id}orbit`} x1="0" y1="0" x2="1" y2="1">
            <stop offset="0" stopColor="#60a5fa" stopOpacity="0" />
            <stop offset="0.5" stopColor="#7dd3fc" stopOpacity="0.8" />
            <stop offset="1" stopColor="#a78bfa" stopOpacity="0" />
          </linearGradient>
        </defs>
        <g fill="none" stroke={`url(#${id}orbit)`} strokeWidth="1.4">
          <ellipse cx="210" cy="150" rx="190" ry="70" transform="rotate(-18 210 150)" />
          <ellipse cx="200" cy="330" rx="200" ry="80" transform="rotate(14 200 330)" />
          <ellipse cx="230" cy="500" rx="170" ry="60" transform="rotate(-10 230 500)" />
        </g>
        {[
          [44, 118],
          [382, 190],
          [22, 346],
          [372, 300],
          [320, 548],
        ].map(([cx, cy]) => (
          <circle key={`${cx}-${cy}`} className="orbit-dot" cx={cx} cy={cy} r="3" />
        ))}
      </svg>

      {SLABS.map(({ label, Icon, tone }, index) => (
        <div key={label} className={`slab slab--${tone}`} style={{ '--i': index }}>
          <div className="slab__face slab__face--top" />
          <div className="slab__face slab__face--side" />
          <div className="slab__face slab__face--front">
            <Icon size={34} />
            <span>{label}</span>
          </div>
        </div>
      ))}

      {ORBIT_LABELS.map(({ text, className: labelClass }) => (
        <span key={labelClass} className={`orbit-label ${labelClass}`}>
          {text[0]}
          <br />
          {text[1]}
        </span>
      ))}
    </div>
  );
}
