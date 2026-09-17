import { describe, expect, it } from 'vitest';
import { fileTypeLabel, humanize } from './DocumentBits.jsx';

describe('humanize', () => {
  it('turns API constants and keys into readable labels', () => {
    expect(humanize('COMPLETED')).toBe('Completed');
    expect(humanize('authors')).toBe('Authors');
    expect(humanize('page_count')).toBe('Page count');
    expect(humanize('chunkerVersion')).toBe('Chunker version');
    expect(humanize('in-progress')).toBe('In progress');
  });

  it('leaves missing values missing', () => {
    expect(humanize(undefined)).toBeUndefined();
    expect(humanize(null)).toBeNull();
  });
});

describe('fileTypeLabel', () => {
  it('names common document types and shows unknown ones as they are', () => {
    expect(fileTypeLabel('application/vnd.openxmlformats-officedocument.spreadsheetml.sheet')).toBe('Excel spreadsheet');
    expect(fileTypeLabel('text/markdown')).toBe('Markdown');
    expect(fileTypeLabel('image/png')).toBe('image/png');
  });
});
