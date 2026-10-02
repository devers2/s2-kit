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
 * 드롭영역
 * @author devers2
 * @since  2025
 * @version 1.0
 */
export class S2DropZone {
  /**
   * DropZone 생성자
   *
   * @param {Object} options - 드롭존 설정 옵션
   * @param {Element|string} options.dropZone - 드롭존으로 사용할 DOM 요소 또는 선택자
   * @param {Function} [options.dragenter] - 드래그 엔터 이벤트 발생 시 호출될 콜백 함수
   * @param {Function} [options.dragleave] - 드래그 리브 이벤트 발생 시 호출될 콜백 함수
   * @param {Function} [options.dragover] - 드래그 오버 이벤트 발생 시 호출될 콜백 함수
   * @param {Function} [options.drop] - 파일이 드롭됐을 때 호출될 콜백 함수
   *
   * @example
   * import { S2DropZone } from './js/s2.dropzone.js'; // Thymeleaf 는 '[[@{/js/s2.dropzone.js}]]' 형태로 경로 지정
   *
   * const dropZone = new S2DropZone({
   *     dropZone: document.querySelector('.dropZone'),
   *     dragenter: function (event, dropZoneElement) {
   *         // 드래그 엔터 이벤트 발생 시 호출
   *         // event: 드래그 이벤트 객체, dropZoneElement: 드롭존 DOM 요소 (dropZone)
   *     },
   *     dragover: function (event, dropZoneElement) {
   *         // 드래그 오버 이벤트 발생 시 호출
   *         // event: 드래그 이벤트 객체, dropZoneElement: 드롭존 DOM 요소 (dropZone)
   *     },
   *     dragleave: function (event, dropZoneElement) {
   *         // 드래그 리브 이벤트 발생 시 호출
   *         // event: 드래그 이벤트 객체, dropZoneElement: 드롭존 DOM 요소 (dropZone)
   *     },
   *     drop: function (event, dropZoneElement, dropFiles) {
   *         // 드롭 이벤트 발생 시 호출
   *         // event: 드래그 이벤트 객체, dropZoneElement: 드롭존 DOM 요소 (dropZone)
   *         // dropFiles: 드롭된 파일 목록 (File 객체 배열)
   *     }
   * });
   *
   * @throws {Error} options.dropZone이 유효한 DOM 요소로 해석되지 않으면 예외를 던진다.
   */
  constructor(options = {}) {
    let dropZone = options.dropZone;

    // 문자열 선택자인 경우 DOM 요소로 변환
    if (typeof dropZone === 'string') {
      dropZone = document.querySelector(dropZone);
    }

    if (!dropZone) {
      // 여기서 조용히 return하면(예외를 던지지 않으면) 이벤트가 하나도 연결되지 않은
      // "좀비" 인스턴스가 정상 생성된 것처럼 반환되어 호출부가 실패를 알아채기 어렵다.
      throw new Error('[S2DropZone] 유효하지 않은 dropZone 입니다.');
    }

    this.dropZone = dropZone;
    this.options = options;
    this.enableDrop = true;

    // 이벤트 리스너 등록
    this.setupEventListeners();
  }

  /**
   * 드롭존 활성화
   */
  enable() {
    this.enableDrop = true;
  }

  /**
   * 드롭존 비활성화
   */
  disable() {
    this.enableDrop = false;
  }

  /**
   * 등록된 이벤트 리스너를 모두 해제한다. dropZone 요소를 재사용하되 이 인스턴스는 더 이상 쓰지 않을 때 호출한다.
   * (destroy() 호출 이후에는 이 인스턴스를 재사용하지 않는다.)
   *
   * @example
   * dropZone.destroy();
   */
  destroy() {
    if (!this.dropZone) {
      return;
    }
    this.dropZone.removeEventListener('dragenter', this._onDragEnter);
    this.dropZone.removeEventListener('dragleave', this._onDragLeave);
    this.dropZone.removeEventListener('dragover', this._onDragOver);
    this.dropZone.removeEventListener('drop', this._onDrop);
  }

  /**
   * 이벤트 리스너 설정
   * @private
   */
  setupEventListeners() {
    const self = this;

    // 나중에 destroy()에서 removeEventListener로 해제할 수 있도록 핸들러를 인스턴스에 보관해둔다.
    this._onDragEnter = function (e) {
      if (self.enableDrop === false) return;

      e.stopPropagation();
      e.preventDefault();

      this.classList.remove('dragover');

      // 사용자 정의 dragenter 콜백 호출
      if (self.options.dragenter && typeof self.options.dragenter === 'function') {
        self.options.dragenter(e, this);
      }
    };

    this._onDragLeave = function (e) {
      if (self.enableDrop === false) return;

      e.stopPropagation();
      e.preventDefault();

      this.classList.remove('dragover');

      // 사용자 정의 dragleave 콜백 호출
      if (self.options.dragleave && typeof self.options.dragleave === 'function') {
        self.options.dragleave(e, this);
      }
    };

    this._onDragOver = function (e) {
      if (self.enableDrop === false) return;

      e.stopPropagation();
      e.preventDefault();

      this.classList.add('dragover');

      // 사용자 정의 dragover 콜백 호출
      if (self.options.dragover && typeof self.options.dragover === 'function') {
        self.options.dragover(e, this);
      }
    };

    this._onDrop = function (e) {
      if (self.enableDrop === false) return;

      e.stopPropagation();
      e.preventDefault();

      this.classList.remove('dragover');

      const dropFiles = [];

      // 파일 처리
      if (e.dataTransfer.items) {
        // DataTransferItemList 인터페이스 사용
        Array.from(e.dataTransfer.items).forEach((item) => {
          if (item.kind === 'file') {
            const file = item.getAsFile();
            if (file) {
              dropFiles.push(file);
            }
          }
        });
      } else {
        // 구형 브라우저 지원
        Array.from(e.dataTransfer.files).forEach((file) => {
          dropFiles.push(file);
        });
      }

      // 사용자 정의 drop 콜백 호출
      if (self.options.drop && typeof self.options.drop === 'function') {
        self.options.drop(e, this, dropFiles);
      }
    };

    this.dropZone.addEventListener('dragenter', this._onDragEnter);
    this.dropZone.addEventListener('dragleave', this._onDragLeave);
    this.dropZone.addEventListener('dragover', this._onDragOver);
    this.dropZone.addEventListener('drop', this._onDrop);
  }
}
