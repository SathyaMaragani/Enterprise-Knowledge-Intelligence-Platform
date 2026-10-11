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
  it('accepts PDF, DOCX, text and markdown up to 10 MB', () => {
    expect(uploadProblem(textFile('policy.pdf'))).toBeNull();
    expect(uploadProblem(textFile('handbook.DOCX'))).toBeNull();
    expect(uploadProblem(textFile('notes.md'))).toBeNull();
    expect(uploadProblem(textFile('NOTES.TXT'))).toBeNull();
    expect(uploadProblem(textFile('x.markdown'))).toBeNull();
    expect(uploadProblem(new File([new Uint8Array(10 * 1024 * 1024)], 'max.pdf'))).toBeNull();
  });

  it('rejects missing, unsupported, empty and oversized files', () => {
    expect(uploadProblem(null)).toMatch(/Choose a file/);
    expect(uploadProblem(textFile('legacy.doc'))).toMatch(/Only \.pdf, \.docx, \.txt and \.md/);
    expect(uploadProblem(textFile('empty.txt', ''))).toMatch(/empty/);
    expect(uploadProblem(new File([new Uint8Array(10 * 1024 * 1024 + 1)], 'big.pdf'))).toMatch(/at most 10 MB/);
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
    await within(await screen.findByRole('combobox', { name: 'Category' })).findByRole('option', { name: 'Finance' });

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
    await within(await screen.findByRole('combobox', { name: 'Category' })).findByRole('option', { name: 'HR' });

    fireEvent.change(screen.getByLabelText('File'), { target: { files: [textFile('n.txt')] } });
    fireEvent.change(screen.getByLabelText('Category'), { target: { value: 'HR' } });
    fireEvent.click(screen.getByRole('button', { name: 'Upload document' }));

    expect(await screen.findByText(/semantic search was not available, so its content is not searchable yet/)).toBeTruthy();
  });

  it('catches mistakes before sending anything', async () => {
    const forms = renderApp('/upload', { upload: () => jsonResponse(500, {}) });
    await within(await screen.findByRole('combobox', { name: 'Category' })).findByRole('option', { name: 'HR' });

    fireEvent.click(screen.getByRole('button', { name: 'Upload document' }));
    expect(screen.getByRole('alert').textContent).toBe('Choose a file to upload.');

    fireEvent.change(screen.getByLabelText('File'), { target: { files: [textFile('legacy.doc')] } });
    expect(screen.getByRole('alert').textContent).toBe('Only .pdf, .docx, .txt and .md files can be uploaded.');

    fireEvent.change(screen.getByLabelText('File'), { target: { files: [textFile('ok.txt')] } });
    fireEvent.click(screen.getByRole('button', { name: 'Upload document' }));
    expect(screen.getByRole('alert').textContent).toBe('Choose a category.');

    expect(forms).toHaveLength(0);
  });

  it('shows the backend rejection and lets the user try again', async () => {
    renderApp('/upload', { upload: () => jsonResponse(400, { message: 'The file must be UTF-8 text' }) });
    await within(await screen.findByRole('combobox', { name: 'Category' })).findByRole('option', { name: 'HR' });

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

describe('bulk upload', () => {
  beforeEach(() => {
    sessionStorage.clear();
    sessionStorage.setItem('eip.token', tokenFor('alice_mgr'));
  });

  afterEach(() => vi.unstubAllGlobals());

  const created = (form, id, chunkCount = 1) =>
    jsonResponse(201, { id, title: form.get('file').name, status: 'UPLOADED', chunkCount, vectorsStored: false });
  const names = (forms) => forms.map((form) => form.get('file').name);
  const chooseCategory = async (name) => {
    await within(await screen.findByRole('combobox', { name: 'Category' })).findByRole('option', { name });
    fireEvent.change(screen.getByLabelText('Category'), { target: { value: name } });
  };

  it('uploads several files one at a time, with shared fields and a result for each', async () => {
    let active = 0;
    let mostAtOnce = 0;
    const forms = renderApp('/upload', {
      upload: async (form) => {
        active += 1;
        mostAtOnce = Math.max(mostAtOnce, active);
        await Promise.resolve();
        active -= 1;
        return form.get('file').name === 'a.txt' ? created(form, 41) : created(form, 42, 3);
      },
    });
    await chooseCategory('Finance');

    fireEvent.change(screen.getByLabelText('File'), {
      target: { files: [textFile('a.txt'), textFile('legacy.doc'), textFile('b.md')] },
    });

    // A batch has no title field: each document is named from its file.
    expect(screen.queryByLabelText('Title')).toBeNull();
    expect(screen.getByText('3 files · 33 B')).toBeTruthy();
    const list = screen.getByRole('list', { name: 'Files in this batch' });
    expect(within(list).getAllByRole('listitem')).toHaveLength(3);
    expect(within(list).getByText('Only .pdf, .docx, .txt and .md files can be uploaded.')).toBeTruthy();

    fireEvent.change(screen.getByLabelText('Description'), { target: { value: 'Quarter-end batch' } });
    fireEvent.click(screen.getByRole('button', { name: 'Upload 2 documents' }));

    expect((await screen.findByRole('status')).textContent).toBe('Uploaded 2 of 3 documents.');
    expect(names(forms)).toEqual(['a.txt', 'b.md']);
    expect(mostAtOnce).toBe(1);
    for (const form of forms) {
      expect(form.get('title')).toBeNull();
      expect(form.get('category')).toBe('Finance');
      expect(form.get('description')).toBe('Quarter-end batch');
    }
    expect(within(list).getByRole('link', { name: 'Uploaded · 1 chunk' }).getAttribute('href')).toBe('/documents/41');
    expect(within(list).getByRole('link', { name: 'Uploaded · 3 chunks' }).getAttribute('href')).toBe('/documents/42');
    expect(screen.getByRole('link', { name: 'Go to the repository' }).getAttribute('href')).toBe('/repository');

    // Starting again returns to the single-file form.
    fireEvent.click(screen.getByRole('button', { name: 'Upload more' }));
    expect(screen.getByLabelText('Title')).toBeTruthy();
    expect(screen.queryByRole('list', { name: 'Files in this batch' })).toBeNull();
  });

  it('goes on after a failure, then retries only what failed', async () => {
    const attempts = {};
    const forms = renderApp('/upload', {
      upload: (form) => {
        const name = form.get('file').name;
        attempts[name] = (attempts[name] ?? 0) + 1;
        return name === 'bad.txt' && attempts[name] === 1
          ? jsonResponse(400, { message: 'The file must be UTF-8 text' })
          : created(form, 50 + forms.length);
      },
    });
    await chooseCategory('HR');
    fireEvent.change(screen.getByLabelText('File'), {
      target: { files: [textFile('good.txt'), textFile('bad.txt'), textFile('last.txt')] },
    });

    fireEvent.click(screen.getByRole('button', { name: 'Upload 3 documents' }));

    const retry = await screen.findByRole('button', { name: 'Retry 1 failed upload' });
    expect(names(forms)).toEqual(['good.txt', 'bad.txt', 'last.txt']);
    const list = screen.getByRole('list', { name: 'Files in this batch' });
    expect(within(list).getByText('Failed: The file must be UTF-8 text')).toBeTruthy();
    expect(within(list).getAllByRole('link')).toHaveLength(2);
    expect(screen.queryByRole('status')).toBeNull();

    fireEvent.click(retry);

    expect((await screen.findByRole('status')).textContent).toBe('Uploaded 3 of 3 documents.');
    expect(names(forms)).toEqual(['good.txt', 'bad.txt', 'last.txt', 'bad.txt']);
  });

  it('needs a category, lets files be removed, and offers nothing when no file is usable', async () => {
    const forms = renderApp('/upload', { upload: (form) => created(form, 60) });
    await screen.findByRole('combobox', { name: 'Category' });
    fireEvent.change(screen.getByLabelText('File'), {
      target: { files: [textFile('keep.txt'), textFile('drop.txt'), textFile('old.doc')] },
    });

    fireEvent.click(screen.getByRole('button', { name: 'Upload 2 documents' }));
    expect(screen.getByRole('alert').textContent).toBe('Choose a category.');
    expect(forms).toHaveLength(0);

    fireEvent.click(screen.getByRole('button', { name: 'Remove drop.txt' }));
    expect(screen.getByRole('button', { name: 'Upload 1 document' })).toBeTruthy();
    fireEvent.click(screen.getByRole('button', { name: 'Remove keep.txt' }));
    // One file left: the page is the single-file form again, which names the problem on submit.
    expect(screen.getByLabelText('Title')).toBeTruthy();

    fireEvent.change(screen.getByLabelText('File'), { target: { files: [textFile('x.doc'), textFile('y.exe')] } });
    const nothing = screen.getByRole('button', { name: 'Nothing to upload' });
    expect(nothing.disabled).toBe(true);
  });

  it('keeps the first 20 files of a larger selection and says so', async () => {
    renderApp('/upload', { upload: (form) => created(form, 70) });
    await screen.findByRole('combobox', { name: 'Category' });

    const many = Array.from({ length: 23 }, (_, index) => textFile(`file-${index + 1}.txt`));
    fireEvent.change(screen.getByLabelText('File'), { target: { files: many } });

    expect(screen.getByRole('alert').textContent).toBe('Only the first 20 files were kept. Upload the rest as another batch.');
    const items = within(screen.getByRole('list', { name: 'Files in this batch' })).getAllByRole('listitem');
    expect(items).toHaveLength(20);
    expect(items.at(-1).textContent).toContain('file-20.txt');
  });

  it('takes files dropped on the form', async () => {
    renderApp('/upload', { upload: (form) => created(form, 80) });
    await screen.findByRole('combobox', { name: 'Category' });

    const dropzone = screen.getByText('Choose or drop PDF, DOCX, TXT or MD files').closest('label');
    fireEvent.drop(dropzone, { dataTransfer: { files: [textFile('one.txt'), textFile('two.txt')] } });

    expect(within(screen.getByRole('list', { name: 'Files in this batch' })).getAllByRole('listitem')).toHaveLength(2);
  });

  it('arrives as a batch when several files were dropped on the dashboard', async () => {
    renderApp(
      { pathname: '/upload', state: { files: [textFile('one.txt'), textFile('two.txt'), textFile('three.txt')] } },
      { upload: (form) => created(form, 90) },
    );

    const list = await screen.findByRole('list', { name: 'Files in this batch' });
    expect(within(list).getAllByRole('listitem')).toHaveLength(3);
    expect(screen.getByRole('button', { name: 'Upload 3 documents' })).toBeTruthy();
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

    // The hero's upload button; the empty dashboard offers a second one.
    const [action] = await screen.findAllByRole('link', { name: 'Upload document' });
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
