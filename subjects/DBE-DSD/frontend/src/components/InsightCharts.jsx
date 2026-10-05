// Small chart pieces for the ML insights page: plain SVG and HTML, no chart library.
// Series colors are a validated categorical set for the dark surface (slots 1-4);
// text always uses the text tokens, never a series color.

export const SERIES = ['#3987e5', '#d95926', '#199e70', '#c98500'];

const fmt = (value, digits = 2) => (value == null ? '—' : Number(value).toFixed(digits));

/** Horizontal bars for one measure. `max` fixes the scale so bars compare across lists. */
export function BarList({ rows, max = 1, digits = 2, label }) {
  return (
    <ul className="bar-list" aria-label={label}>
      {rows.map((row) => (
        <li key={row.key ?? row.label} className={`bar-list__row${row.highlight ? ' is-highlight' : ''}`}>
          <span className="bar-list__label">{row.label}</span>
          <span className="bar-list__track" aria-hidden="true">
            <span className="bar-list__bar" style={{ width: `${Math.max(0, Math.min(1, row.value / max)) * 100}%` }} />
          </span>
          <span className="bar-list__value">{fmt(row.value, digits)}</span>
        </li>
      ))}
    </ul>
  );
}

function ticks(min, max, count = 5) {
  const step = (max - min) / (count - 1);
  return Array.from({ length: count }, (_, i) => min + i * step);
}

/**
 * Lines over a shared x axis. One y scale only: a second measure gets its own chart.
 * `reference` draws a dashed guide: { kind: 'diagonal' } | { kind: 'x', value, label, at?: 'bottom' } | { kind: 'y', value, label }.
 */
export function LineChart({ series, xDomain, yDomain, xLabel, yLabel, reference, xFormat = (v) => fmt(v, 1), yFormat = (v) => fmt(v, 2), title }) {
  const W = 520, H = 260, P = { top: 14, right: 18, bottom: 40, left: 52 };
  const x = (v) => P.left + ((v - xDomain[0]) / (xDomain[1] - xDomain[0])) * (W - P.left - P.right);
  const y = (v) => H - P.bottom - ((v - yDomain[0]) / (yDomain[1] - yDomain[0])) * (H - P.top - P.bottom);
  return (
    <figure className="chart">
      {series.length > 1 && (
        <ul className="chart__legend">
          {series.map((s, i) => (
            <li key={s.name}>
              <span className="chart__swatch" style={{ background: s.color ?? SERIES[i] }} />
              {s.name}
            </li>
          ))}
        </ul>
      )}
      <svg viewBox={`0 0 ${W} ${H}`} role="img" aria-label={title}>
        {ticks(...yDomain).map((t) => (
          <g key={`y${t}`}>
            <line x1={P.left} x2={W - P.right} y1={y(t)} y2={y(t)} className="chart__grid" />
            <text x={P.left - 8} y={y(t)} className="chart__tick" textAnchor="end" dominantBaseline="middle">{yFormat(t)}</text>
          </g>
        ))}
        {ticks(...xDomain).map((t) => (
          <text key={`x${t}`} x={x(t)} y={H - P.bottom + 16} className="chart__tick" textAnchor="middle">{xFormat(t)}</text>
        ))}
        <text x={(P.left + W - P.right) / 2} y={H - 6} className="chart__axis-label" textAnchor="middle">{xLabel}</text>
        <text transform={`translate(13 ${(P.top + H - P.bottom) / 2}) rotate(-90)`} className="chart__axis-label" textAnchor="middle">{yLabel}</text>
        {reference?.kind === 'diagonal' && (
          <line x1={x(xDomain[0])} y1={y(yDomain[0])} x2={x(xDomain[1])} y2={y(yDomain[1])} className="chart__reference" />
        )}
        {reference?.kind === 'x' && (
          <g>
            <line x1={x(reference.value)} x2={x(reference.value)} y1={P.top} y2={H - P.bottom} className="chart__reference" />
            <text x={x(reference.value) + 5} y={reference.at === 'bottom' ? H - P.bottom - 8 : P.top + 10} className="chart__tick">
              {reference.label}
            </text>
          </g>
        )}
        {reference?.kind === 'y' && (
          <g>
            <line x1={P.left} x2={W - P.right} y1={y(reference.value)} y2={y(reference.value)} className="chart__reference" />
            <text x={P.left + 6} y={y(reference.value) - 6} className="chart__tick">{reference.label}</text>
          </g>
        )}
        {series.map((s, i) => {
          const color = s.color ?? SERIES[i];
          const path = s.points.map(([px, py], j) => `${j ? 'L' : 'M'}${x(px).toFixed(1)},${y(py).toFixed(1)}`).join('');
          return (
            <g key={s.name}>
              <path d={path} fill="none" stroke={color} strokeWidth="2" strokeLinejoin="round" />
              {s.markers !== false && s.points.map(([px, py], j) => (
                <circle key={j} cx={x(px)} cy={y(py)} r="4" fill={color} className="chart__marker">
                  <title>{`${s.name}: ${xLabel} ${xFormat(px)}, ${yLabel} ${yFormat(py)}${s.notes?.[j] ? ` (${s.notes[j]})` : ''}`}</title>
                </circle>
              ))}
            </g>
          );
        })}
      </svg>
    </figure>
  );
}

/** Confusion matrix as a heat table: one hue, stronger with more documents. */
export function ConfusionMatrix({ labels, matrix, caption }) {
  const max = Math.max(1, ...matrix.flat());
  return (
    <div className="table-scroll">
      <table className="confusion">
        <caption>{caption}</caption>
        <thead>
          <tr>
            <th scope="col">Actual ↓ / Predicted →</th>
            {labels.map((l) => <th key={l} scope="col">{l}</th>)}
          </tr>
        </thead>
        <tbody>
          {matrix.map((row, i) => (
            <tr key={labels[i]}>
              <th scope="row">{labels[i]}</th>
              {row.map((n, j) => (
                <td
                  key={labels[j]}
                  className={i === j ? 'is-diagonal' : undefined}
                  style={{ background: n ? `rgba(57, 135, 229, ${0.12 + 0.68 * (n / max)})` : undefined }}
                >
                  {n}
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

/** One small map per group: that group's documents in color, everyone else as gray context. */
export function MapMultiples({ points, groups, groupOf, project, label }) {
  const xs = points.map((p) => project(p)[0]);
  const ys = points.map((p) => project(p)[1]);
  const [x0, x1, y0, y1] = [Math.min(...xs), Math.max(...xs), Math.min(...ys), Math.max(...ys)];
  const S = 200, pad = 8;
  const sx = (v) => pad + ((v - x0) / (x1 - x0 || 1)) * (S - 2 * pad);
  const sy = (v) => S - pad - ((v - y0) / (y1 - y0 || 1)) * (S - 2 * pad);
  return (
    <ul className="map-multiples" aria-label={label}>
      {groups.map((group) => (
        <li key={group}>
          <svg viewBox={`0 0 ${S} ${S}`} role="img" aria-label={`${group} documents highlighted`}>
            {points.filter((p) => groupOf(p) !== group).map((p, i) => (
              <circle key={`o${i}`} cx={sx(project(p)[0])} cy={sy(project(p)[1])} r="2.2" className="map__context" />
            ))}
            {points.filter((p) => groupOf(p) === group).map((p, i) => (
              <circle key={`g${i}`} cx={sx(project(p)[0])} cy={sy(project(p)[1])} r="3.4" className="map__point">
                <title>{p.title}</title>
              </circle>
            ))}
          </svg>
          <span className="map-multiples__label">{group}</span>
        </li>
      ))}
    </ul>
  );
}
