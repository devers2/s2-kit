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
package io.github.devers2.s2kit.support;

import java.nio.charset.StandardCharsets;

/**
 * Removes the C0 control characters (U+0000 to U+001F) from the format 4 character maps of a TrueType font, so a glyph
 * they share with the space is written to the PDF as a space.
 * <p>
 * <b>[한국어 설명]</b>
 * </p>
 * TrueType 폰트의 format 4 문자 맵(cmap)에서 C0 제어 문자(U+0000~U+001F)를 지운다. 나눔고딕처럼 제어 문자와 공백(U+0020)을 같은 글리프에 연결한
 * 폰트는, PDF 에 글리프의 문자를 기록할 때 가장 작은 코드인 U+0000 이 골라져 PDF 에서 복사·검색한 공백이 빈 문자가 된다. 제어 문자는 화면에 그리지 않으므로
 * 모양은 그대로이며, 공백만 남아 올바르게 기록된다.
 */
final class S2FontCmap {

    private static final int LAST_CONTROL = 0x1F;

    private S2FontCmap() {
    }

    /**
     * A copy without control-character mappings, or the same array when there are none or the font cannot be read
     * | 제어 문자 연결을 지운 사본. 없거나 읽을 수 없으면 같은 배열
     */
    static byte[] withoutControlCharacters(byte[] font) {
        if (font == null || font.length < 12) {
            return font;
        }
        try {
            var copy = font.clone();
            var cmap = tableOffset(copy, "cmap");
            if (cmap < 0) {
                return font;
            }
            var changed = false;
            var subtables = u16(copy, cmap + 2);
            for (int i = 0; i < subtables; i++) {
                var record = cmap + 4 + i * 8;
                var offset = cmap + (int) u32(copy, record + 4);
                if (u16(copy, offset) == 4) {
                    changed |= clearFormat4(copy, offset);
                }
            }
            return changed ? copy : font;
        } catch (RuntimeException e) {
            // An unusual table layout: use the font as it is | 특이한 표 구조: 폰트를 그대로 씀
            return font;
        }
    }

    private static int tableOffset(byte[] font, String tag) {
        var tables = u16(font, 4);
        for (int i = 0; i < tables; i++) {
            var record = 12 + i * 16;
            if (new String(font, record, 4, StandardCharsets.US_ASCII).equals(tag)) {
                return (int) u32(font, record + 8);
            }
        }
        return -1;
    }

    /**
     * Points control characters at glyph 0 (.notdef) in a format 4 subtable: entries of the glyph array, or the delta
     * of a single-character segment; multi-character delta segments map to distinct glyphs, not to the space | format 4
     * 하위 표에서 제어 문자를 글리프 0 으로. 글리프 배열 항목이나 한 글자 구간의 델타를 고친다 (여러 글자의 델타 구간은 서로 다른 글리프라 공백과 겹치지 않음)
     */
    private static boolean clearFormat4(byte[] font, int offset) {
        var segX2 = u16(font, offset + 6);
        var endCodes = offset + 14;
        var startCodes = endCodes + segX2 + 2;
        var deltas = startCodes + segX2;
        var rangeOffsets = deltas + segX2;
        var changed = false;
        for (int s = 0; s < segX2 / 2; s++) {
            var start = u16(font, startCodes + s * 2);
            var end = u16(font, endCodes + s * 2);
            if (start > LAST_CONTROL) {
                continue;
            }
            var rangeOffset = u16(font, rangeOffsets + s * 2);
            if (rangeOffset != 0) {
                for (int code = start; code <= Math.min(end, LAST_CONTROL); code++) {
                    var at = rangeOffsets + s * 2 + rangeOffset + (code - start) * 2;
                    if (u16(font, at) != 0) {
                        put16(font, at, 0);
                        changed = true;
                    }
                }
            } else if (start == end) {
                // glyph = (code + delta) mod 65536 → 0 | 글리프 = (코드 + 델타) mod 65536 → 0
                var delta = (-start) & 0xFFFF;
                if (u16(font, deltas + s * 2) != delta) {
                    put16(font, deltas + s * 2, delta);
                    changed = true;
                }
            }
        }
        return changed;
    }

    private static int u16(byte[] b, int at) {
        return ((b[at] & 0xFF) << 8) | (b[at + 1] & 0xFF);
    }

    private static long u32(byte[] b, int at) {
        return ((long) u16(b, at) << 16) | u16(b, at + 2);
    }

    private static void put16(byte[] b, int at, int value) {
        b[at] = (byte) (value >>> 8);
        b[at + 1] = (byte) value;
    }
}
