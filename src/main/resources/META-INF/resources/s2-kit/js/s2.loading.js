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
import { S2Util } from './s2.util.js';

let g_interval;
// 현재 로딩 오버레이를 요청 중인 토큰(S2Util.uuid()) 집합. 여러 요청(예: 동시 fetch)이 겹쳐도
// 모든 토큰이 제거되어야 오버레이가 사라진다. 단순 카운트가 아니라 토큰 단위로 관리하므로,
// 서로 관련 없는 호출이 우연히 hide()를 한 번 더/덜 호출하더라도 다른 요청의 상태를 침범하지 않는다.
const g_activeLoadingTokens = new Set();

/**
 * 로딩 오버레이 DOM과 프로그레스 바 인터벌을 정리한다.
 * @private
 */
const removeLoadingOverlay = () => {
  if (g_interval) {
    clearInterval(g_interval);
    g_interval = undefined;
  }
  const loadingOverlay = document.querySelector('.s2-loading-overlay');
  if (loadingOverlay) {
    loadingOverlay.remove();
  }
};

/**
 * 프로그레스 바의 애니메이션 진행을 제어합니다.
 * @private
 * @param {number} timeout - 인터벌 시간 (밀리초)
 */
const controlProgressBar = (timeout) => {
  let progress = 0;
  g_interval = setInterval(
    function () {
      if (progress >= 98) {
        clearInterval(g_interval);
        return;
      }
      setProgressBar(++progress);
    },
    timeout && !isNaN(timeout) ? timeout : 1200
  );
};

/**
 * 프로그레스 바의 너비와 텍스트를 업데이트합니다.
 * @private
 * @param {number} percent - 진행률 (0-100)
 */
const setProgressBar = (percent) => {
  const progressBar = document.querySelector('.progress-bar');
  const progressPercent = document.querySelector('.progress-text');
  if (progressBar) progressBar.style.width = percent + '%';
  if (progressPercent) progressPercent.textContent = percent + '%';
};

/**
 * 로딩 페이지 오버레이를 화면에 표시합니다.
 * 옵션에 따라 프로그레스 바를 표시하고 애니메이션을 시작합니다.
 *
 * @param {Object} [options] - 로딩 설정 옵션
 * @param {boolean} [options.showOverlay=false] - 오버레이 표시 여부
 * @param {boolean} [options.showProgress=false] - 프로그레스 바 표시 여부
 * @param {number} [options.timeout=1200] - 프로그레스 바 진행 속도 (인터벌 시간, 기본 1200ms)
 * @returns {string} 이 show 요청을 식별하는 고유 토큰(S2Util.uuid()). hideS2Loading(token) 호출 시 그대로 전달해야 한다.
 *
 * showS2Loading/hideS2Loading은 토큰 단위로 관리되므로, 동시에 여러 번 show가 호출되었다면 각 호출에서
 * 반환된 토큰으로 hideS2Loading(token)을 호출해야(1:1로 짝이 맞아야) 실제로 오버레이가 사라진다.
 * (예: 두 개의 fetch 요청이 동시에 진행 중일 때, 하나가 먼저 끝나도 나머지가 끝날 때까지 오버레이가 유지된다.)
 * 토큰 없이 hideS2Loading()을 호출하면, 진행 중인 다른 요청 여부와 무관하게 즉시 강제로 오버레이를 제거한다.
 *
 * @example
 * import { showS2Loading, hideS2Loading } from './js/s2.loading.js'; // Thymeleaf 는 '[[@{/js/s2.loading.js}]]' 형태로 경로 지정
 *
 * ex1) 프로그레스 바 없이 로딩 표시 후 숨기기
 * const token = showS2Loading();
 * hideS2Loading(token);
 * ex2) 프로그레스 바를 표시하며 로딩 시작
 * showS2Loading({ showProgress: true, timeout: 800 });
 */
export const showS2Loading = (options) => {
  const token = S2Util.uuid();
  g_activeLoadingTokens.add(token);

  try {
    // 기존에 존재하는 로딩 오버레이를 정리하고 새 옵션으로 다시 그린다.
    removeLoadingOverlay();

    const loadingHtml = `
        <div class="s2-loading-overlay">
            <div class="loader-container">
                <div class="hexagon-wrap">
                    <div>
                        <div class="hexagon hex1 blue">
                            <div class="inner-line"></div>
                        </div>
                    </div>
                    <div>
                        <div class="hexagon hex2 gray">
                            <div class="inner-line"></div>
                            <div class="hexagon-line"></div>
                        </div>
                        <div class="hexagon hex3 gray">
                            <div class="inner-line"></div>
                            <div class="hexagon-line"></div>
                        </div>
                    </div>
                    <div>
                        <div class="hexagon hex4 blue">
                            <div class="inner-line"></div>
                            <div class="hexagon-line blue line1"></div>
                            <div class="hexagon-line blue line2"></div>
                            <div class="hexagon-line blue line3"></div>
                        </div>
                    </div>
                </div>
                <div class="progress-wrap ${options && options.showProgress ? '' : 'd-none'}">
                    <span class="progress-text"></span>
                    <div class="progress-bar"></div>
                </div>
            </div>
        </div>
    `;

    S2Util.replaceChildren(document.body, loadingHtml, {
      isAppend: true,
      onNodeReady: (node) => {
        if (options && (options.showOverlay || options.showProgress)) {
          node.classList.add('background');
        }
      }
    });

    if (options && options.showProgress) {
      setProgressBar(0);
      controlProgressBar(options ? options.timeout : 0);
    }
  } catch (error) {
    // 렌더링 도중 오류가 발생해도 토큰은 반드시 정리한다. 그렇지 않으면 이후 hideS2Loading()이
    // 아무리 호출되어도 이 토큰이 살아남아 오버레이가 영원히 표시된 것으로(또는 숨겨지지 않는 것으로) 남는다.
    g_activeLoadingTokens.delete(token);

    // 오버레이가 DOM에 이미 붙은 뒤(예: setProgressBar/controlProgressBar 단계)에 예외가 발생하는
    // 경우까지 포함해서, 실패 지점과 무관하게 화면에 고아 상태로 남지 않도록 정리한다.
    // 단, 오버레이는 토큰별이 아니라 화면에 하나만 존재하는 공유 요소이므로, 아직 완료되지 않은
    // 다른 요청(토큰)이 남아있다면 그 요청의 오버레이까지 함께 지우지 않도록 여기서는 건드리지 않는다.
    if (g_activeLoadingTokens.size === 0) {
      removeLoadingOverlay();
    }
    throw error;
  }

  return token;
};

/**
 * 화면에 표시된 로딩 페이지 오버레이를 제거하고, 프로그레스 바 애니메이션을 중지합니다.
 * showS2Loading이 반환한 토큰과 짝을 맞추어 관리되므로, 아직 완료되지 않은 다른 show 요청(다른 토큰)이
 * 남아있다면 실제 오버레이 제거는 그 요청들이 모두 hide 될 때까지 지연된다.
 *
 * @param {string} [token] - showS2Loading()이 반환한 토큰. 생략하면 진행 중인 모든 요청과 무관하게 강제로 오버레이를 제거한다.
 * @returns {void}
 *
 * @example
 * import { showS2Loading, hideS2Loading } from './js/s2.loading.js'; // Thymeleaf 는 '[[@{/js/s2.loading.js}]]' 형태로 경로 지정
 * ex1) 토큰으로 정확히 짝을 맞춰 숨기기
 * const token = showS2Loading();
 * hideS2Loading(token);
 * ex2) 토큰 없이 강제로 숨기기
 * hideS2Loading();
 */
export const hideS2Loading = (token) => {
  if (token !== undefined && token !== null) {
    g_activeLoadingTokens.delete(token);
  } else {
    // 토큰 없이 호출되면(레거시 직접 호출 등) 진행 중인 다른 요청과 무관하게 강제로 초기화한다.
    g_activeLoadingTokens.clear();
  }

  if (g_activeLoadingTokens.size > 0) {
    // 아직 완료되지 않은 다른 요청이 남아있으므로 오버레이를 유지한다.
    return;
  }

  removeLoadingOverlay();
};
