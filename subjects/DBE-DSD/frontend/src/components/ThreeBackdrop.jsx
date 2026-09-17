import { Component, lazy, Suspense, useState } from 'react';

// The threeui scenes and their stylesheet are a separate chunk, fetched only when
// a backdrop will actually render. Pages paint immediately on their CSS fallback.
const ThreeScene = lazy(() => import('./threeScenes.jsx'));

/**
 * True when an animated WebGL scene is appropriate: the browser supports WebGL
 * and the user has not asked for reduced motion.
 */
export function canRenderScene() {
  if (typeof window === 'undefined' || typeof window.WebGLRenderingContext === 'undefined') {
    return false;
  }
  if (window.matchMedia?.('(prefers-reduced-motion: reduce)').matches) {
    return false;
  }
  try {
    const canvas = document.createElement('canvas');
    return Boolean(canvas.getContext('webgl2') || canvas.getContext('webgl'));
  } catch {
    return false;
  }
}

// A decorative scene must never take a page down with it. If WebGL setup throws,
// the fallback (by default nothing, leaving the CSS behind the scene) shows instead.
export class SceneBoundary extends Component {
  state = { failed: false };

  static getDerivedStateFromError() {
    return { failed: true };
  }

  render() {
    return this.state.failed ? (this.props.fallback ?? null) : this.props.children;
  }
}

/** A decorative threeui scene, or nothing when it cannot or should not render. */
export default function ThreeBackdrop({ scene, className = '' }) {
  const [enabled] = useState(canRenderScene);
  if (!enabled) {
    return null;
  }
  return (
    <div className={`three-backdrop ${className}`} aria-hidden="true">
      <SceneBoundary>
        <Suspense fallback={null}>
          <ThreeScene scene={scene} />
        </Suspense>
      </SceneBoundary>
    </div>
  );
}
