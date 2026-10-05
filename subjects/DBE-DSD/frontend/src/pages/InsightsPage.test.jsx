import { fireEvent, render, screen, within } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import data from '../data/mlInsights.json';
import InsightsPage from './InsightsPage.jsx';

const pct = (v) => `${Math.round(v * 100)}%`;

describe('InsightsPage', () => {
  it('leads with the held-out-topic results, not the leaky random split', () => {
    render(<InsightsPage />);
    const { test, leaveOneTopicOut } = data.classification;
    const tile = (label) => screen.getByText(label).closest('.kpi');
    expect(within(tile('Filed correctly, unseen topics')).getByText(pct(test.accuracy))).toBeTruthy();
    expect(within(tile('Across all 21 topics')).getByText(pct(leaveOneTopicOut.accuracy))).toBeTruthy();

    const bars = within(screen.getByRole('list', { name: 'Accuracy by evaluation method' })).getAllByRole('listitem');
    expect(bars.map((b) => b.textContent)).toEqual([
      expect.stringContaining('Random 5-fold split (leaks)'),
      expect.stringContaining('Whole topics held out'),
      expect.stringContaining('Chance'),
    ]);
    expect(bars[1].className).toContain('is-highlight');
  });

  it('shows the locked test set confusion matrix with every test document counted', () => {
    render(<InsightsPage />);
    const table = screen.getByRole('table', { name: /Locked test set, counts of documents/ });
    const counts = within(table).getAllByRole('cell').map((cell) => Number(cell.textContent));
    expect(counts.reduce((a, b) => a + b, 0)).toBe(data.classification.test.documents);
  });

  it('switches to clustering with the arrow keys and shows one map panel per category', () => {
    render(<InsightsPage />);
    const classification = screen.getByRole('tab', { name: 'Classification' });
    fireEvent.keyDown(classification, { key: 'ArrowRight' });
    const clustering = screen.getByRole('tab', { name: 'Clustering' });
    expect(clustering.getAttribute('aria-selected')).toBe('true');
    expect(screen.getByRole('tabpanel', { name: 'Clustering' }).hidden).toBe(false);

    const map = screen.getByRole('list', { name: 't-SNE map of documents by category' });
    expect(within(map).getAllByRole('listitem')).toHaveLength(data.classification.categories.length);
    expect(screen.getByRole('heading', { name: 'Algorithms compared' })).toBeTruthy();
  });
});
