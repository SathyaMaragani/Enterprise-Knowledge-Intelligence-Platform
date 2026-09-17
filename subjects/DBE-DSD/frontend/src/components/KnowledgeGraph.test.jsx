import { render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import KnowledgeGraph from './KnowledgeGraph.jsx';
import { buildKnowledgeGraph, TOPICS } from './knowledgeGraphData.js';
import { SceneBoundary } from './ThreeBackdrop.jsx';

describe('KnowledgeGraph', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
  });

  it('shows the still graph when WebGL is unavailable, hidden from assistive technology', () => {
    const { container } = render(<KnowledgeGraph />);

    const figure = container.querySelector('figure');
    expect(figure.getAttribute('aria-hidden')).toBe('true');
    expect(container.querySelector('canvas')).toBeNull();
    expect(container.querySelectorAll('circle')).toHaveLength(buildKnowledgeGraph().nodes.length);
    expect(container.querySelectorAll('line')).toHaveLength(buildKnowledgeGraph().edges.length);
    expect([...container.querySelectorAll('text')].map((text) => text.textContent)).toEqual(TOPICS);
    expect([...container.querySelectorAll('li')].map((item) => item.textContent)).toEqual([
      'Documents',
      'People',
      'Topics',
    ]);
  });

  it('falls back to the still graph when the 3D scene cannot start', async () => {
    // WebGL looks available, but the context is unusable, so the renderer throws.
    vi.stubGlobal('WebGLRenderingContext', function WebGLRenderingContext() {});
    const getContext = vi.spyOn(HTMLCanvasElement.prototype, 'getContext').mockReturnValue({});
    vi.spyOn(console, 'error').mockImplementation(() => {});
    vi.spyOn(console, 'warn').mockImplementation(() => {});

    const { container } = render(<KnowledgeGraph />);

    // three.js asks for a context with attributes; the feature check does not.
    await vi.waitFor(() => expect(getContext.mock.calls.some((call) => call.length === 2)).toBe(true), {
      timeout: 10000,
    });
    await vi.waitFor(() => expect(container.querySelector('.knowledge-graph__still')).not.toBeNull());
    expect(container.querySelector('.knowledge-graph__canvas')).toBeNull();
    expect(container.querySelector('canvas')).toBeNull();
  });
});

describe('SceneBoundary', () => {
  it('shows its fallback when a scene throws', () => {
    vi.spyOn(console, 'error').mockImplementation(() => {});
    function Broken() {
      throw new Error('WebGL context lost');
    }
    render(
      <SceneBoundary fallback={<p>still image</p>}>
        <Broken />
      </SceneBoundary>,
    );
    expect(screen.getByText('still image')).toBeTruthy();
    vi.restoreAllMocks();
  });
});
