/*
 * S2Util 중 DOM 이 필요 없는 함수 시험 (node --test).
 */
import { test } from 'node:test';
import assert from 'node:assert/strict';

import { S2Util } from '../../main/resources/META-INF/resources/s2-kit/js/s2.util.js';

test('jsonToQueryString encodes keys and values and keeps 0 and false', () => {
  assert.equal(
    S2Util.jsonToQueryString({ q: 'A&B #1+2=3', n: 0, f: false, e: '', u: null, k: '한' }, '/list.do'),
    '/list.do?q=A%26B%20%231%2B2%3D3&n=0&f=false&k=%ED%95%9C'
  );
  assert.equal(S2Util.jsonToQueryString({ ids: [1, 2] }, '/a'), '/a?ids=1&ids=2');
  assert.equal(S2Util.jsonToQueryString({ a: 1 }), 'a=1');
});

test('jsonToQueryString merges into the URL query and keeps the hash', () => {
  assert.equal(S2Util.jsonToQueryString({ page: 2 }, '/a?page=1&t=abc%3D%3D&x=a+b'), '/a?page=2&t=abc%3D%3D&x=a%20b');
  assert.equal(S2Util.jsonToQueryString({ a: 1 }, '/a#top'), '/a?a=1#top');
  assert.equal(S2Util.jsonToQueryString({ a: 1 }, '/a?b=raw=value'), '/a?b=raw%3Dvalue&a=1');
});

test('queryStringToJson decodes and splits at the first equals sign', () => {
  assert.deepEqual(S2Util.queryStringToJson('?name=%ED%99%8D&key=&flag&t=ab==&s=a+b'), {
    name: '홍',
    key: '',
    flag: null,
    t: 'ab==',
    s: 'a b'
  });
  assert.deepEqual(S2Util.queryStringToJson(''), {});
});

test('getContentDispositionFilename prefers filename*', () => {
  const name = S2Util.getContentDispositionFilename;
  // What S2FileUtil.formatContentDisposition sends | S2FileUtil.formatContentDisposition 이 보내는 형식
  assert.equal(name(`attachment; filename="__ __.pdf"; filename*=UTF-8''%ED%95%9C%20%EA%B8%80.pdf`), '한 글.pdf');
  assert.equal(name('attachment; filename="report \\"q\\".pdf"'), 'report "q".pdf');
  assert.equal(name('attachment; filename=%ED%95%9C.pdf'), '한.pdf');
  assert.equal(name('inline; filename=a.txt'), 'a.txt');
  assert.equal(name('attachment'), '');
  assert.equal(name(null), '');
});

test('josa follows the last syllable or digit', () => {
  assert.equal(S2Util.formatMessage('{0|을} {1|으로} {2|이} {3|는}', '사과', '서울', '1', '책'), '사과를 서울로 1이 책은');
  assert.equal(S2Util.appendJosa('길', '으로'), '길로');
  assert.equal(S2Util.appendJosa('Apple', '을'), 'Apple을(를)');
  assert.equal(S2Util.appendJosa('"사과"', '이'), '"사과"가');
  assert.ok(S2Util.hasBatchim('밥'));
  assert.ok(!S2Util.hasBatchim('바'));
});

test('byteLength and truncateBytes count EUC-KR or UTF-8 bytes', () => {
  assert.equal(S2Util.byteLength('가a', 'EUC-KR'), 3);
  assert.equal(S2Util.byteLength('가a', 'UTF-8'), 4);
  assert.equal(S2Util.byteLength('😀', 'UTF-8'), 4);
  assert.equal(S2Util.byteLength('가a'), 3, 'EUC-KR by default');
  assert.equal(S2Util.truncateBytes('가나다', 7, 'UTF-8'), '가나');
  assert.equal(S2Util.truncateBytes('가나다', 5, 'EUC-KR'), '가나');
  assert.equal(S2Util.truncateBytes('a😀b', 4, 'UTF-8'), 'a', 'never splits a surrogate pair');
  assert.equal(S2Util.truncateBytes('abc', 10, 'UTF-8'), 'abc');
});

test('escapeHtml and unescapeHtml round trip', () => {
  assert.equal(S2Util.escapeHtml('<a href="x">&\'</a>'), '&lt;a href=&quot;x&quot;&gt;&amp;&#39;&lt;/a&gt;');
  assert.equal(S2Util.escapeHtml('a=b/`', true), 'a&#x3D;b&#x2F;&#x60;');
  assert.equal(S2Util.unescapeHtml('&amp;lt;'), '&lt;', 'unescapes once');
  assert.equal(S2Util.escapeHtml(null), '');
});

test('case conversion, comma and byte formatting', () => {
  assert.equal(S2Util.toCamelCase('user_name-id'), 'userNameId');
  assert.equal(S2Util.toPascalCase('userName'), 'UserName');
  assert.equal(S2Util.toSnakeCase('userNameID'), 'user_name_id');
  assert.equal(S2Util.toKebabCase('User Name'), 'user-name');
  assert.equal(S2Util.comma(1234567), '1,234,567');
  assert.equal(S2Util.formatBytes(1536).formattedValue + S2Util.formatBytes(1536).unit, '1.5KB');
  assert.equal(S2Util.formatBytes(0).unit, 'B');
});

test('the 401 redirect and the byte charset can be configured', () => {
  assert.equal(S2Util.loginUrl, '/login');
  assert.equal(S2Util.byteCharset, 'EUC-KR');
});
