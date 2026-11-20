/*
 * 웹 자원 JS 가 ES2020 을 넘는 문법을 쓰지 않는지 확인한다 (README 의 지원 브라우저: Chrome·Edge 80, Firefox 74, Safari 13.1).
 * 파서 없이 주석·문자열을 지운 코드에서 대표적인 ES2021 이후 문법과 클래스 필드(ES2022)를 찾는다.
 */
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readdirSync, readFileSync } from 'node:fs';
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';

const jsDir = fileURLToPath(new URL('../../main/resources/META-INF/resources/s2-kit/js/', import.meta.url));

/** Removes comments, strings and regex-like literals well enough for the checks below | 아래 검사에 충분할 만큼 주석·문자열 제거 */
const codeOnly = (source) =>
  source
    .replace(/\/\*[\s\S]*?\*\//g, '')
    .replace(/(^|[^:\\])\/\/.*$/gm, '$1')
    .replace(/`(?:\\[\s\S]|[^`\\])*`/g, '``')
    .replace(/'(?:\\.|[^'\\\n])*'/g, "''")
    .replace(/"(?:\\.|[^"\\\n])*"/g, '""');

const AFTER_ES2020 = [
  [/\b\d+_\d/, 'numeric separator (ES2021)'],
  [/(\?\?|\|\||&&)=/, 'logical assignment (ES2021)'],
  [/\.replaceAll\(/, 'String#replaceAll (ES2021)'],
  [/\bPromise\.any\(/, 'Promise.any (ES2021)'],
  [/\.at\(/, 'Array#at (ES2022)'],
  [/\bObject\.hasOwn\(/, 'Object.hasOwn (ES2022)'],
  [/\.findLast(Index)?\(/, 'Array#findLast (ES2023)'],
  [/\bstructuredClone\(/, 'structuredClone'],
  [/^\s*(static\s+)?#[A-Za-z_]/m, 'private class member (ES2022)'],
  [/\bstatic\s*\{/, 'static block (ES2022)']
];

/**
 * Class fields (`_date;`, `x = 1;`, `handler = () => {}`) are ES2022: walks each class body and reports members that are
 * not methods | 클래스 필드는 ES2022: 클래스 본문을 따라가며 메서드가 아닌 멤버를 찾는다
 */
const classFields = (code) => {
  const fields = [];
  const classStart = /\bclass\s+[A-Za-z_$][\w$]*(?:\s+extends\s+[\w$.]+)?\s*\{/g;
  let match;
  while ((match = classStart.exec(code)) !== null) {
    let depth = 1;
    let member = '';
    for (let i = match.index + match[0].length; i < code.length && depth > 0; i++) {
      const ch = code[i];
      if (depth === 1) {
        // A '=' before any '(' is a field (`x = 1`, `handler = () => {`); in a method it belongs to a default parameter
        // | '(' 보다 앞의 '=' 는 필드, 메서드에서는 기본 매개변수의 '='
        const eq = member.indexOf('=');
        const paren = member.indexOf('(');
        const assigns = eq !== -1 && (paren === -1 || eq < paren);
        if ((ch === ';' && (assigns || !member.includes('('))) || (ch === '{' && assigns)) {
          if (member.trim()) fields.push(member.trim());
        } else if (ch === '{' && /\bstatic\s*$/.test(member)) {
          fields.push('static {');
        }
        member = ch === ';' || ch === '{' || ch === '}' ? '' : member + ch;
      }
      if (ch === '{') depth++;
      if (ch === '}') depth--;
    }
  }
  return fields;
};

for (const file of readdirSync(jsDir).filter((name) => name.endsWith('.js'))) {
  test(`${file} stays within ES2020`, () => {
    const code = codeOnly(readFileSync(join(jsDir, file), 'utf8'));
    for (const [pattern, feature] of AFTER_ES2020) {
      const match = code.match(pattern);
      assert.equal(match, null, `${file} uses ${feature}: ${match && match[0]}`);
    }
    assert.deepEqual(classFields(code), [], `${file} declares class fields (ES2022); initialize them in the constructor`);
  });
}
