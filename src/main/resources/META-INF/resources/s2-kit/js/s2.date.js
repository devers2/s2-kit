/**
 * S2 Kit Library
 *
 * Copyright 2020 - 2026 devers2 (이승수, Daejeon, Korea)
 * Contact: eseungsu.dev@gmail.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * For more information, please see the LICENSE file in the root directory.
 */

/**
 * S2Date 래퍼(Date) 객체를 생성한다.
 * @typedef {S2Day} S2Date
 *
 * @param {Date | string | number | S2Date} [dateInput]
 * - **(생략)**: 현재 시간 (new Date())
 * - **Date**: 기존 Date 객체 (복사하여 사용)
 * - **number**: 1970-01-01 UTC 기준 밀리초 (타임스탬프)
 * - **string**: ISO 8601 형식 문자열 ('YYYY-MM-DDTHH:mm:ssZ', '2025-10-30' / '2025-10-30T15:30:00+09:00')
 * @param {string} [formatStr] - dateInput이 문자열일 때, 파싱할 포맷. (예: 'YYYY-MM-DD', 'YYYY-MM-DD HH:mm Z')
 * @returns {S2Date}
 *
 * @example
 * <script type="module">
 *     import { S2Date } from './js/s2.date.js'; // Thymeleaf: import { S2Date } from '[[@{/js/s2.date.js}]]'
 *     S2Date().add(5, 'days').format('YYYY-MM-DD'); // 오늘 날짜로부터 5일 후의 날짜를 'YYYY-MM-DD' 형식으로 반환
 *     S2Date('2025-10-30').subtract(1, 'month'); // 2025년 10월 30일에서 1개월 뺀 날짜 객체 반환
 *     ...
 * </script>
 *
 * @description
 * ### 지원하는 포맷 토큰
 * YYYY: 4자리 연도(2025)
 * YY: 2자리 연도(25)
 * MM: 2자리 월 (01~12)
 * M: 1자리 월 (1~12)
 * DD: 2자리 일 (01~31)
 * D: 1자리 일 (1~31)
 * A: 오전/오후 (대문자, AM / PM)
 * a: 오전/오후 (소문자, am / pm)
 * HH: 2자리 시간 (24시, 00~23)
 * H: 1자리 시간 (24시, 0~23)
 * hh: 2자리 시간 (12시, 01~12)
 * h: 1자리 시간 (12시, 1~12)
 * mm: 2자리 분 (00~59)
 * m: 1자리 분 (0~59)
 * ss: 2자리 초 (00-59)
 * s: 1자리 초 (0-59)
 * SSS: 3자리 밀리초 (000-999)
 * Z: UTC 오프셋 (콜론 포함, +09:00)
 * ZZ: UTC 오프셋 (붙여쓰기, +0900)
 */
export const S2Date = (dateInput, formatStr) => {
  return new S2Day(dateInput, formatStr);
};

/**
 * @class S2Day
 * @description
 * Date 객체를 감싸는 래퍼 클래스
 * (S2Date() 팩토리 함수를 통해 생성된다.)
 */
class S2Day {
  /*
   * ES2015 호환성을 위해 Public field 로 선언 (ES2022를 지원하는 경우 Private field로 변경 가능, #_date, #_isValid)
   * _date → #_date
   * _isValid → #_isValid
   */
  _date;
  _isValid = true; // 유효한 날짜인지 여부

  constructor(dateInput, formatStr) {
    if (dateInput instanceof S2Day) {
      this._date = new Date(dateInput.toDate());
      this._isValid = dateInput.isValid();
      return;
    }
    if (dateInput === undefined) {
      this._date = new Date();
      return;
    }

    // 문자열 처리 로직
    if (typeof dateInput === 'string') {
      // 파싱 시 누락된 연/월/일의 기본값을 현재 시각으로 설정
      const now = new Date();
      let year = now.getFullYear(),
        month = now.getMonth() + 1,
        day = now.getDate();
      let hour = 0,
        minute = 0,
        second = 0,
        millisecond = 0;

      // 1. 사용할 포맷 결정 (formatStr가 없으면, 'YYYY-MM-DD'와 'YYYYMMDD'를 기본으로 시도)
      const formatsToTry = formatStr ? [formatStr] : ['YYYY-MM-DD', 'YYYYMMDD'];

      let parser;
      let match;
      for (const fmt of formatsToTry) {
        parser = _buildParser(fmt); // 파서 동적 생성
        if (parser.regex.test(dateInput)) {
          match = dateInput.match(parser.regex);
          break; // 매칭 성공
        }
        parser = null; // 실패
      }

      // 2. 파싱 성공 처리 및 값 매핑
      if (parser && match) {
        const values = {};
        parser.groups.forEach((tokenName, index) => {
          values[tokenName] = match[index + 1]; // 숫자가 아닌 AM/PM도 있으므로 parseInt 제거
        });

        // 3. [개선] YY (2자리 연도) 파싱 로직 및 누락된 값 처리
        if (values.YYYY) {
          year = parseInt(values.YYYY);
        } else if (values.YY) {
          const twoDigitYear = parseInt(values.YY);
          const currentYear = now.getFullYear();
          const currentCentury = Math.floor(currentYear / 100) * 100; // 2000
          const pivot = (currentYear + 50) % 100; // 2075 -> 75

          if (twoDigitYear > pivot) {
            year = twoDigitYear + currentCentury - 100; // (예: 80 -> 1980)
          } else {
            year = twoDigitYear + currentCentury; // (예: 15 -> 2015)
          }
        }
        // (values.YYYY나 YY가 없으면 기본값인 'now.getFullYear()' 유지)

        if (values.MM || values.M) {
          month = parseInt(values.MM || values.M);
        }
        if (values.DD || values.D) {
          day = parseInt(values.DD || values.D);
        }

        hour = parseInt(values.HH || values.H || values.hh || values.h || 0);

        // 4. AM/PM 파싱 로직
        const ampm = values.A || values.a;
        if (ampm && (values.hh || values.h)) {
          // 12시간제 토큰이 있을 때만
          const isPM = ampm.toLowerCase() === 'pm';
          if (isPM && hour < 12) {
            // 1:00 PM (hour=1) -> 13
            hour += 12;
          } else if (!isPM && hour === 12) {
            // 12:00 AM (hour=12) -> 0
            hour = 0;
          }
        }

        minute = parseInt(values.mm || values.m || 0);
        second = parseInt(values.ss || values.s || 0);
        millisecond = parseInt(values.SSS || 0);

        // UTC/Timezone 토큰 존재 여부 확인 및 오프셋 분(min) 계산
        const hasOffsetToken = values.Z !== undefined || values.ZZ !== undefined;
        const parseOffsetMinutes = (z, zz) => {
          if (z) {
            // "+09:00" 또는 "-05:30"
            const sign = z[0] === '-' ? -1 : 1;
            const [hh, mm] = z.substring(1).split(':');
            return sign * (parseInt(hh) * 60 + parseInt(mm));
          }
          if (zz) {
            // "+0900" 또는 "-0530"
            const sign = zz[0] === '-' ? -1 : 1;
            const body = zz.substring(1);
            const hh = body.slice(0, 2);
            const mm = body.slice(2, 4);
            return sign * (parseInt(hh) * 60 + parseInt(mm));
          }
          return 0;
        };
        const offsetMinutes = hasOffsetToken ? parseOffsetMinutes(values.Z, values.ZZ) : 0;

        // 5. Date 객체 생성 및 오버플로우 검증
        const monthIndex = month - 1; // Date 객체는 0-based

        // 오프셋 토큰 존재 시: 주어진 값(오프셋 지역의 시각)을 UTC 타임스탬프로 변환
        if (hasOffsetToken) {
          const utcMs =
            Date.UTC(year, monthIndex, day, hour, minute, second, millisecond) -
            offsetMinutes * 60000;
          this._date = new Date(utcMs);
        } else {
          // 로컬 시간 사용
          this._date = new Date(year, monthIndex, day, hour, minute, second, millisecond);
        }

        // 유효성 검증
        // - 오프셋 토큰이 있을 경우: 입력은 "오프셋 로컬 시각"이므로, 내부 UTC 타임스탬프에 오프셋을 더해 같은 로컬 시각을 재구성하여 비교
        // - 없을 경우: 기존 로컬 시각 비교
        const cmpDate = hasOffsetToken
          ? new Date(this._date.getTime() + offsetMinutes * 60000)
          : this._date;
        const yearGetter = hasOffsetToken ? 'getUTCFullYear' : 'getFullYear';
        const monthGetter = hasOffsetToken ? 'getUTCMonth' : 'getMonth';
        const dateGetter = hasOffsetToken ? 'getUTCDate' : 'getDate';
        const hourGetter = hasOffsetToken ? 'getUTCHours' : 'getHours';
        const minuteGetter = hasOffsetToken ? 'getUTCMinutes' : 'getMinutes';
        const secondGetter = hasOffsetToken ? 'getUTCSeconds' : 'getSeconds';
        const msGetter = hasOffsetToken ? 'getUTCMilliseconds' : 'getMilliseconds';

        // 날짜 오버플로우 검증 (예: 2025-02-30 -> 2025-03-02 처럼 넘어가는 경우 _isValid를 false로 설정)
        if (
          cmpDate[yearGetter]() !== year ||
          cmpDate[monthGetter]() !== monthIndex ||
          cmpDate[dateGetter]() !== day ||
          cmpDate[hourGetter]() !== hour ||
          cmpDate[minuteGetter]() !== minute ||
          cmpDate[secondGetter]() !== second ||
          cmpDate[msGetter]() !== millisecond
        ) {
          this._isValid = false; // 플래그를 false로 설정
        }
      } else if (formatStr) {
        // formatStr가 주어졌는데 파싱 실패
        this._isValid = false;
        this._date = new Date(NaN); // Invalid Date
      } else {
        // formatStr가 없고, 기본 파싱(YYYY-MM-DD 등)도 실패 (표준 ISO 문자열('...T...') 또는 'abc' 등)
        // ISO 8601 문자열(Z, +HH:mm 포함)은 이 부분에서 JS 표준 파서에 의해 처리됨.
        this._date = new Date(dateInput);
      }
    } else {
      // --- 문자열이 아님 (number, Date 객체) ---
      this._date = new Date(dateInput);
    }

    // 최종 검증 (예: 'abc' -> Invalid Date): getTime()은 Invalid Date에 대해 NaN을 반환한다.
    if (isNaN(this._date.getTime())) {
      this._isValid = false;
    }
  }

  /**
   * [내부용] 입력값을 S2Day 객체로 정규화한다.
   *
   * @param {Date | string | number | S2Date} [dateInput]
   * @returns {S2Date}
   * @private
   */
  _normalizeInput(dateInput) {
    if (dateInput instanceof S2Day) {
      return dateInput;
    }
    return new S2Day(dateInput);
  }

  /**
   * 지정된 값만큼 시간을 더한다.
   *
   * @param {number} value - 더할 시간 값 (예: 5, 10).
   * @param {'year' | 'month' | 'day' | 'hour' | 'minute' | 'second'} unit - 시간 단위 (복수형 허용)
   * @returns {S2Date} - 새로운 S2Date 객체 (체이닝)
   *
   * @example
   * S2Date().add(5, 'days'); // 오늘 날짜로부터 5일 후의 날짜 객체 반환
   * S2Date('2025-10-30').add(1, 'month'); // 2025년 10월 30일에서 1개월 더한 날짜 객체 반환
   */
  add(value, unit) {
    const newDate = new Date(this._date); // 불변성 유지를 위해 복사

    switch (typeof unit === 'string' ? unit.toLowerCase() : unit) {
      case 'year':
      case 'years':
        newDate.setFullYear(newDate.getFullYear() + value);
        break;
      case 'month':
      case 'months':
        newDate.setMonth(newDate.getMonth() + value);
        break;
      case 'day':
      case 'days':
        newDate.setDate(newDate.getDate() + value);
        break;
      case 'hour':
      case 'hours':
        newDate.setHours(newDate.getHours() + value);
        break;
      case 'minute':
      case 'minutes':
        newDate.setMinutes(newDate.getMinutes() + value);
        break;
      case 'second':
      case 'seconds':
        newDate.setSeconds(newDate.getSeconds() + value);
        break;
      default:
        // 지원하지 않는 단위에 대한 예외 처리
        throw new Error(`[S2Date] Unknown unit: ${unit}`);
    }

    const result = new S2Day(newDate);

    // 유효성 전파: 부모의 유효성(isValid)이 false였다면(예: 오버플로우), 자식 객체도 (설령 날짜 계산이 유효하더라도) false 상태를 유지한다.
    if (!this._isValid) {
      result._setValid(false);
    }

    return result;
  }

  /**
   * 지정된 값만큼 시간을 뺀다.
   * @param {number} value - 뺄 시간 값 (예: 5, 10)
   * @param {'year' | 'month' | 'day' | 'hour' | 'minute' | 'second'} unit - 시간 단위 (복수형 허용)
   * @returns {S2Date} - 새로운 S2Date 객체 (체이닝)
   *
   * @example
   * S2Date().subtract(5, 'days'); // 오늘 날짜로부터 5일 전의 날짜 객체 반환
   * S2Date('2025-10-30').subtract(1, 'month'); // 2025년 10월 30일에서 1개월 이전 날짜 객체 반환
   */
  subtract(value, unit) {
    return this.add(-value, unit); // add 메서드 재활용
  }

  /**
   * 날짜를 포맷팅한다.
   * (문자열로 반환되기 때문에 체이닝의 마지막 단계에서 사용된다.)
   *
   * @param {string} [formatString = 'YYYY/MM/DD HH:mm:ss'] - 포맷 문자열.
   * @param {string} [locale = 'ko'] - 로케일: 'ko' (기본, 한국어) | 'en' (영어)
   * @param {boolean} [returnEmptyOnInvalid=false] - true일 경우, 유효하지 않은 날짜(isValid() === false)면 빈 문자열('')을 반환. false(기본)일 경우 기본 동작을 따름.
   * @param {{ tz?: 'local' | 'utc' | 'offset', offsetMinutes?: number }} [options] - 표시 기준 타임존 옵션 (diff/startOf/endOf와 동일한 규칙)
   * - tz: 'local' (기본, 브라우저 로컬 타임존) | 'utc' (UTC 기준) | 'offset' (offsetMinutes로 지정한 고정 오프셋 기준).
   * - offsetMinutes: tz가 'offset'일 때 사용되는 분 단위 오프셋(예: +09:00 → 540).
   * - X/x(유닉스 타임스탬프) 토큰은 tz 옵션과 무관하게 항상 실제 절대 시각 기준이다.
   * @returns {string} - 포맷된 날짜 문자열
   *
   * @example
   * S2Date().format('YYYY년 MM월 DD일'); // '2025년 10월 30일' (오늘 날짜 기준)
   * S2Date('2025-10-30T15:30:00+09:00').format('YYYY-MM-DD HH:mm:ss', 'ko'); // '2025-10-30 15:30:00'
   * S2Date('2025-02-30', 'YYYY-MM-DD').format('YYYY-MM-DD HH:mm:ss', 'ko', true); // ''
   * S2Date('2025-02-30', 'YYYY-MM-DD').format('YYYY-MM-DD HH:mm:ss', 'ko', false); // '2025-03-02 00:00:00'
   * S2Date('2025-10-30T23:30:00Z').format('YYYY-MM-DD HH:mm:ss ZZ', 'ko', false, { tz: 'utc' }); // '2025-10-30 23:30:00 +0000'
   *
   * @description
   * ### 지원하는 포맷 토큰
   * YYYY: 4자리 연도(2025)
   * YY: 2자리 연도(25)
   * MMMM: 월 전체 이름 (10월 / October)
   * MMM: 월 축약 이름 (10월 / Oct)
   * MM: 2자리 월 (01~12)
   * M: 1자리 월 (1~12)
   * DD: 2자리 일 (01~31)
   * D: 1자리 일 (1~31)
   * dddd: 요일 전체 이름 (목요일 / Thursday)
   * ddd: 요일 축약 이름 (목 / Thu)
   * dd: 요일 최소 이름 (목 / Th)
   * d: 요일 숫자 (숫자 0~6, 0=일요일)
   * A: 오전/오후 (대문자, AM / PM)
   * a: 오전/오후 (소문자, am / pm)
   * HH: 2자리 시간 (24시, 00~23)
   * H: 1자리 시간 (24시, 0~23)
   * hh: 2자리 시간 (12시, 01~12)
   * h: 1자리 시간 (12시, 1~12)
   * mm: 2자리 분 (00~59)
   * m: 1자리 분 (0~59)
   * ss: 2자리 초 (00-59)
   * s: 1자리 초 (0-59)
   * SSS: 3자리 밀리초 (000-999)
   * Z: UTC 오프셋 (콜론 포함, +09:00)
   * ZZ: UTC 오프셋 (붙여쓰기, +0900)
   * X: 유닉스 타임스탬프 (초, 1761858311)
   * x: 유닉스 타임스탬프 (밀리초, 1761858311123)
   */
  format(
    formatString = 'YYYY/MM/DD HH:mm:ss',
    locale = 'ko',
    returnEmptyOnInvalid = false,
    options = undefined
  ) {
    if (returnEmptyOnInvalid && !this.isValid()) {
      return '';
    }

    const mode = options && typeof options.tz === 'string' ? options.tz.toLowerCase() : 'local';
    if (mode === 'local') {
      return _formatDate(this._date, formatString, locale);
    }

    // utc/offset 모드: 표시 필드만 해당 오프셋 기준으로 옮기고(startOf/endOf/diff와 동일한 방식),
    // X/x(유닉스 타임스탬프)는 _formatDate 내부에서 원래의 절대 시각으로 되돌려 계산한다.
    const off =
      mode === 'offset' && typeof options.offsetMinutes === 'number' ? options.offsetMinutes : 0;
    const shifted = new Date(this._date.getTime() + off * 60000);
    return _formatDate(shifted, formatString, locale, off);
  }

  /**
   * 연도를 반환한다.
   *
   * @returns {number} (예: 2025)
   */
  year() {
    return this._date.getFullYear();
  }

  /**
   * 월을 반환한다. (0-indexed)
   *
   * @returns {number} (0=1월, 1=2월, ... 11=12월)
   */
  month() {
    return this._date.getMonth();
  }

  /**
   * 날짜(일)를 반환한다.
   *
   * @returns {number} (1~31)
   */
  date() {
    return this._date.getDate();
  }

  /**
   * 요일을 반환한다. (0-indexed)
   *
   * @returns {number} (0=일요일, 1=월요일, ... 6=토요일)
   */
  day() {
    return this._date.getDay();
  }

  /**
   * 시간(24시)을 반환한다.
   *
   * @returns {number} (0~23)
   */
  hour() {
    return this._date.getHours();
  }

  /**
   * 분을 반환한다.
   *
   * @returns {number} (0~59)
   */
  minute() {
    return this._date.getMinutes();
  }

  /**
   * 초를 반환한다.
   *
   * @returns {number} (0~59)
   */
  second() {
    return this._date.getSeconds();
  }

  /**
   * 현재 S2Date 객체가 다른 날짜보다 이전인지 확인한다.
   *
   * @param {Date | string | number | S2Date} otherDate - 비교할 날짜
   * @param {'year' | 'month' | 'day' | 'hour' | 'minute' | 'second'} [unit] - 비교할 단위 (복수형 허용, 해당 단위까지 동일하면 false)
   * @returns {boolean}
   *
   * @example
   * S2Date('2025-01-01').isBefore('2025-01-02') // true
   * S2Date('2025-01-01 12:00').isBefore('2025-01-01 13:00', 'day') // false
   */
  isBefore(otherDate, unit) {
    const other = this._normalizeInput(otherDate);
    if (!this.isValid() || !other.isValid()) return false;

    if (unit) {
      const thisStart = this.startOf(unit);
      const otherStart = other.startOf(unit);
      return thisStart.toDate().getTime() < otherStart.toDate().getTime();
    }

    return this.toDate().getTime() < other.toDate().getTime();
  }

  /**
   * 현재 S2Date 객체가 다른 날짜보다 이후인지 확인한다.
   *
   * @param {Date | string | number | S2Date} otherDate - 비교할 날짜
   * @param {'year' | 'month' | 'day' | 'hour' | 'minute' | 'second'} [unit] - 비교할 단위 (복수형 허용, 해당 단위까지 동일하면 false)
   * @returns {boolean}
   *
   * @example
   * S2Date('2025-01-02').isAfter('2025-01-01') // true
   */
  isAfter(otherDate, unit) {
    const other = this._normalizeInput(otherDate);
    if (!this.isValid() || !other.isValid()) return false;

    if (unit) {
      const thisStart = this.startOf(unit);
      const otherStart = other.startOf(unit);
      return thisStart.toDate().getTime() > otherStart.toDate().getTime();
    }

    return this.toDate().getTime() > other.toDate().getTime();
  }

  /**
   * 현재 S2Date 객체가 다른 날짜와 (지정된 단위까지) 동일한지 확인한다.
   *
   * @param {Date | string | number | S2Date} otherDate - 비교할 날짜
   * @param {'year' | 'month' | 'day' | 'hour' | 'minute' | 'second'} [unit] - 비교할 단위 (복수형 허용, 단위를 생략하면 ms까지 비교)
   * @returns {boolean}
   *
   * @example
   * S2Date('2025-01-01 12:00').isSame('2025-01-01 13:00', 'day') // true
   * S2Date('2025-01-01 12:00').isSame('2025-01-01 13:00') // false
   */
  isSame(otherDate, unit) {
    const other = this._normalizeInput(otherDate);
    if (!this.isValid() || !other.isValid()) return false;

    if (unit) {
      const thisStart = this.startOf(unit);
      const otherStart = other.startOf(unit);
      return thisStart.toDate().getTime() === otherStart.toDate().getTime();
    }

    return this.toDate().getTime() === other.toDate().getTime();
  }

  /**
   * 다른 날짜와의 차이를 지정된 단위로 반환한다. (내림 처리)
   *
   * @param {Date | string | number | S2Date} otherDate - 비교할 날짜
   * @param {'year' | 'month' | 'day' | 'hour' | 'minute' | 'second' | 'ms'} [unit = 'ms'] - 반환할 단위 (복수형/'millisecond' 등 허용, 단위를 생략하면 ms까지 비교)
   * @param {boolean} [float = false] - 소수점까지 반환할지 여부 (true)
   * @param {{ tz?: 'local' | 'utc' | 'offset', offsetMinutes?: number }} [options] - 월/연 비교 시 타임존 기준 옵션
   * - tz: 'local' (기본) | 'utc' | 'offset'.
   * - offsetMinutes: tz가 'offset'일 때 사용되는 분 단위 오프셋(예: +09:00 → 540).
   * - 주의: 이 옵션은 'month'와 'year' 단위 계산에만 영향을 준다. ms/초/분/시간/일은 절대 시간(getTime) 기준으로 동일.
   * @throws {Error} 인식할 수 없는 unit이 주어지면 예외를 던진다.
   *
   * 차이점:
   * - tz = 'local' (기본): 시스템 로컬 타임존의 연/월 필드를 사용해 경계를 판단한다.
   * - tz = 'utc': UTC 기준의 연/월 필드를 사용한다.
   * - tz = 'offset': 주어진 고정 오프셋을 적용한 "그 지역의 달력" 기준으로 연/월 경계를 판단한다.
   *
   * @example
   * S2Date('2025-10-31').diff('2025-10-30', 'day') // 1
   * S2Date('2025-10-30 10:00').diff('2025-11-30 11:00', 'month', true) // -0.9997
   * S2Date('2025-11-30 10:00').diff('2025-10-30 11:00', 'month') // 0
   * S2Date('2026-01-01').diff('2025-01-01', 'month') // 12
   * S2Date('2025-03-01T00:00:00Z').diff('2025-02-28T23:00:00Z', 'month', false, { tz: 'utc' }) // 0
   * S2Date('2025-03-01T00:00:00+09:00', 'YYYY-MM-DDTHH:mm:ssZ').diff('2025-02-28T23:30:00+09:00', 'month', false, { tz: 'offset', offsetMinutes: 540 }) // 0
   */
  diff(otherDate, unit = 'ms', float = false, options = undefined) {
    const other = this._normalizeInput(otherDate);
    if (!this.isValid() || !other.isValid()) return NaN;

    const thisMs = this.toDate().getTime();
    const otherMs = other.toDate().getTime();
    const diffMs = thisMs - otherMs;

    let result;

    // 옵션 정규화 (월/연 전용)
    let tzMode = 'local';
    let fixedOffset = null; // 분 단위. null일 땐 local
    if (options && typeof options === 'object') {
      const tzOpt = typeof options.tz === 'string' ? options.tz.toLowerCase() : undefined;
      if (tzOpt === 'utc') {
        tzMode = 'utc';
        fixedOffset = 0;
      } else if (tzOpt === 'offset') {
        tzMode = 'offset';
        fixedOffset = typeof options.offsetMinutes === 'number' ? options.offsetMinutes : 0;
      }
    }

    const msPerDay = 86400000;

    const getYearMonthByOffset = (ms, offsetMinutes) => {
      const shifted = new Date(ms + offsetMinutes * 60000);
      return { y: shifted.getUTCFullYear(), m: shifted.getUTCMonth(), base: shifted };
    };
    const daysInMonthByYMOffset = (y, m) => {
      return new Date(Date.UTC(y, m + 1, 0)).getUTCDate();
    };
    const buildMsFromYMDHMSOffset = (y, m, d, hh, mm, ss, ms, offsetMinutes) => {
      return Date.UTC(y, m, d, hh, mm, ss, ms) - offsetMinutes * 60000;
    };

    switch (typeof unit === 'string' ? unit.toLowerCase() : unit) {
      case 'month':
      case 'months': {
        if (tzMode === 'local') {
          // 로컬 타임존 기준 기존 로직 유지
          result = (this.year() - other.year()) * 12 + (this.month() - other.month());
          const thatDate = other.add(result, 'month');

          if (diffMs > 0) {
            if (this.isBefore(thatDate)) result -= 1;
          } else if (diffMs < 0) {
            if (this.isAfter(thatDate)) result += 1;
          }

          if (float) {
            const diffRemainMs = thisMs - thatDate.toDate().getTime();
            const denomDays = thatDate.daysInMonth();
            result += diffRemainMs / (denomDays * msPerDay);
          }
        } else {
          // UTC 또는 고정 오프셋 기준 달력으로 연/월 계산 (local의 setMonth 오버플로우 동작과 동일하게 처리)
          const off = fixedOffset ?? 0;
          const a = getYearMonthByOffset(thisMs, off);
          const b = getYearMonthByOffset(otherMs, off);
          result = (a.y - b.y) * 12 + (a.m - b.m);

          // other(+off)를 기준으로 months를 더할 때, clamp 없이 Date.UTC의 오버플로우 규칙을 그대로 사용한다
          const bShift = new Date(otherMs + off * 60000);
          const y0 = bShift.getUTCFullYear();
          const m0 = bShift.getUTCMonth();
          const d0 = bShift.getUTCDate();
          const hh0 = bShift.getUTCHours();
          const mm0 = bShift.getUTCMinutes();
          const ss0 = bShift.getUTCSeconds();
          const ms0 = bShift.getUTCMilliseconds();

          const anchorMs = buildMsFromYMDHMSOffset(y0, m0 + result, d0, hh0, mm0, ss0, ms0, off);

          if (diffMs > 0) {
            if (thisMs < anchorMs) result -= 1;
          } else if (diffMs < 0) {
            if (thisMs > anchorMs) result += 1;
          }

          if (float) {
            // 앵커 시점을 기준으로 해당 월 길이를 계산하여 분모로 사용
            const anchorDateShifted = new Date(anchorMs + off * 60000);
            const denomY = anchorDateShifted.getUTCFullYear();
            const denomM = anchorDateShifted.getUTCMonth();
            const denomDays = daysInMonthByYMOffset(denomY, denomM);
            const diffRemainMs = thisMs - anchorMs;
            result += diffRemainMs / (denomDays * msPerDay);
          }
        }
        break;
      }
      case 'year':
      case 'years': {
        if (tzMode === 'local') {
          let monthDiff = (this.year() - other.year()) * 12 + (this.month() - other.month());
          const thatDate = other.add(monthDiff, 'month');
          if (diffMs > 0) {
            if (this.isBefore(thatDate)) monthDiff -= 1;
          } else if (diffMs < 0) {
            if (this.isAfter(thatDate)) monthDiff += 1;
          }
          result = monthDiff / 12;
        } else {
          const off = fixedOffset ?? 0;
          const a = getYearMonthByOffset(thisMs, off);
          const b = getYearMonthByOffset(otherMs, off);
          let monthDiff = (a.y - b.y) * 12 + (a.m - b.m);

          // monthDiff 만큼의 months를 오버플로우 규칙으로 더한 앵커 계산
          const bShift = new Date(otherMs + off * 60000);
          const y0 = bShift.getUTCFullYear();
          const m0 = bShift.getUTCMonth();
          const d0 = bShift.getUTCDate();
          const hh0 = bShift.getUTCHours();
          const mm0 = bShift.getUTCMinutes();
          const ss0 = bShift.getUTCSeconds();
          const ms0 = bShift.getUTCMilliseconds();
          const anchorMs = buildMsFromYMDHMSOffset(y0, m0 + monthDiff, d0, hh0, mm0, ss0, ms0, off);

          if (diffMs > 0) {
            if (thisMs < anchorMs) monthDiff -= 1;
          } else if (diffMs < 0) {
            if (thisMs > anchorMs) monthDiff += 1;
          }

          result = monthDiff / 12;
        }
        break;
      }
      case 'day':
      case 'days':
        result = diffMs / 86400000; // (1000 * 60 * 60 * 24)
        break;
      case 'hour':
      case 'hours':
        result = diffMs / 3600000; // (1000 * 60 * 60)
        break;
      case 'minute':
      case 'minutes':
        result = diffMs / 60000; // (1000 * 60)
        break;
      case 'second':
      case 'seconds':
        result = diffMs / 1000;
        break;
      case 'ms':
      case 'millisecond':
      case 'milliseconds':
        result = diffMs;
        break;
      default:
        throw new Error(`[S2Date] Unknown unit: ${unit}`);
    }

    return float ? result : Math.trunc(result);
  }

  /**
   * 지정된 단위의 시작 시간으로 설정된 새 S2Date 객체를 반환한다.
   *
   * @param {'year' | 'month' | 'day' | 'hour' | 'minute' | 'second'} unit - 단위 (복수형 허용)
   * @param {{ tz?: 'local' | 'utc' | 'offset', offsetMinutes?: number }} [options]
   * - tz: 'local'(기본) | 'utc' | 'offset'.
   * - offsetMinutes: tz = 'offset'일 때 사용되는 분 단위 오프셋(예: +09:00 → 540).
   * - 동작: 지정된 달력 기준으로 경계를 00시 00분 00초.000 으로 내림(floor)한다.
   * @returns {S2Date}
   * @throws {Error} 인식할 수 없는 unit이 주어지면 예외를 던진다.
   *
   * @example
   * S2Date('2025-10-30 15:30').startOf('day') // local 기준
   * S2Date('2025-10-30T23:30:00Z').startOf('day', { tz: 'utc' })
   * S2Date('2025-10-30T23:30:00+09:00', 'YYYY-MM-DDTHH:mm:ssZ').startOf('day', { tz: 'offset', offsetMinutes: 540 })
   */
  startOf(unit, options = undefined) {
    const mode = options && typeof options.tz === 'string' ? options.tz.toLowerCase() : 'local';
    const off =
      mode === 'utc'
        ? 0
        : mode === 'offset'
          ? typeof options.offsetMinutes === 'number'
            ? options.offsetMinutes
            : 0
          : null;

    if (off === null) {
      const newDate = new Date(this._date);
      switch (typeof unit === 'string' ? unit.toLowerCase() : unit) {
        case 'year':
        case 'years':
          newDate.setMonth(0, 1);
          newDate.setHours(0, 0, 0, 0);
          break;
        case 'month':
        case 'months':
          newDate.setDate(1);
          newDate.setHours(0, 0, 0, 0);
          break;
        case 'day':
        case 'days':
          newDate.setHours(0, 0, 0, 0);
          break;
        case 'hour':
        case 'hours':
          newDate.setMinutes(0, 0, 0);
          break;
        case 'minute':
        case 'minutes':
          newDate.setSeconds(0, 0);
          break;
        case 'second':
        case 'seconds':
          newDate.setMilliseconds(0);
          break;
        default:
          throw new Error(`[S2Date] Unknown unit: ${unit}`);
      }
      const result = new S2Day(newDate);
      if (!this._isValid) result._setValid(false);
      return result;
    }

    // UTC/고정 오프셋 기준으로 경계 내림
    const shifted = new Date(this._date.getTime() + off * 60000);
    switch (typeof unit === 'string' ? unit.toLowerCase() : unit) {
      case 'year':
      case 'years':
        shifted.setUTCMonth(0, 1);
        shifted.setUTCHours(0, 0, 0, 0);
        break;
      case 'month':
      case 'months':
        shifted.setUTCDate(1);
        shifted.setUTCHours(0, 0, 0, 0);
        break;
      case 'day':
      case 'days':
        shifted.setUTCHours(0, 0, 0, 0);
        break;
      case 'hour':
      case 'hours':
        shifted.setUTCMinutes(0, 0, 0);
        break;
      case 'minute':
      case 'minutes':
        shifted.setUTCSeconds(0, 0);
        break;
      case 'second':
      case 'seconds':
        shifted.setUTCMilliseconds(0);
        break;
      default:
        throw new Error(`[S2Date] Unknown unit: ${unit}`);
    }
    const resultUtcMs = shifted.getTime() - off * 60000;
    const result = new S2Day(new Date(resultUtcMs));
    if (!this._isValid) result._setValid(false);
    return result;
  }

  /**
   * 지정된 단위의 끝 시간으로 설정된 새 S2Date 객체를 반환한다.
   *
   * @param {'year' | 'month' | 'day' | 'hour' | 'minute' | 'second'} unit - 단위 (복수형 허용)
   * @param {{ tz?: 'local' | 'utc' | 'offset', offsetMinutes?: number }} [options]
   * - tz: 'local'(기본) | 'utc' | 'offset'.
   * - offsetMinutes: tz = 'offset'일 때 사용되는 분 단위 오프셋(예: +09:00 → 540).
   * - 동작: 지정된 달력 기준으로 경계를 23시 59분 59초.999 으로 올림(ceil-ε)한다.
   * @returns {S2Date}
   * @throws {Error} 인식할 수 없는 unit이 주어지면 예외를 던진다.
   *
   * @example
   * S2Date('2025-10-30 15:30').endOf('day') // local 기준
   * S2Date('2025-10-30T23:30:00Z').endOf('month', { tz: 'utc' })
   * S2Date('2025-10-30T23:30:00+09:00', 'YYYY-MM-DDTHH:mm:ssZ').endOf('day', { tz: 'offset', offsetMinutes: 540 })
   */
  endOf(unit, options = undefined) {
    const mode = options && typeof options.tz === 'string' ? options.tz.toLowerCase() : 'local';
    const off =
      mode === 'utc'
        ? 0
        : mode === 'offset'
          ? typeof options.offsetMinutes === 'number'
            ? options.offsetMinutes
            : 0
          : null;

    if (off === null) {
      const newDate = new Date(this._date);
      switch (typeof unit === 'string' ? unit.toLowerCase() : unit) {
        case 'year':
        case 'years':
          newDate.setMonth(11, 31);
          newDate.setHours(23, 59, 59, 999);
          break;
        case 'month':
        case 'months':
          newDate.setMonth(newDate.getMonth() + 1, 0);
          newDate.setHours(23, 59, 59, 999);
          break;
        case 'day':
        case 'days':
          newDate.setHours(23, 59, 59, 999);
          break;
        case 'hour':
        case 'hours':
          newDate.setMinutes(59, 59, 999);
          break;
        case 'minute':
        case 'minutes':
          newDate.setSeconds(59, 999);
          break;
        case 'second':
        case 'seconds':
          newDate.setMilliseconds(999);
          break;
        default:
          throw new Error(`[S2Date] Unknown unit: ${unit}`);
      }
      const result = new S2Day(new Date(newDate));
      if (!this._isValid) result._setValid(false);
      return result;
    }

    // UTC/고정 오프셋 기준으로 경계 올림(끝 시각)
    const shifted = new Date(this._date.getTime() + off * 60000);
    switch (typeof unit === 'string' ? unit.toLowerCase() : unit) {
      case 'year':
      case 'years':
        shifted.setUTCMonth(11, 31);
        shifted.setUTCHours(23, 59, 59, 999);
        break;
      case 'month':
      case 'months':
        shifted.setUTCMonth(shifted.getUTCMonth() + 1, 0);
        shifted.setUTCHours(23, 59, 59, 999);
        break;
      case 'day':
      case 'days':
        shifted.setUTCHours(23, 59, 59, 999);
        break;
      case 'hour':
      case 'hours':
        shifted.setUTCMinutes(59, 59, 999);
        break;
      case 'minute':
      case 'minutes':
        shifted.setUTCSeconds(59, 999);
        break;
      case 'second':
      case 'seconds':
        shifted.setUTCMilliseconds(999);
        break;
      default:
        throw new Error(`[S2Date] Unknown unit: ${unit}`);
    }
    const resultUtcMs = shifted.getTime() - off * 60000;
    const result = new S2Day(new Date(resultUtcMs));
    if (!this._isValid) result._setValid(false);
    return result;
  }

  /**
   * 윤년인지 확인한다.
   *
   * @returns {boolean}
   */
  isLeapYear() {
    const year = this.year();
    return (year % 4 === 0 && year % 100 !== 0) || year % 400 === 0;
  }

  /**
   * 해당 월의 총 일수(28, 29, 30, 31)를 반환한다.
   * @returns {number}
   */
  daysInMonth() {
    // month()가 0-indexed이므로, +1을 하여 다음 달의 0번째 날(이달의 마지막 날)을 가져옴
    return new Date(this.year(), this.month() + 1, 0).getDate();
  }

  /**
   * S2Date 객체가 생성 시점부터 유효한 날짜를 참조하는지 확인한다.
   * (예: '2025-02-30' 처럼 존재하지 않는 날짜는 false)
   *
   * @returns {boolean} - 유효하면 true, 아니면 false.
   *
   * @example
   * S2Date('2025-02-28', 'YYYY-MM-DD').isValid() // true
   * S2Date('2025-02-30', 'YYYY-MM-DD').isValid() // false
   * S2Date('abc').isValid() // false
   */
  isValid() {
    return this._isValid;
  }

  /**
   * [내부용] 체이닝 시 부모의 유효성(false)을 전파하기 위해 S2Date 객체의 유효성 플래그를 강제로 설정한다.
   *
   * @param {boolean} value - 설정할 유효성 값
   * @private
   */
  _setValid(value) {
    this._isValid = value;
  }

  /**
   * 내부의 원본 Date 객체를 반환한다.
   * @returns {Date}
   */
  toDate() {
    return this._date;
  }

  /**
   * S2Date 객체를 문자열로 변환한다.
   * @returns {string}
   */
  toString() {
    return this.isValid() ? this.format() : 'Invalid Date';
  }

  /**
   * 현재 시각의 S2Date 객체를 반환한다.
   * @returns {S2Date}
   *
   * @example
   * S2Date.now().add(1, 'day').format('YYYY-MM-DD');
   */
  static now() {
    return new S2Day();
  }
}

// ===============================================
// 헬퍼 함수 (Helper Functions)
// ===============================================

/**
 * 날짜를 지정된 포맷 문자열로 변환 (헬퍼 함수)
 *
 * @param {Date} [date = new Date()] - 포맷할 Date 객체. fixedOffsetMinutes가 주어졌다면, 호출자가 이미 해당 오프셋만큼 미리 옮겨놓은(shift) 시각이어야 한다.
 * @param {string} [formatString = 'YYYY/MM/DD HH:mm:ss'] - Day.js 스타일 포맷 문자열
 * @param {string} [locale = 'ko'] - 로케일: 'ko' (기본, 한국어) | 'en' (영어)
 * @param {number|null} [fixedOffsetMinutes = null] - null이면 로컬 타임존 getter를 사용. 숫자가 주어지면(0=UTC 포함)
 * UTC getter로 필드를 읽고(date가 이미 이 오프셋만큼 shift되어 있으므로), Z/ZZ는 이 오프셋으로, X/x는 원래의 절대 시각으로 계산한다.
 * @returns {string} 포맷된 날짜 문자열
 */
function _formatDate(
  date = new Date(),
  formatString = 'YYYY/MM/DD HH:mm:ss',
  locale = 'ko',
  fixedOffsetMinutes = null
) {
  if (!(date instanceof Date) || isNaN(date)) return '';

  const pad = (n, len = 2) => '0'.repeat(Math.max(0, len - String(n).length)) + n;
  const pad3 = (n) => '0'.repeat(Math.max(0, 3 - String(n).length)) + n;

  const locales = {
    en: {
      weekdays: ['Sunday', 'Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday'],
      weekdaysShort: ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'],
      weekdaysMin: ['Su', 'Mo', 'Tu', 'We', 'Th', 'Fr', 'Sa'],
      months: [
        'January',
        'February',
        'March',
        'April',
        'May',
        'June',
        'July',
        'August',
        'September',
        'October',
        'November',
        'December'
      ],
      monthsShort: [
        'Jan',
        'Feb',
        'Mar',
        'Apr',
        'May',
        'Jun',
        'Jul',
        'Aug',
        'Sep',
        'Oct',
        'Nov',
        'Dec'
      ]
    },
    ko: {
      weekdays: ['일요일', '월요일', '화요일', '수요일', '목요일', '금요일', '토요일'],
      weekdaysShort: ['일', '월', '화', '수', '목', '금', '토'],
      weekdaysMin: ['일', '월', '화', '수', '목', '금', '토'],
      months: [
        '1월',
        '2월',
        '3월',
        '4월',
        '5월',
        '6월',
        '7월',
        '8월',
        '9월',
        '10월',
        '11월',
        '12월'
      ],
      monthsShort: [
        '1월',
        '2월',
        '3월',
        '4월',
        '5월',
        '6월',
        '7월',
        '8월',
        '9월',
        '10월',
        '11월',
        '12월'
      ]
    }
  };

  const loc = locales[locale] || locales.ko;
  const { weekdays, weekdaysShort, weekdaysMin, months, monthsShort } = loc;

  // fixedOffsetMinutes가 주어지면(utc/offset 모드) UTC getter를, 아니면 로컬 getter를 사용한다.
  const useFixedOffset = fixedOffsetMinutes !== null;
  const getFullYear = () => (useFixedOffset ? date.getUTCFullYear() : date.getFullYear());
  const getMonth = () => (useFixedOffset ? date.getUTCMonth() : date.getMonth());
  const getDate = () => (useFixedOffset ? date.getUTCDate() : date.getDate());
  const getDay = () => (useFixedOffset ? date.getUTCDay() : date.getDay());
  const getHours = () => (useFixedOffset ? date.getUTCHours() : date.getHours());
  const getMinutes = () => (useFixedOffset ? date.getUTCMinutes() : date.getMinutes());
  const getSeconds = () => (useFixedOffset ? date.getUTCSeconds() : date.getSeconds());
  const getMilliseconds = () =>
    useFixedOffset ? date.getUTCMilliseconds() : date.getMilliseconds();

  const YYYY = getFullYear();
  const YY = String(YYYY).slice(-2);
  const MMMM = months[getMonth()];
  const MMM = monthsShort[getMonth()];
  const MM = pad(getMonth() + 1);
  const M = getMonth() + 1;
  const DD = pad(getDate());
  const D = getDate();
  const dddd = weekdays[getDay()];
  const ddd = weekdaysShort[getDay()];
  const dd = weekdaysMin[getDay()];
  const d = getDay();
  const A = getHours() >= 12 ? 'PM' : 'AM';
  const a = getHours() >= 12 ? 'pm' : 'am';
  const HH = pad(getHours());
  const H = getHours();
  const hh = pad(getHours() % 12 || 12);
  const h = getHours() % 12 || 12;
  const mm = pad(getMinutes());
  const m = getMinutes();
  const ss = pad(getSeconds());
  const s = getSeconds();
  const SSS = pad3(getMilliseconds());
  const tzOffset = useFixedOffset ? fixedOffsetMinutes : -date.getTimezoneOffset();
  const tzSign = tzOffset >= 0 ? '+' : '-';
  const tzHours = pad(Math.floor(Math.abs(tzOffset) / 60));
  const tzMinutes = pad(Math.abs(tzOffset) % 60);
  const Z = `${tzSign}${tzHours}:${tzMinutes}`;
  const ZZ = `${tzSign}${tzHours}${tzMinutes}`;
  // X/x는 표시 타임존과 무관하게 항상 실제 절대 시각(epoch) 기준이어야 하므로,
  // fixedOffsetMinutes 모드에서는 호출자가 미리 shift한 만큼을 되돌려 원래 시각을 복원한다.
  const trueMs = useFixedOffset ? date.getTime() - fixedOffsetMinutes * 60000 : date.getTime();
  const X = Math.floor(trueMs / 1000);
  const x = trueMs;

  const map = {
    YYYY,
    YY,
    MMMM,
    MMM,
    MM,
    M,
    DD,
    D,
    dddd,
    ddd,
    dd,
    d,
    A,
    a,
    HH,
    H,
    hh,
    h,
    mm,
    m,
    ss,
    s,
    SSS,
    Z,
    ZZ,
    X,
    x
  };

  return formatString.replace(
    /YYYY|YY|MMMM|MMM|MM|M|DD|D|dddd|ddd|dd|d|A|a|HH|H|hh|h|mm|m|ss|s|SSS|ZZ|Z|X|x/g,
    (m) => map[m]
  );
}

/** 문자열을 정규식 리터럴로 이스케이프하는 헬퍼 */
function _escapeRegex(s) {
  return s.replace(/[-/\\^$*+?.()|[\]{}]/g, '\\$&');
}

/** 포맷 토큰을 정규식 문자열로 매핑 (숫자 기반 토큰) */
const _parseTokenMap = {
  YYYY: '(\\d{4})',
  YY: '(\\d{2})',
  MM: '(\\d{2})',
  M: '(\\d{1,2})',
  DD: '(\\d{2})',
  D: '(\\d{1,2})',
  A: '(AM|PM)',
  a: '(am|pm)',
  HH: '(\\d{2})',
  H: '(\\d{1,2})',
  hh: '(\\d{2})',
  h: '(\\d{1,2})',
  mm: '(\\d{2})',
  m: '(\\d{1,2})',
  ss: '(\\d{2})',
  s: '(\\d{1,2})',
  SSS: '(\\d{3})',
  // UTC 오프셋 파싱 토큰 (Z/ZZ가 있을 경우 UTC 모드로 파싱을 유도함)
  Z: '([+-]\\d{2}:\\d{2})', // +09:00, -05:00 형태
  ZZ: '([+-]\\d{4})' // +0900, -0500 형태
};

/** _parseTokenMap의 키를 정규식으로 (긴 것부터) */
const _parseTokenRegex = /YYYY|YY|MM|M|DD|D|A|a|HH|H|hh|h|mm|m|ss|s|SSS|ZZ|Z/g;

/** 생성된 파서를 캐싱하기 위한 맵 */
const _parserCache = new Map();

/**
 * 포맷 문자열(예: 'YYYY년MM월')을 기반으로 파서 객체 { regex, groups }를 동적으로 생성한다.
 */
function _buildParser(formatString) {
  // 1. 캐시 확인
  if (_parserCache.has(formatString)) {
    return _parserCache.get(formatString);
  }

  let finalRegexString = '^';
  const groups = [];
  let lastIndex = 0;

  // 2. 포맷 문자열에서 토큰(YYYY 등)을 순회
  _parseTokenRegex.lastIndex = 0; // 정규식 인덱스 초기화
  let match;
  while ((match = _parseTokenRegex.exec(formatString)) !== null) {
    // 3. 토큰 앞의 리터럴 문자(예: '년') 처리
    if (match.index > lastIndex) {
      const literal = formatString.substring(lastIndex, match.index);
      finalRegexString += _escapeRegex(literal); // 정규식으로 이스케이프
    }

    // 4. 토큰(예: 'YYYY')을 정규식 그룹(예: '(\d{4})')으로 변환
    const token = match[0];
    finalRegexString += _parseTokenMap[token];
    groups.push(token); // 캡처 그룹 순서 저장

    lastIndex = _parseTokenRegex.lastIndex;
  }

  // 5. 마지막 토큰 뒤의 리터럴 문자(예: '월') 처리
  if (lastIndex < formatString.length) {
    const literal = formatString.substring(lastIndex);
    finalRegexString += _escapeRegex(literal);
  }

  finalRegexString += '$'; // 문자열 끝 일치

  // 6. 파서 객체 생성 및 캐시 저장
  const parser = {
    regex: new RegExp(finalRegexString),
    groups: groups
  };
  _parserCache.set(formatString, parser);
  return parser;
}
