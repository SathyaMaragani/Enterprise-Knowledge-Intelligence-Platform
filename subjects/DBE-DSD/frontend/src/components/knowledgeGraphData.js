// The sign-in illustration's data: topics, the documents filed under them, and
// the people who connect documents across topics. Illustrative, not live data;
// the sign-in page runs before anyone is authenticated.

export const TOPICS = ['Policies', 'Finance', 'Engineering', 'People', 'Research', 'Legal'];

export const NODE_COLORS = {
  topic: '#a78bfa',
  document: '#60a5fa',
  person: '#38bdf8',
};

/** Small deterministic PRNG, so the graph is identical on every visit and in tests. */
function mulberry32(seed) {
  let state = seed >>> 0;
  return () => {
    state = (state + 0x6d2b79f5) >>> 0;
    let t = state;
    t = Math.imul(t ^ (t >>> 15), t | 1);
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

const distance = (a, b) => Math.hypot(a[0] - b[0], a[1] - b[1], a[2] - b[2]);

/**
 * @returns {{nodes: {id: number, kind: 'topic'|'document'|'person', label?: string,
 *   position: [number, number, number]}[], edges: [number, number][]}}
 */
export function buildKnowledgeGraph({ seed = 7, documentsPerTopic = 6, people = 10 } = {}) {
  const random = mulberry32(seed);
  const nodes = [];
  const edges = [];
  const link = (a, b) => edges.push(a < b ? [a, b] : [b, a]);

  // Topics sit on a tilted ring, each related to its neighbours.
  TOPICS.forEach((label, index) => {
    const angle = (index / TOPICS.length) * Math.PI * 2;
    nodes.push({
      id: index,
      kind: 'topic',
      label,
      position: [Math.cos(angle) * 1.7, (random() - 0.5) * 1.4, Math.sin(angle) * 1.7],
    });
  });
  TOPICS.forEach((_, index) => link(index, (index + 1) % TOPICS.length));

  // Documents cluster around their topic.
  TOPICS.forEach((_, topic) => {
    const hub = nodes[topic].position;
    for (let n = 0; n < documentsPerTopic; n += 1) {
      const theta = random() * Math.PI * 2;
      const phi = Math.acos(2 * random() - 1);
      const radius = 0.35 + random() * 0.45;
      const id = nodes.length;
      nodes.push({
        id,
        kind: 'document',
        position: [
          hub[0] + radius * Math.sin(phi) * Math.cos(theta),
          hub[1] + radius * Math.cos(phi),
          hub[2] + radius * Math.sin(phi) * Math.sin(theta),
        ],
      });
      link(topic, id);
    }
  });

  // People sit between clusters and connect the documents nearest to them.
  const documents = nodes.filter((node) => node.kind === 'document');
  for (let n = 0; n < people; n += 1) {
    const id = nodes.length;
    const position = [(random() - 0.5) * 2.6, (random() - 0.5) * 2.6, (random() - 0.5) * 2.6];
    nodes.push({ id, kind: 'person', position });
    const nearest = [...documents].sort((a, b) => distance(a.position, position) - distance(b.position, position));
    const count = 2 + Math.floor(random() * 2);
    nearest.slice(0, count).forEach((document) => link(id, document.id));
  }

  return { nodes, edges };
}

/** A fixed three-quarter view for the static illustration: [x, y] in the plane. */
export function project([x, y, z]) {
  return [x * 0.94 + z * 0.34, -y * 0.85 + z * 0.5];
}
