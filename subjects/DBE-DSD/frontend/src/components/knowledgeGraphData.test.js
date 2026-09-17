import { describe, expect, it } from 'vitest';
import { buildKnowledgeGraph, TOPICS } from './knowledgeGraphData.js';

describe('buildKnowledgeGraph', () => {
  const graph = buildKnowledgeGraph();

  it('is the same graph every time', () => {
    expect(buildKnowledgeGraph()).toEqual(graph);
    expect(buildKnowledgeGraph({ seed: 8 })).not.toEqual(graph);
  });

  it('has labelled topics, their documents and connecting people', () => {
    const byKind = (kind) => graph.nodes.filter((node) => node.kind === kind);
    expect(byKind('topic').map((node) => node.label)).toEqual(TOPICS);
    expect(byKind('document')).toHaveLength(TOPICS.length * 6);
    expect(byKind('person')).toHaveLength(10);
    graph.nodes.forEach((node, index) => expect(node.id).toBe(index));
  });

  it('draws valid, distinct edges that reach every node', () => {
    const keys = graph.edges.map(([a, b]) => `${a}-${b}`);
    expect(new Set(keys).size).toBe(keys.length);

    const touched = new Set();
    for (const [a, b] of graph.edges) {
      expect(a).toBeLessThan(b);
      expect(graph.nodes[b]).toBeDefined();
      touched.add(a).add(b);
    }
    expect(touched.size).toBe(graph.nodes.length);
  });

  it('files each document under exactly one topic', () => {
    for (const document of graph.nodes.filter((node) => node.kind === 'document')) {
      const topics = graph.edges.filter(
        ([a, b]) => (a === document.id || b === document.id) && graph.nodes[Math.min(a, b)].kind === 'topic',
      );
      expect(topics).toHaveLength(1);
    }
  });

  it('stays within a bounded space, so the camera frames all of it', () => {
    for (const { position } of graph.nodes) {
      expect(Math.hypot(...position)).toBeLessThan(3);
    }
  });
});
