import { render } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import ThreeBackdrop, { canRenderScene } from './ThreeBackdrop.jsx';

function fakeWebGL({ reducedMotion }) {
  vi.stubGlobal('WebGLRenderingContext', function WebGLRenderingContext() {});
  vi.stubGlobal('matchMedia', (query) => ({ matches: reducedMotion && query.includes('reduce') }));
  vi.spyOn(HTMLCanvasElement.prototype, 'getContext').mockReturnValue({});
}

describe('canRenderScene', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
  });

  it('is false without WebGL, which is also how tests avoid loading three.js', () => {
    expect(canRenderScene()).toBe(false);
  });

  it('is false when the user prefers reduced motion', () => {
    fakeWebGL({ reducedMotion: true });
    expect(canRenderScene()).toBe(false);
  });

  it('is true with WebGL and no motion preference', () => {
    fakeWebGL({ reducedMotion: false });
    expect(canRenderScene()).toBe(true);
  });

  it('is false when creating a context throws', () => {
    fakeWebGL({ reducedMotion: false });
    HTMLCanvasElement.prototype.getContext.mockImplementation(() => {
      throw new Error('context lost');
    });
    expect(canRenderScene()).toBe(false);
  });
});

describe('ThreeBackdrop', () => {
  it('renders nothing when a scene cannot run', () => {
    const { container } = render(<ThreeBackdrop scene="cloud-field" />);
    expect(container.innerHTML).toBe('');
  });
});
