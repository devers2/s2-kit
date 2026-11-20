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
package io.github.devers2.s2kit.file.impl;

import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import io.github.devers2.s2util.core.S2Util;
import io.github.devers2.s2util.core.S2ThreadUtil;
import io.github.devers2.s2util.log.S2LogManager;
import io.github.devers2.s2util.log.S2Logger;

/**
 * Shared watchdog for SFTP transfers: one daemon thread checks stalled uploads and abandoned download streams, and the
 * network work of cleaning them up runs on the common executor. Internal to the file managers in this library.
 * <p>
 * <b>[한국어 설명]</b>
 * </p>
 * SFTP 전송 감시용 공용 스케줄러입니다. 데몬 스레드 1개가 멈춘 업로드와 방치된 다운로드 스트림을 점검하고, 네트워크를 쓰는
 * 정리(채널 끊기·세션 반납)는 공용 실행기에서 돌립니다. 이 라이브러리의 파일 관리자 내부용입니다.
 */
public final class S2TransferWatchdog {

    private static final S2Logger logger = S2LogManager.getLogger(S2TransferWatchdog.class);

    /*
     * 전송 감시용 공용 스케줄러. 예전처럼 전송마다 Timer 스레드를 만들면 대량 요청에서 요청 수만큼 스레드가 생겼다 사라진다.
     * 점검만 데몬 스레드 1개가 하고, 네트워크를 쓰는 정리(채널 끊기·세션 반납)는 공용 실행기로 넘겨 점검이 밀리지 않게 한다.
     */
    private static final ScheduledThreadPoolExecutor WATCHDOG = createWatchdog();

    private S2TransferWatchdog() {
    }

    private static ScheduledThreadPoolExecutor createWatchdog() {
        var executor = new ScheduledThreadPoolExecutor(1, task -> {
            var thread = new Thread(task, "s2-sftp-watchdog");
            thread.setDaemon(true);
            return thread;
        });
        executor.setRemoveOnCancelPolicy(true); // 끝난 전송의 점검 작업이 큐에 쌓이지 않도록
        return executor;
    }

    /**
     * Runs a cleanup on the common executor, or right here when the executor refuses it (for example while shutting
     * down).
     * <p>
     * <b>[한국어 설명]</b>
     * </p>
     * 정리 작업을 공용 실행기에서 돌린다. 실행기가 받지 않으면(종료 중 등) 그 자리에서 돌린다.
     * @param cleanup Cleanup to run | 돌릴 정리 작업
     */
    public static void runCleanup(Runnable cleanup) {
        try {
            S2ThreadUtil.getCommonExecutor().execute(cleanup);
        } catch (RejectedExecutionException e) {
            cleanup.run();
        }
    }

    /**
     * Schedules a periodic check; cancel the returned future when the transfer ends.
     * <p>
     * <b>[한국어 설명]</b>
     * </p>
     * 주기 점검을 등록한다. 전송이 끝나면 돌려받은 future 를 취소한다.
     * @param check          Check to run | 돌릴 점검
     * @param intervalMillis Interval in milliseconds | 점검 주기 (밀리초)
     * @return Future to cancel | 취소할 future
     */
    public static ScheduledFuture<?> scheduleCheck(Runnable check, long intervalMillis) {
        var interval = Math.max(1L, intervalMillis);
        return WATCHDOG.scheduleWithFixedDelay(() -> {
            try {
                check.run();
            } catch (RuntimeException e) {
                if (S2Util.isKorean()) {
                    logger.warn("SFTP 전송 감시 중 오류: {}", e.toString()); // 예외가 나가면 이후 점검이 멈추므로 삼킨다
                } else {
                    logger.warn("Error in the SFTP transfer watchdog: {}", e.toString()); // 예외가 나가면 이후 점검이 멈추므로 삼킨다
                }
            }
        }, interval, interval, TimeUnit.MILLISECONDS);
    }

}
