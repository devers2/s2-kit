/*
 * S2Date 시험 (node --test). 날짜 계산은 지역 시간대를 따르므로 Gradle jsTest 가 TZ=Asia/Seoul 로 실행한다.
 */
import { test } from 'node:test';
import assert from 'node:assert/strict';

import { S2Date } from '../../main/resources/META-INF/resources/s2-kit/js/s2.date.js';

const ymd = (d) => d.format('YYYY-MM-DD');

test('adding months clamps to the last day of the month', () => {
  assert.equal(ymd(S2Date('2025-01-31').add(1, 'month')), '2025-02-28');
  assert.equal(ymd(S2Date('2024-01-31').add(1, 'month')), '2024-02-29');
  assert.equal(ymd(S2Date('2025-03-31').subtract(1, 'month')), '2025-02-28');
  assert.equal(ymd(S2Date('2025-10-30').add(1, 'month')), '2025-11-30');
  assert.equal(ymd(S2Date('2025-12-31').add(2, 'months')), '2026-02-28');
});

test('adding years clamps February 29', () => {
  assert.equal(ymd(S2Date('2024-02-29').add(1, 'year')), '2025-02-28');
  assert.equal(ymd(S2Date('2024-02-29').add(4, 'years')), '2028-02-29');
});

test('days, hours and minutes are added as is', () => {
  assert.equal(ymd(S2Date('2025-02-28').add(1, 'day')), '2025-03-01');
  assert.equal(S2Date('2025-01-01 23:30', 'YYYY-MM-DD HH:mm').add(45, 'minutes').format('YYYY-MM-DD HH:mm'),
    '2025-01-02 00:15');
  assert.throws(() => S2Date('2025-01-01').add(1, 'week'), /Unknown unit/);
});

test('month and year differences follow the clamped months', () => {
  assert.equal(S2Date('2025-02-28').diff(S2Date('2025-01-31'), 'month'), 1);
  assert.equal(S2Date('2025-03-30').diff(S2Date('2025-01-31'), 'month'), 1);
  assert.equal(S2Date('2025-03-31').diff(S2Date('2025-01-31'), 'month'), 2);
  assert.equal(S2Date('2025-01-31').diff(S2Date('2025-03-31'), 'month'), -2);
  assert.equal(S2Date('2025-02-28T00:00:00Z').diff(S2Date('2025-01-31T00:00:00Z'), 'month', false, { tz: 'utc' }), 1);
  assert.equal(S2Date('2026-02-28').diff(S2Date('2024-02-29'), 'year'), 2);
  assert.equal(S2Date('2025-01-02').diff(S2Date('2025-01-01'), 'day'), 1);
});

test('a missing day or month is 1, not today', () => {
  assert.equal(ymd(S2Date('202502', 'YYYYMM')), '2025-02-01');
  assert.ok(S2Date('202502', 'YYYYMM').isValid());
  assert.equal(ymd(S2Date('2024', 'YYYY')), '2024-01-01');
  // Without a year or month the date stays today | 연·월이 없으면 오늘 날짜를 유지
  assert.equal(ymd(S2Date('09:30', 'HH:mm')), ymd(S2Date()));
});

test('parsing validates overflowing dates and formats', () => {
  assert.equal(S2Date('2025-02-30').isValid(), false);
  assert.equal(S2Date('2025/01/02', 'YYYY-MM-DD').isValid(), false);
  assert.equal(ymd(S2Date('20250102')), '2025-01-02');
  assert.equal(S2Date('2025-01-02 12:30 AM', 'YYYY-MM-DD hh:mm A').format('HH:mm'), '00:30');
  assert.equal(S2Date('2025-01-02 01:30 PM', 'YYYY-MM-DD hh:mm A').format('HH:mm'), '13:30');
  assert.equal(S2Date('2025-01-02T03:04:05+09:00').toDate().toISOString(), '2025-01-01T18:04:05.000Z');
  assert.equal(S2Date('2025-01-02 03:04 +0900', 'YYYY-MM-DD HH:mm ZZ').toDate().toISOString(), '2025-01-01T18:04:00.000Z');
});

test('format tokens and time zone modes', () => {
  const d = S2Date('2025-01-02 13:04:05', 'YYYY-MM-DD HH:mm:ss');
  assert.equal(d.format('YYYY.MM.DD dddd A hh:mm:ss'), '2025.01.02 목요일 PM 01:04:05');
  assert.equal(d.format('MMM D ddd', 'en'), 'Jan 2 Thu');
  const utc = S2Date('2025-01-01T00:30:00Z');
  assert.equal(utc.format('YYYY-MM-DD HH:mm Z', 'ko', false, { tz: 'utc' }), '2025-01-01 00:30 +00:00');
  assert.equal(utc.format('HH:mm', 'ko', false, { tz: 'offset', offsetMinutes: -300 }), '19:30');
  assert.equal(S2Date('bad', 'YYYY').format('YYYY', 'ko', true), '');
});

test('start and end of units', () => {
  const d = S2Date('2025-02-15 10:20', 'YYYY-MM-DD HH:mm');
  assert.equal(d.startOf('month').format('YYYY-MM-DD HH:mm'), '2025-02-01 00:00');
  assert.equal(d.endOf('month').format('YYYY-MM-DD HH:mm:ss'), '2025-02-28 23:59:59');
  assert.equal(d.endOf('year').format('YYYY-MM-DD'), '2025-12-31');
  assert.ok(d.isSame('2025-02-01', 'month'));
  assert.ok(d.isAfter('2025-02-14', 'day'));
  assert.ok(S2Date('2024-02-01').isLeapYear());
  assert.equal(S2Date('2024-02-01').daysInMonth(), 29);
});
