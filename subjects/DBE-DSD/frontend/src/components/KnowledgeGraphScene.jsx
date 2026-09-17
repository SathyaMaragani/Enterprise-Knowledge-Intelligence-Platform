// Loaded lazily by KnowledgeGraph, so three.js only downloads when the scene runs.
import { useEffect, useRef } from 'react';
import {
  AdditiveBlending,
  BufferAttribute,
  BufferGeometry,
  CanvasTexture,
  Color,
  Group,
  LineBasicMaterial,
  LineSegments,
  PerspectiveCamera,
  Points,
  PointsMaterial,
  Scene,
  Sprite,
  SpriteMaterial,
  SRGBColorSpace,
  WebGLRenderer,
} from 'three';
import { NODE_COLORS } from './knowledgeGraphData.js';

const NODE_SIZES = { topic: 0.62, document: 0.26, person: 0.34 };
const PULSE_COUNT = 16;

/** A soft round glow, so points render as lights rather than squares. */
function glowTexture() {
  const canvas = document.createElement('canvas');
  canvas.width = canvas.height = 64;
  const context = canvas.getContext('2d');
  const gradient = context.createRadialGradient(32, 32, 0, 32, 32, 32);
  gradient.addColorStop(0, 'rgba(255,255,255,1)');
  gradient.addColorStop(0.22, 'rgba(255,255,255,0.85)');
  gradient.addColorStop(0.5, 'rgba(255,255,255,0.22)');
  gradient.addColorStop(1, 'rgba(255,255,255,0)');
  context.fillStyle = gradient;
  context.fillRect(0, 0, 64, 64);
  return new CanvasTexture(canvas);
}

function labelSprite(text) {
  const canvas = document.createElement('canvas');
  const context = canvas.getContext('2d');
  const font = '600 44px Inter, "Segoe UI", system-ui, sans-serif';
  context.font = font;
  canvas.width = Math.ceil(context.measureText(text).width) + 32;
  canvas.height = 64;
  context.font = font;
  context.textAlign = 'center';
  context.textBaseline = 'middle';
  context.shadowColor = 'rgba(167, 139, 250, 0.9)';
  context.shadowBlur = 14;
  context.fillStyle = '#eef2ff';
  context.fillText(text, canvas.width / 2, canvas.height / 2);

  const texture = new CanvasTexture(canvas);
  texture.colorSpace = SRGBColorSpace;
  const sprite = new Sprite(new SpriteMaterial({ map: texture, transparent: true, depthWrite: false }));
  const height = 0.24;
  sprite.scale.set((height * canvas.width) / canvas.height, height, 1);
  return sprite;
}

/**
 * The animated knowledge graph: glowing nodes, faint links, and pulses of light
 * travelling between documents, topics and people. It drifts slowly, leans
 * toward the pointer, and stops rendering while off screen or in a hidden tab.
 */
export default function KnowledgeGraphScene({ graph, onUnavailable }) {
  const containerRef = useRef(null);

  useEffect(() => {
    const container = containerRef.current;
    let renderer;
    try {
      renderer = new WebGLRenderer({ antialias: true, alpha: true, powerPreference: 'low-power' });
    } catch {
      onUnavailable();
      return undefined;
    }
    renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 2));
    renderer.setClearColor(0x000000, 0);
    container.appendChild(renderer.domElement);

    const scene = new Scene();
    const camera = new PerspectiveCamera(42, 1, 0.1, 50);
    // Far enough back that the widest point of the slowly turning graph stays in frame.
    camera.position.set(0, 0.4, 6.9);
    camera.lookAt(0, 0, 0);

    const world = new Group();
    world.rotation.x = 0.7;
    scene.add(world);
    const disposables = [];
    const track = (...items) => {
      disposables.push(...items);
      return items[0];
    };

    const glow = track(glowTexture());

    // Nodes: one point cloud per kind, so each kind has its own size.
    for (const kind of Object.keys(NODE_SIZES)) {
      const positions = graph.nodes.filter((node) => node.kind === kind).flatMap((node) => node.position);
      const geometry = track(new BufferGeometry());
      geometry.setAttribute('position', new BufferAttribute(new Float32Array(positions), 3));
      const material = track(
        new PointsMaterial({
          size: NODE_SIZES[kind],
          color: NODE_COLORS[kind],
          map: glow,
          transparent: true,
          depthWrite: false,
          blending: AdditiveBlending,
        }),
      );
      world.add(new Points(geometry, material));
    }

    // Links, each fading between its two endpoints' colours.
    const linkPositions = new Float32Array(graph.edges.length * 6);
    const linkColors = new Float32Array(graph.edges.length * 6);
    graph.edges.forEach(([a, b], index) => {
      [a, b].forEach((nodeIndex, end) => {
        const node = graph.nodes[nodeIndex];
        linkPositions.set(node.position, index * 6 + end * 3);
        const color = new Color(NODE_COLORS[node.kind]);
        linkColors.set([color.r, color.g, color.b], index * 6 + end * 3);
      });
    });
    const linkGeometry = track(new BufferGeometry());
    linkGeometry.setAttribute('position', new BufferAttribute(linkPositions, 3));
    linkGeometry.setAttribute('color', new BufferAttribute(linkColors, 3));
    const linkMaterial = track(
      new LineBasicMaterial({
        vertexColors: true,
        transparent: true,
        opacity: 0.32,
        depthWrite: false,
        blending: AdditiveBlending,
      }),
    );
    world.add(new LineSegments(linkGeometry, linkMaterial));

    // Pulses: small lights moving along links, like queries finding answers.
    const pulses = Array.from({ length: PULSE_COUNT }, (_, index) => ({
      edge: (index * 7) % graph.edges.length,
      progress: index / PULSE_COUNT,
      speed: 0.18 + ((index * 37) % 10) / 40,
    }));
    const pulsePositions = new Float32Array(PULSE_COUNT * 3);
    const pulseGeometry = track(new BufferGeometry());
    pulseGeometry.setAttribute('position', new BufferAttribute(pulsePositions, 3));
    const pulseMaterial = track(
      new PointsMaterial({
        size: 0.16,
        color: '#e0f2fe',
        map: glow,
        transparent: true,
        depthWrite: false,
        blending: AdditiveBlending,
      }),
    );
    world.add(new Points(pulseGeometry, pulseMaterial));

    for (const node of graph.nodes.filter((item) => item.label)) {
      const sprite = labelSprite(node.label);
      track(sprite.material.map, sprite.material);
      sprite.position.set(node.position[0], node.position[1] + 0.3, node.position[2]);
      world.add(sprite);
    }

    const pointer = { x: 0, y: 0 };
    const lean = { x: 0, y: 0 };
    const onPointerMove = (event) => {
      pointer.x = (event.clientX / window.innerWidth) * 2 - 1;
      pointer.y = (event.clientY / window.innerHeight) * 2 - 1;
    };
    window.addEventListener('pointermove', onPointerMove);

    const resize = () => {
      const { width, height } = container.getBoundingClientRect();
      if (width === 0 || height === 0) return;
      renderer.setSize(width, height, false);
      camera.aspect = width / height;
      camera.updateProjectionMatrix();
    };
    const resizeObserver = new ResizeObserver(resize);
    resizeObserver.observe(container);
    resize();

    let last = performance.now();
    let spin = 0;
    const frame = (now) => {
      const delta = Math.min((now - last) / 1000, 0.05);
      last = now;
      spin += delta * 0.09;
      lean.x += (pointer.y * 0.12 - lean.x) * 0.04;
      lean.y += (pointer.x * 0.22 - lean.y) * 0.04;
      world.rotation.set(0.7 + lean.x, spin + lean.y, 0);

      pulses.forEach((pulse, index) => {
        pulse.progress += delta * pulse.speed;
        if (pulse.progress >= 1) {
          pulse.progress = 0;
          pulse.edge = (pulse.edge + 11 + index) % graph.edges.length;
        }
        const [a, b] = graph.edges[pulse.edge];
        const from = graph.nodes[a].position;
        const to = graph.nodes[b].position;
        for (let axis = 0; axis < 3; axis += 1) {
          pulsePositions[index * 3 + axis] = from[axis] + (to[axis] - from[axis]) * pulse.progress;
        }
      });
      pulseGeometry.attributes.position.needsUpdate = true;
      renderer.render(scene, camera);
    };

    let onScreen = true;
    const updateLoop = () => {
      const running = onScreen && !document.hidden;
      if (running) last = performance.now();
      renderer.setAnimationLoop(running ? frame : null);
    };
    const visibility = new IntersectionObserver(([entry]) => {
      onScreen = entry.isIntersecting;
      updateLoop();
    });
    visibility.observe(container);
    document.addEventListener('visibilitychange', updateLoop);
    updateLoop();

    return () => {
      renderer.setAnimationLoop(null);
      visibility.disconnect();
      resizeObserver.disconnect();
      document.removeEventListener('visibilitychange', updateLoop);
      window.removeEventListener('pointermove', onPointerMove);
      disposables.forEach((item) => item.dispose());
      renderer.dispose();
      renderer.domElement.remove();
    };
  }, [graph, onUnavailable]);

  return <div ref={containerRef} className="knowledge-graph__canvas" />;
}
