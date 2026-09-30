/**
 * S2 Support Library
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
package io.github.devers2.s2util.file;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.Cleaner;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.github.devers2.s2util.support.S2FileUtil;
import io.github.devers2.s2util.support.S2StreamUtil;

/**
 * s2's utilities try-with-resources 구문 사용 가능한 InputStream 전달 객체
 *
 * @author devers2
 * @version 1.0
 * @since 2020. 07. 08.
 * @details
 *          <dl>
 *          <dd>InputStream 과 함께 Closeable 이나 File 객체들을 같이 전달하여 InputStream 이 close 될때 해당 객체들도 close 되거나 삭제 되어 메모리 누수를 방지한다.</dd>
 *          <dd>호출 측이 close() 를 명시적으로 호출하는 것이 정석이며, 그 경우 아래 로직이 즉시 동기적으로 실행된다.
 *          다만 호출 측이 close() 를 깜빡하는 경우에 대비해 {@link Cleaner} 기반 안전망도 함께 등록해두어,
 *          이 객체가 GC 대상이 될 때 최소한 뒤늦게라도 리소스가 정리되도록 한다 (명시적 close() 를 대체하는
 *          것이 아니라, 그것이 누락됐을 때의 최후의 보루다).</dd>
 *          </dl>
 */
public class S2ResourceInputStream extends InputStream { // AutoCloseable

    private static final Cleaner CLEANER = Cleaner.create();

    /**
     * 정리(clean-up) 로직과 그 대상 리소스를 담는 상태 객체.
     * Cleaner 에 등록되는 대상은 반드시 바깥 클래스(S2ResourceInputStream) 인스턴스를 참조하지
     * 않아야 한다 - 참조하면 그 인스턴스가 영원히 GC 대상이 되지 못해 Cleaner 가 무용지물이 된다.
     * 그래서 정적 중첩 클래스로 분리한다.
     */
    private static final class State implements Runnable {
        private final InputStream delegate;
        private List<Closeable> closeableList;
        private List<Path> tempFileList;
        private volatile boolean closed = false;

        State(InputStream delegate) {
            this.delegate = delegate;
        }

        synchronized void setCloseable(Closeable... closeables) {
            if (closeables != null) {
                if (this.closeableList == null) {
                    this.closeableList = new ArrayList<>();
                }
                Collections.addAll(this.closeableList, closeables);
            }
        }

        synchronized void setTempFile(Path... tempFiles) {
            if (tempFiles != null) {
                if (this.tempFileList == null) {
                    this.tempFileList = new ArrayList<>();
                }
                Collections.addAll(this.tempFileList, tempFiles);
            }
        }

        synchronized List<Path> getTempFiles() {
            return this.tempFileList != null ? Collections.unmodifiableList(new ArrayList<>(this.tempFileList))
                    : Collections.emptyList();
        }

        boolean isClosed() {
            return closed;
        }

        // Cleaner.Cleanable#clean() 이 명시적 close() 호출이든 GC 트리거든 최대 1회만 실행되도록 보장한다.
        @Override
        public synchronized void run() {
            closed = true;
            S2StreamUtil.closeStream(delegate);
            if (closeableList != null) {
                for (var closeable : closeableList) {
                    S2StreamUtil.closeStream(closeable);
                }
            }
            if (tempFileList != null) {
                for (var tempFile : tempFileList) {
                    S2FileUtil.deleteQuietly(tempFile); // A cleaner must not throw | Cleaner 는 예외를 던지면 안 됨
                }
            }
        }
    }

    private final InputStream delegate;
    private final State state;
    private final Cleaner.Cleanable cleanable;

    public S2ResourceInputStream(InputStream inputStream) {
        this.delegate = inputStream;
        this.state = new State(inputStream);
        this.cleanable = CLEANER.register(this, state);
    }

    public S2ResourceInputStream(InputStream inputStream, Closeable... closeables) {
        this(inputStream);
        this.setCloseable(closeables);
    }

    public S2ResourceInputStream(InputStream inputStream, Path... tempFiles) {
        this(inputStream);
        this.setTempFile(tempFiles);
    }

    public void setCloseable(Closeable... closeables) {
        state.setCloseable(closeables);
    }

    public void setTempFile(Path... tempFiles) {
        state.setTempFile(tempFiles);
    }

    public List<Path> getTempFiles() {
        return state.getTempFiles();
    }

    public boolean isClosed() {
        return state.isClosed();
    }

    @Override
    public int read() throws IOException {
        if (isClosed()) {
            throw new IOException("Stream is closed");
        }
        return delegate.read();
    }

    @Override
    public int read(byte[] b) throws IOException {
        if (isClosed()) {
            throw new IOException("Stream is closed");
        }
        return delegate.read(b);
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        if (isClosed()) {
            throw new IOException("Stream is closed");
        }
        return delegate.read(b, off, len);
    }

    @Override
    public long skip(long n) throws IOException {
        if (isClosed()) {
            throw new IOException("Stream is closed");
        }
        return delegate.skip(n);
    }

    @Override
    public int available() throws IOException {
        if (isClosed()) {
            throw new IOException("Stream is closed");
        }
        return delegate.available();
    }

    @Override
    public void close() {
        // Cleanable#clean() 은 명시적 호출이든(지금) 이후 GC 트리거에 의한 것이든 State#run() 이
        // 최대 1회만 실행되도록 보장하고, 명시적으로 호출한 이 경로에서는 호출한 스레드에서
        // 즉시(동기적으로) 실행된다 - 기존의 "close() 하면 바로 정리된다"는 동작과 동일하다.
        cleanable.clean();
    }

    @Override
    public void mark(int readlimit) {
        delegate.mark(readlimit);
    }

    @Override
    public void reset() throws IOException {
        if (isClosed()) {
            throw new IOException("Stream is closed");
        }
        delegate.reset();
    }

    @Override
    public boolean markSupported() {
        return delegate.markSupported();
    }
}
