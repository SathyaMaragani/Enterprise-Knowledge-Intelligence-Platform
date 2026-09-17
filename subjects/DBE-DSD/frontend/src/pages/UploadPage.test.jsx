import { fireEvent, render, screen, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import App from '../App.jsx';
import { AuthProvider } from '../auth/AuthContext.jsx';
import { jsonResponse, tokenFor } from '../test-utils.js';
import { uploadProblem } from './UploadPage.jsx';

const MANAGER = { username: 'alice_mgr', fullName: 'Alice Manager', roles: ['MANAGER'], permissions: ['DOCUMENT_CREATE', 'DOCUMENT_READ'] };
const EMPLOYEE = { username: 'bob_eng', fullName: 'Bob Engineer', roles: ['EMPLOYEE'], permissions: ['DOCUMENT_READ'] };
const CATEGORIES = [
  { id: 1, name: 'Finance', description: '' },
  { id: 2, name: 'HR', description: '' },
];

const textFile = (name, content = 'hello world', type = 'text/plain') => new File([content], name, { type });

const uploaded = (id, title) =>
  jsonResponse(200, { id, title, status: 'INDEXED', documentType: 'MD', owner: 'alice_mgr', content: { rawText: 'hello world' }, chunks: [] });

/** Routes by method and path; POST /api/documents answers with `upload(form)`. */
function renderApp(path, { profile = MANAGER, upload, extra = {} } = {}) {
  const forms = [];
  vi.stubGlobal(
    'fetch',
    vi.fn(async (url, init = {}) => {
      const key = `${init.method ?? 'GET'} ${url}`;
      if (extra[key]) return extra[key];
      if (key === 'GET /api/auth/me') return jsonResponse(200, profile);
      if (key === 'GET /api/categories') return jsonResponse(200, CATEGORIES);
      if (key === 'POST /api/documents') {
        forms.push(init.body);
        return upload(init.body);
      }
      return jsonResponse(404, { message: `No mock for ${key}` });
    }),
  );
  render(
    <MemoryRouter initialEntries={[path]}>
      <AuthProvider>
        <App />
      </AuthProvider>
    </MemoryRouter>,
  );
  return forms;
}

describe('uploadProblem', () => {
  it('accepts text and markdown up to 1 MB', () => {
    expect(uploadProblem(textFile('notes.md'))).toBeNull();
    expect(uploadProblem(textFile('NOTES.TXT'))).toBeNull();
    expect(uploadProblem(textFile('x.markdown'))).toBeNull();
    expect(uploadProblem(new File(['a'.repeat(1024 * 1024)], 'max.txt'))).toBeNull();
  });

  it('rejects missing, unsupported, empty and oversized files', () => {
    expect(uploadProblem(null)).toMatch(/Choose a file/);
    expect(uploadProblem(textFile('report.pdf'))).toMatch(/Only \.txt and \.md/);
    expect(uploadProblem(textFile('empty.txt', ''))).toMatch(/empty/);
    expect(uploadProblem(new File(['a'.repeat(1024 * 1024 + 1)], 'big.txt'))).toMatch(/at most 1 MB/);
  });
});

describe('upload page', () => {
  beforeEach(() => {
    sessionStorage.clear();
    sessionStorage.setItem('eip.token', tokenFor('alice_mgr'));
  });

  afterEach(() => vi.unstubAllGlobals());

  it('uploads the file with its fields and opens the new document', async () => {
    const forms = renderApp('/upload', {
      upload: () => jsonResponse(201, { id: 31, title: 'Budget notes', status: 'INDEXED', chunkCount: 3, vectorsStored: true }),
      extra: { 'GET /api/documents/31': uploaded(31, 'Budget notes') },
    });
    await screen.findByRole('option', { name: 'Finance' });

    fireEvent.change(screen.getByLabelText('File'), { target: { files: [textFile('budget-notes.md')] } });
    expect(screen.getByText(/budget-notes\.md · 11 B/)).toBeTruthy();
    expect(screen.getByLabelText('Title').getAttribute('placeholder')).toBe('budget-notes');
    fireEvent.change(screen.getByLabelText('Title'), { target: { value: '  Budget notes ' } });
    fireEvent.change(screen.getByLabelText('Description'), { target: { value: 'Q3 planning' } });
    fireEvent.change(screen.getByLabelText('Category'), { target: { value: 'Finance' } });
    fireEvent.click(screen.getByRole('button', { name: 'Upload document' }));

    expect(await screen.findByText(/Uploaded “Budget notes” as 3 chunks\. Search finds it by title, description and the meaning of its content\./)).toBeTruthy();
    const form = forms[0];
    expect(form.get('file').name).toBe('budget-notes.md');
    expect(form.get('title')).toBe('Budget notes');
    expect(form.get('description')).toBe('Q3 planning');
    expect(form.get('category')).toBe('Finance');
    expect(form.get('department')).toBeNull();
  });

  it('says when the upload is keyword-searchable only', async () => {
    renderApp('/upload', {
      upload: () => jsonResponse(201, { id: 32, title: 'n', status: 'UPLOADED', chunkCount: 1, vectorsStored: false }),
      extra: { 'GET /api/documents/32': uploaded(32, 'n') },
    });
    await screen.findByRole('option', { name: 'HR' });

    fireEvent.change(screen.getByLabelText('File'), { target: { files: [textFile('n.txt')] } });
    fireEvent.change(screen.getByLabelText('Category'), { target: { value: 'HR' } });
    fireEvent.click(screen.getByRole('button', { name: 'Upload document' }));

    expect(await screen.findByText(/semantic search was not available, so its content is not searchable yet/)).toBeTruthy();
  });

  it('catches mistakes before sending anything', async () => {
    const forms = renderApp('/upload', { upload: () => jsonResponse(500, {}) });
    await screen.findByRole('option', { name: 'HR' });

    fireEvent.click(screen.getByRole('button', { name: 'Upload document' }));
    expect(screen.getByRole('alert').textContent).toBe('Choose a file to upload.');

    fireEvent.change(screen.getByLabelText('File'), { target: { files: [textFile('scan.pdf')] } });
    expect(screen.getByRole('alert').textContent).toBe('Only .txt and .md files can be uploaded.');

    fireEvent.change(screen.getByLabelText('File'), { target: { files: [textFile('ok.txt')] } });
    fireEvent.click(screen.getByRole('button', { name: 'Upload document' }));
    expect(screen.getByRole('alert').textContent).toBe('Choose a category.');

    expect(forms).toHaveLength(0);
  });

  it('shows the backend rejection and lets the user try again', async () => {
    renderApp('/upload', { upload: () => jsonResponse(400, { message: 'The file must be UTF-8 text' }) });
    await screen.findByRole('option', { name: 'HR' });

    fireEvent.change(screen.getByLabelText('File'), { target: { files: [textFile('latin.txt')] } });
    fireEvent.change(screen.getByLabelText('Category'), { target: { value: 'HR' } });
    fireEvent.click(screen.getByRole('button', { name: 'Upload document' }));

    expect((await screen.findByRole('alert')).textContent).toBe('The file must be UTF-8 text');
    expect(screen.getByRole('button', { name: 'Upload document' }).disabled).toBe(false);
  });

  it('says so when permissions cannot be checked', async () => {
    renderApp('/upload', {
      upload: () => jsonResponse(500, {}),
      extra: { 'GET /api/auth/me': jsonResponse(500, { message: 'boom' }) },
    });

    expect((await screen.findByRole('alert')).textContent).toMatch(/could not be checked/);
  });

  it('tells a role without upload permission, instead of showing the form', async () => {
    renderApp('/upload', { profile: EMPLOYEE, upload: () => jsonResponse(500, {}) });

    expect((await screen.findByRole('alert')).textContent).toMatch(/Your role cannot upload documents/);
    expect(screen.queryByLabelText('File')).toBeNull();
  });
});

describe('upload entry points', () => {
  beforeEach(() => {
    sessionStorage.clear();
    sessionStorage.setItem('eip.token', tokenFor('alice_mgr'));
  });

  afterEach(() => vi.unstubAllGlobals());

  const dashboardRoutes = {
    'GET /api/documents': jsonResponse(200, []),
    'GET /api/search/vector/collection-info': jsonResponse(200, { pointsCount: 0 }),
    'GET /api/documents/page?page=0&size=10': jsonResponse(200, { items: [], page: 0, size: 10, totalItems: 0, totalPages: 0 }),
  };

  it('lets a manager start an upload from the dashboard and the repository', async () => {
    renderApp('/', { upload: () => jsonResponse(500, {}), extra: dashboardRoutes });

    const action = await screen.findByRole('link', { name: 'Upload document' });
    fireEvent.click(action);
    expect(await screen.findByRole('heading', { name: 'Upload a document' })).toBeTruthy();

    fireEvent.click(screen.getByRole('link', { name: '← Repository' }));
    const button = await screen.findByRole('link', { name: 'Upload document' });
    expect(button.getAttribute('href')).toBe('/upload');
  });

  it('keeps uploading closed for an employee', async () => {
    renderApp('/repository', { profile: EMPLOYEE, upload: () => jsonResponse(500, {}), extra: dashboardRoutes });

    await screen.findByRole('button', { name: /Account menu for Bob Engineer/ });
    expect(screen.queryByRole('link', { name: 'Upload document' })).toBeNull();

    fireEvent.click(within(screen.getByRole('navigation', { name: 'Main' })).getByRole('link', { name: 'Dashboard' }));
    await screen.findByRole('heading', { name: /^Good (morning|afternoon|evening), Bob$/ });
    expect(screen.queryByRole('link', { name: 'Upload document' })).toBeNull();
  });
});
