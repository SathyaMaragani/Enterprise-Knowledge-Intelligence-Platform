import { beforeEach, describe, expect, it } from 'vitest';
import { makeToken } from '../test-utils.js';
import { clearToken, decodeToken, readToken, saveToken, sessionFromToken } from './session.js';

describe('decodeToken', () => {
  it('decodes a base64url payload, including non-ASCII text', () => {
    const token = makeToken({ sub: 'zoë_ops', exp: 2000000000 });
    expect(decodeToken(token)).toEqual({ sub: 'zoë_ops', exp: 2000000000 });
  });

  it('decodes payloads whose base64url form uses - and _', () => {
    // '?>?' encodes to base64 'Pz4/', which base64url writes as 'Pz4_'.
    const token = makeToken({ sub: '?>?', exp: 1 });
    expect(token.split('.')[1]).toMatch(/[-_]/);
    expect(decodeToken(token).sub).toBe('?>?');
  });

  it.each([
    ['null', null],
    ['a number', 42],
    ['one segment', 'abc'],
    ['two segments', 'a.b'],
    ['four segments', 'a.b.c.d'],
    ['invalid base64', 'a.!!!.c'],
    ['a payload that is not JSON', `a.${btoa('not json')}.c`],
    ['a payload that is a JSON string', `a.${btoa('"text"')}.c`],
  ])('rejects %s', (_label, token) => {
    expect(decodeToken(token)).toBeNull();
  });
});

describe('sessionFromToken', () => {
  const now = Date.UTC(2026, 0, 1);
  const nowSeconds = now / 1000;

  it('describes a valid token', () => {
    const token = makeToken({ sub: 'alice_mgr', exp: nowSeconds + 60 });
    expect(sessionFromToken(token, now)).toEqual({
      token,
      username: 'alice_mgr',
      expiresAt: (nowSeconds + 60) * 1000,
    });
  });

  it('treats a token as expired from the exact moment of exp', () => {
    expect(sessionFromToken(makeToken({ sub: 'a', exp: nowSeconds }), now)).toBeNull();
    expect(sessionFromToken(makeToken({ sub: 'a', exp: nowSeconds - 1 }), now)).toBeNull();
  });

  it('rejects tokens missing sub or exp', () => {
    expect(sessionFromToken(makeToken({ exp: nowSeconds + 60 }), now)).toBeNull();
    expect(sessionFromToken(makeToken({ sub: 'a' }), now)).toBeNull();
    expect(sessionFromToken(makeToken({ sub: 'a', exp: 'soon' }), now)).toBeNull();
  });
});

describe('token storage', () => {
  beforeEach(() => sessionStorage.clear());

  it('round-trips through sessionStorage, not localStorage', () => {
    saveToken('t');
    expect(readToken()).toBe('t');
    expect(localStorage.length).toBe(0);
    clearToken();
    expect(readToken()).toBeNull();
  });
});
