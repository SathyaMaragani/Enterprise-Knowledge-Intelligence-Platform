import { lazy, Suspense, useCallback, useMemo, useState } from 'react';
import { buildKnowledgeGraph, NODE_COLORS, project } from './knowledgeGraphData.js';
import { canRenderScene, SceneBoundary } from './ThreeBackdrop.jsx';
import useSvgId from './useSvgId.js';

const KnowledgeGraphScene = lazy(() => import('./KnowledgeGraphScene.jsx'));

const RADIUS = { topic: 0.1, document: 0.045, person: 0.06 };
const LEGEND = [
  { kind: 'document', label: 'Documents' },
  { kind: 'person', label: 'People' },
  { kind: 'topic', label: 'Topics' },
];

/** The same graph as a still image: for no WebGL, reduced motion, or while the scene loads. */
function KnowledgeGraphStill({ graph }) {
  const id = useSvgId();
  const points = graph.nodes.map((node) => project(node.position));
  const xs = points.map(([x]) => x);
  const ys = points.map(([, y]) => y);
  const pad = 0.35;
  const minX = Math.min(...xs) - pad;
  const minY = Math.min(...ys) - pad;
  const width = Math.max(...xs) + pad - minX;
  const height = Math.max(...ys) + pad - minY;

  return (
    <svg className="knowledge-graph__still" viewBox={`${minX} ${minY} ${width} ${height}`} focusable="false">
      <defs>
        <filter id={`${id}glow`} x="-100%" y="-100%" width="300%" height="300%">
          <feGaussianBlur stdDeviation="0.035" result="blur" />
          <feMerge>
            <feMergeNode in="blur" />
            <feMergeNode in="SourceGraphic" />
          </feMerge>
        </filter>
      </defs>
      <g stroke="rgba(148, 163, 255, 0.3)" strokeWidth="0.012">
        {graph.edges.map(([a, b]) => (
          <line key={`${a}-${b}`} x1={points[a][0]} y1={points[a][1]} x2={points[b][0]} y2={points[b][1]} />
        ))}
      </g>
      <g filter={`url(#${id}glow)`}>
        {graph.nodes.map((node) => (
          <circle
            key={node.id}
            className={`knowledge-graph__node--${node.kind}`}
            cx={points[node.id][0]}
            cy={points[node.id][1]}
            r={RADIUS[node.kind]}
            fill={NODE_COLORS[node.kind]}
          />
        ))}
      </g>
      {graph.nodes
        .filter((node) => node.label)
        .map((node) => (
          <text
            key={node.label}
            x={points[node.id][0]}
            y={points[node.id][1] - 0.2}
            textAnchor="middle"
            fontSize="0.2"
            fill="#e0e7ff"
          >
            {node.label}
          </text>
        ))}
    </svg>
  );
}

/**
 * The sign-in page's knowledge graph. Animated in 3D when WebGL runs and motion
 * is welcome; otherwise, or if the scene fails, the still version shows instead.
 */
export default function KnowledgeGraph({ className = '' }) {
  const graph = useMemo(() => buildKnowledgeGraph(), []);
  const [animated, setAnimated] = useState(canRenderScene);
  const stopAnimating = useCallback(() => setAnimated(false), []);
  const still = <KnowledgeGraphStill graph={graph} />;

  return (
    <figure className={`knowledge-graph ${className}`} aria-hidden="true">
      {animated ? (
        <SceneBoundary fallback={still}>
          <Suspense fallback={still}>
            <KnowledgeGraphScene graph={graph} onUnavailable={stopAnimating} />
          </Suspense>
        </SceneBoundary>
      ) : (
        still
      )}
      <ul className="knowledge-graph__legend">
        {LEGEND.map(({ kind, label }) => (
          <li key={kind}>
            <span className="knowledge-graph__dot" style={{ background: NODE_COLORS[kind], color: NODE_COLORS[kind] }} />
            {label}
          </li>
        ))}
      </ul>
    </figure>
  );
}
