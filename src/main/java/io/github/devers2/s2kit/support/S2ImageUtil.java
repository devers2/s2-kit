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

import java.awt.Color;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.Locale;
import java.util.Objects;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;

import io.github.devers2.s2util.exception.S2RuntimeException;
import io.github.devers2.s2kit.file.S2File;

/**
 * s2's utilities
 * <p>
 * 이미지 크기·형식 변환. 실패하면 null 이나 빈 문자열을 돌려주지 않고 원인을 담은 {@link S2RuntimeException}을 던진다.
 * </p>
 *
 * @author devers2
 * @version 2.0
 * @since 2025. 03. 07.
 */
public class S2ImageUtil {

    /**
     * Largest image (width × height) decoded, so a small file declaring a huge size cannot exhaust memory (100 megapixels,
     * about 400MB as ARGB) | 디코딩할 최대 픽셀 수. 작은 파일이 거대한 해상도를 선언해 메모리를 고갈시키지 못하도록 함 (1억 픽셀, ARGB 약 400MB)
     */
    public static final long MAX_PIXELS = 100_000_000L;

    private S2ImageUtil() {
    }

    /**
     * 이미지를 변경한다.(속도 개선을 위해 이미지 리사이즈와 포맷 변경을 동시에 처리)
     *
     * @param sourceFile           기존 파일
     * @param maxWidth             최대 너비(null: 기존 파일 너비 유지)
     * @param maxHeight            최대 높이(null: 기존 파일 높이 유지)
     * @param isFixedRate          가로세로 비율고정 여부(true: 비율 유지, false: 너비·높이를 각각 제한)
     * @param maxSize              최대 파일 용량
     * @param maxSizeOverExtension 최대 파일 용량 초과 시 변환할 확장자
     * @param newFileExtension     변경할 확장자(null: 기존 파일 확장자 유지, not null: 확장자 변경(maxSizeOverExtension 보다
     *                             우선))
     * @param newFilePath          변경할 파일 경로(null: 기존 파일 경로)
     * @param newFileName          변경할 확장자 포함 파일명(null: 기존 확장자 포함 파일명)
     * @param isSourceFileDelete   기존 파일 삭제 여부 (결과 파일이 원본과 다를 때 저장 후 바로 삭제)
     * @return 결과 정보
     * @throws S2RuntimeException 원본이 없거나 이미지가 아닐 때, 해당 형식으로 쓸 수 없을 때, 입출력 오류 시
     */
    public static S2File convertImage(Path sourceFile, Integer maxWidth, Integer maxHeight, boolean isFixedRate,
            Long maxSize, String maxSizeOverExtension, String newFileExtension, String newFilePath, String newFileName,
            boolean isSourceFileDelete) {
        requireFile(sourceFile);
        var sourceExtension = S2FileUtil.getExtension(sourceFile, true);
        var resultPath = targetPath(sourceFile, newFilePath, newFileName);
        try {
            var image = resize(readImage(sourceFile), maxWidth, maxHeight, isFixedRate);
            var encoded = encode(image, sourceExtension);

            var resultExtension = sourceExtension;
            if (newFileExtension != null && !newFileExtension.isBlank()) {
                resultExtension = newFileExtension.toLowerCase(Locale.ROOT);
            } else if (maxSize != null && maxSize < encoded.length && maxSizeOverExtension != null
                    && !maxSizeOverExtension.isBlank()) {
                resultExtension = maxSizeOverExtension.toLowerCase(Locale.ROOT);
            }
            if (!resultExtension.equals(sourceExtension)) {
                resultPath = withExtension(resultPath, resultExtension);
                encoded = encode(image, resultExtension);
            }

            createParent(resultPath);
            Files.write(resultPath, encoded);
            deleteSourceIfMoved(sourceFile, resultPath, isSourceFileDelete);
            return new S2File(resultPath, resultExtension, S2FileUtil.getBaseName(sourceFile));
        } catch (IOException e) {
            throw new S2RuntimeException("이미지 변환 실패: " + sourceFile, e);
        }
    }

    /**
     * 이미지 사이즈를 변경한다.
     *
     * @param sourceFile         기존 파일
     * @param maxWidth           최대 너비(null: 기존 파일 너비 유지)
     * @param maxHeight          최대 높이(null: 기존 파일 높이 유지)
     * @param isFixedRate        가로세로 비율고정 여부(true: 비율 유지, false: 너비·높이를 각각 제한)
     * @param newFilePath        변경할 파일 경로(null: 기존 파일 경로)
     * @param newFileName        변경할 확장자 포함 파일명(null: 기존 확장자 포함 파일명)
     * @param isSourceFileDelete 기존 파일 삭제 여부 (결과 파일이 원본과 다를 때 저장 후 바로 삭제)
     * @return 결과 정보
     * @throws S2RuntimeException 원본이 없거나 이미지가 아닐 때, 입출력 오류 시
     */
    public static S2File imageResize(Path sourceFile, Integer maxWidth, Integer maxHeight, boolean isFixedRate,
            String newFilePath, String newFileName, boolean isSourceFileDelete) {
        return convertImage(sourceFile, maxWidth, maxHeight, isFixedRate, null, null, null, newFilePath, newFileName,
                isSourceFileDelete);
    }

    /**
     * 이미지 사이즈를 변경한다.
     * <ul>
     * <li>비율 고정: 너비·높이 제한 안에 들어가도록 같은 비율로 줄인다.</li>
     * <li>비율 무시: 제한을 넘는 쪽만 제한값으로 줄인다 (예: 400×100, 최대 너비 200 → 200×100).</li>
     * <li>제한보다 작으면 키우지 않고 원본 이미지를 그대로 반환한다.</li>
     * </ul>
     *
     * @param imageInputStream 이미지 InputStream (닫지 않음)
     * @param maxWidth         최대 너비(null: 기존 너비 유지)
     * @param maxHeight        최대 높이(null: 기존 높이 유지)
     * @param isFixedRate      가로세로 비율고정 여부
     * @return 변경된 이미지
     * @throws S2RuntimeException 이미지가 아니거나 {@link #MAX_PIXELS}를 넘을 때, 입출력 오류 시
     */
    public static BufferedImage imageResize(InputStream imageInputStream, Integer maxWidth, Integer maxHeight,
            boolean isFixedRate) {
        Objects.requireNonNull(imageInputStream, "imageInputStream");
        try {
            var bytes = S2StreamUtil.streamToByteArray(imageInputStream, false);
            var image = applyOrientation(readImage(new ByteArrayInputStream(bytes)), exifOrientation(bytes));
            return resize(image, maxWidth, maxHeight, isFixedRate);
        } catch (IOException e) {
            throw new S2RuntimeException("이미지 크기 변경 실패", e);
        }
    }

    /**
     * 이미지 파일 포맷을 변경한다.
     *
     * @param sourceFile         기존 파일
     * @param newFileExtension   변경할 확장자(null: 기존 파일 확장자 유지)
     * @param newFilePath        변경할 파일 경로(null: 기존 파일 경로)
     * @param newFileName        변경할 확장자 포함 파일명(null: 기존 파일명 + 새 확장자)
     * @param isSourceFileDelete 기존 파일 삭제 여부 (결과 파일이 원본과 다를 때 저장 후 바로 삭제)
     * @return 결과 정보
     * @throws S2RuntimeException 원본이 없거나 이미지가 아닐 때, 해당 형식으로 쓸 수 없을 때, 입출력 오류 시
     */
    public static S2File convertImageExtension(Path sourceFile, String newFileExtension, String newFilePath,
            String newFileName, boolean isSourceFileDelete) {
        requireFile(sourceFile);
        var sourceExtension = S2FileUtil.getExtension(sourceFile, true);
        var resultExtension = newFileExtension != null && !newFileExtension.isBlank()
                ? newFileExtension.toLowerCase(Locale.ROOT)
                : sourceExtension;
        var fileName = newFileName != null && !newFileName.isBlank() ? newFileName
                : S2FileUtil.getBaseName(sourceFile) + (resultExtension.isBlank() ? "" : "." + resultExtension);
        var resultPath = targetPath(sourceFile, newFilePath, fileName);
        try {
            createParent(resultPath);
            if (resultExtension.equals(sourceExtension)) {
                if (!isSameFile(sourceFile, resultPath)) {
                    Files.copy(sourceFile, resultPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
            } else {
                Files.write(resultPath, encode(readImage(sourceFile), resultExtension));
            }
            deleteSourceIfMoved(sourceFile, resultPath, isSourceFileDelete);
            return new S2File(resultPath, resultExtension, S2FileUtil.getBaseName(sourceFile));
        } catch (IOException e) {
            throw new S2RuntimeException("이미지 형식 변경 실패: " + sourceFile, e);
        }
    }

    /**
     * 이미지(InputStream)를 Base64로 인코딩한다. 스트림은 닫지 않는다.
     *
     * @param inputStream InputStream
     * @return Base64 문자열
     * @throws S2RuntimeException 읽기 실패 시
     * @details
     *          <dl>
     *          <dd>img 태그에 사용하는 경우 src=`data:image/jpeg;base64,${base64Image 문자열}` 처럼 활용한다.</dd>
     *          </dl>
     */
    public static String encodeImageToBase64(InputStream inputStream) {
        return encodeImageToBase64(inputStream, false);
    }

    /**
     * 이미지(InputStream)를 Base64로 인코딩한다.
     *
     * @param inputStream       InputStream
     * @param shouldCloseStream inputStream 을 닫을지 여부
     * @return Base64 문자열
     * @throws NullPointerException inputStream 이 null 일 때
     * @throws S2RuntimeException   읽기 실패 또는 {@link S2StreamUtil#DEFAULT_MAX_BYTES} 초과 시
     */
    public static String encodeImageToBase64(InputStream inputStream, boolean shouldCloseStream) {
        return Base64.getEncoder().encodeToString(S2StreamUtil.streamToByteArray(inputStream, shouldCloseStream));
    }

    /** JPEG quality used when this class writes JPEG | 이 클래스가 JPEG 를 쓸 때의 품질 */
    public static final float JPEG_QUALITY = 0.9f;

    /**
     * Fits an image within {@code maxWidth} x {@code maxHeight} (keeping the ratio), upright per its EXIF orientation.
     * Large images are read subsampled, so a 50-megapixel photo never needs its full size in memory. Returns the
     * same array when nothing changes (already small enough and upright).
     * <p>
     * <b>[한국어 설명]</b>
     * </p>
     * 이미지를 비율을 지켜 {@code maxWidth} x {@code maxHeight} 안에 맞추고, EXIF 방향대로 바로 세운다. 큰 이미지는 처음부터 줄여 읽어(서브샘플링)
     * 5천만 화소 사진도 원래 크기만큼 메모리를 쓰지 않는다. 바꿀 것이 없으면(이미 작고 바로 서 있으면) 같은 배열을 돌려준다.
     * <ul>
     * <li>형식: JPEG 는 JPEG(품질 {@link #JPEG_QUALITY}), 그 외는 PNG(투명도 유지, 무손실)로 쓴다.</li>
     * <li>크기 비교는 바로 세운 뒤 기준이다 (세로 사진은 가로·세로가 바뀜).</li>
     * </ul>
     *
     * @param image     이미지 바이트
     * @param maxWidth  최대 너비 (픽셀)
     * @param maxHeight 최대 높이 (픽셀)
     * @return 맞춘 이미지 바이트, 바꿀 것이 없으면 {@code image} 그대로
     * @throws S2RuntimeException 이미지가 아니거나 {@link #MAX_PIXELS}를 넘을 때
     */
    public static byte[] resizeToFit(byte[] image, int maxWidth, int maxHeight) {
        Objects.requireNonNull(image, "image");
        if (maxWidth < 1 || maxHeight < 1) {
            throw new IllegalArgumentException("최대 크기는 1 이상이어야 합니다: " + maxWidth + "x" + maxHeight);
        }
        var orientation = exifOrientation(image);
        var quarter = orientation >= 5;
        try (var imageInput = ImageIO.createImageInputStream(new ByteArrayInputStream(image))) {
            var readers = imageInput == null ? null : ImageIO.getImageReaders(imageInput);
            if (readers == null || !readers.hasNext()) {
                throw new IOException("이미지를 읽을 수 없습니다 (지원하지 않는 형식이거나 손상된 파일)");
            }
            var reader = readers.next();
            BufferedImage decoded;
            String format;
            try {
                reader.setInput(imageInput, true, true);
                format = reader.getFormatName().toLowerCase(Locale.ROOT);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if ((long) width * height > MAX_PIXELS) {
                    throw new IOException("이미지 해상도가 너무 큽니다: " + width + "x" + height);
                }
                // Limits in stored orientation | 저장된 방향 기준 한도
                int limitW = quarter ? maxHeight : maxWidth;
                int limitH = quarter ? maxWidth : maxHeight;
                var scale = Math.min(1.0, Math.min((double) limitW / width, (double) limitH / height));
                if (scale >= 1.0 && orientation <= 1) {
                    return image;
                }
                var param = reader.getDefaultReadParam();
                // Read at 1/2, 1/4 ... while still at least the target size | 목표 크기 이상인 범위에서 1/2, 1/4 ... 로 읽음
                var step = Integer.highestOneBit(Math.max(1, (int) Math.floor(1 / scale)));
                if (step > 1) {
                    param.setSourceSubsampling(step, step, 0, 0);
                }
                decoded = reader.read(0, param);
                decoded = resize(decoded, (int) Math.round(width * scale), (int) Math.round(height * scale), true);
            } finally {
                reader.dispose();
            }
            decoded = applyOrientation(decoded, orientation);
            var jpeg = format.equals("jpeg") || format.equals("jpg");
            return encode(decoded, jpeg ? "jpg" : "png");
        } catch (IOException e) {
            throw new S2RuntimeException("이미지 크기 맞추기 실패: " + e.getMessage(), e);
        }
    }

    /**
     * Scales a decoded image to fit within the size, keeping the ratio (never enlarges) | 디코딩한 이미지를 비율을 지켜 크기 안에
     * 맞춤 (키우지 않음)
     *
     * @param image     이미지
     * @param maxWidth  최대 너비
     * @param maxHeight 최대 높이
     * @return 맞춘 이미지 (이미 작으면 같은 이미지)
     */
    public static BufferedImage scaleToFit(BufferedImage image, int maxWidth, int maxHeight) {
        return resize(Objects.requireNonNull(image, "image"), maxWidth, maxHeight, true);
    }

    /**
     * The EXIF orientation of a JPEG (1 = upright, 6 = turn 90 degrees clockwise, 3 = 180, 8 = 90 counter-clockwise,
     * 2/4/5/7 mirrored); 1 when absent or not a JPEG. Phones store portrait photos sideways with this tag.
     * <p>
     * <b>[한국어 설명]</b>
     * </p>
     * JPEG 의 EXIF 방향 (1 = 바로, 6 = 시계 방향 90도, 3 = 180도, 8 = 반시계 90도, 2/4/5/7 = 좌우 반전 포함). 없거나 JPEG 가 아니면 1. 휴대폰은 세로
     * 사진을 옆으로 눕혀 저장하고 이 값을 붙인다.
     *
     * @param image 이미지 바이트
     * @return 1 ~ 8
     */
    public static int exifOrientation(byte[] image) {
        if (image == null || image.length < 4 || (image[0] & 0xFF) != 0xFF || (image[1] & 0xFF) != 0xD8) {
            return 1;
        }
        var pos = 2;
        while (pos + 4 <= image.length && (image[pos] & 0xFF) == 0xFF) {
            var marker = image[pos + 1] & 0xFF;
            var length = ((image[pos + 2] & 0xFF) << 8) | (image[pos + 3] & 0xFF);
            if (marker == 0xDA || marker == 0xD9 || length < 2) {
                break; // Image data starts; no more metadata | 이미지 데이터 시작
            }
            var start = pos + 4;
            if (marker == 0xE1 && start + 14 <= image.length && image[start] == 'E' && image[start + 1] == 'x'
                    && image[start + 2] == 'i' && image[start + 3] == 'f') {
                var orientation = tiffOrientation(image, start + 6, Math.min(image.length, pos + 2 + length));
                if (orientation > 0) {
                    return orientation;
                }
            }
            pos += 2 + length;
        }
        return 1;
    }

    private static int tiffOrientation(byte[] b, int tiff, int end) {
        if (tiff + 8 > end) {
            return 0;
        }
        var little = b[tiff] == 'I' && b[tiff + 1] == 'I';
        if (!little && !(b[tiff] == 'M' && b[tiff + 1] == 'M')) {
            return 0;
        }
        var ifd = tiff + (int) readInt(b, tiff + 4, 4, little);
        if (ifd < tiff || ifd + 2 > end) {
            return 0;
        }
        var count = (int) readInt(b, ifd, 2, little);
        for (int i = 0; i < count; i++) {
            var entry = ifd + 2 + i * 12;
            if (entry + 12 > end) {
                return 0;
            }
            if (readInt(b, entry, 2, little) == 0x0112) {
                var value = (int) readInt(b, entry + 8, 2, little);
                return value >= 1 && value <= 8 ? value : 0;
            }
        }
        return 0;
    }

    private static long readInt(byte[] b, int at, int size, boolean little) {
        long value = 0;
        for (int i = 0; i < size; i++) {
            var octet = b[at + (little ? size - 1 - i : i)] & 0xFF;
            value = (value << 8) | octet;
        }
        return value;
    }

    /**
     * Turns an image upright for its EXIF orientation | EXIF 방향대로 이미지를 바로 세움
     *
     * @param image       이미지
     * @param orientation EXIF 방향 (1 ~ 8)
     * @return 바로 세운 이미지 (1 이면 같은 이미지)
     */
    public static BufferedImage applyOrientation(BufferedImage image, int orientation) {
        if (orientation <= 1 || orientation > 8) {
            return image;
        }
        int w = image.getWidth();
        int h = image.getHeight();
        var quarter = orientation >= 5;
        var transform = new AffineTransform();
        switch (orientation) {
        case 2 -> transform.setTransform(-1, 0, 0, 1, w, 0);
        case 3 -> transform.setTransform(-1, 0, 0, -1, w, h);
        case 4 -> transform.setTransform(1, 0, 0, -1, 0, h);
        case 5 -> transform.setTransform(0, 1, 1, 0, 0, 0);
        case 6 -> transform.setTransform(0, 1, -1, 0, h, 0);
        case 7 -> transform.setTransform(0, -1, -1, 0, h, w);
        case 8 -> transform.setTransform(0, -1, 1, 0, 0, w);
        default -> {
            return image;
        }
        }
        var type = image.getColorModel().hasAlpha() ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB;
        var output = new BufferedImage(quarter ? h : w, quarter ? w : h, type);
        var graphics = output.createGraphics();
        try {
            graphics.drawImage(image, transform, null);
        } finally {
            graphics.dispose();
        }
        return output;
    }

    // ------------------------------------------------------------------------

    private static void requireFile(Path sourceFile) {
        Objects.requireNonNull(sourceFile, "sourceFile");
        if (!Files.isRegularFile(sourceFile)) {
            throw new S2RuntimeException("이미지 파일이 없습니다: " + sourceFile);
        }
    }

    private static Path targetPath(Path sourceFile, String newFilePath, String newFileName) {
        var directory = newFilePath != null && !newFilePath.isBlank() ? Paths.get(newFilePath)
                : sourceFile.toAbsolutePath().getParent();
        var name = newFileName != null && !newFileName.isBlank() ? newFileName : sourceFile.getFileName().toString();
        return directory.resolve(name);
    }

    private static Path withExtension(Path path, String extension) {
        var name = path.getFileName().toString();
        var dot = name.lastIndexOf('.');
        return path.resolveSibling((dot > 0 ? name.substring(0, dot) : name) + "." + extension);
    }

    private static void createParent(Path path) throws IOException {
        var parent = path.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
    }

    private static boolean isSameFile(Path a, Path b) throws IOException {
        return Files.exists(b) && Files.isSameFile(a, b);
    }

    /** Deletes right after the result is written; all streams on the source are closed by then | 결과를 쓴 직후 삭제 (원본 스트림은 이미 닫힘) */
    private static void deleteSourceIfMoved(Path sourceFile, Path resultPath, boolean isSourceFileDelete)
            throws IOException {
        if (isSourceFileDelete && !isSameFile(sourceFile, resultPath)) {
            Files.deleteIfExists(sourceFile);
        }
    }

    /** Reads and turns upright per EXIF, since re-encoding drops the tag | 읽고 EXIF 대로 바로 세움 (다시 쓰면 태그가 사라지므로) */
    private static BufferedImage readImage(Path file) throws IOException {
        var bytes = Files.readAllBytes(file);
        return applyOrientation(readImage(new ByteArrayInputStream(bytes)), exifOrientation(bytes));
    }

    /** Reads the declared size first and refuses huge images before decoding | 선언된 크기를 먼저 읽어 거대한 이미지는 디코딩 전에 거부 */
    private static BufferedImage readImage(InputStream in) throws IOException {
        try (var imageInput = ImageIO.createImageInputStream(in)) {
            var readers = imageInput == null ? null : ImageIO.getImageReaders(imageInput);
            if (readers == null || !readers.hasNext()) {
                throw new IOException("이미지를 읽을 수 없습니다 (지원하지 않는 형식이거나 손상된 파일)");
            }
            var reader = readers.next();
            try {
                reader.setInput(imageInput, true, true);
                var pixels = (long) reader.getWidth(0) * reader.getHeight(0);
                if (pixels > MAX_PIXELS) {
                    throw new IOException("이미지 해상도가 너무 큽니다: " + reader.getWidth(0) + "x" + reader.getHeight(0));
                }
                return reader.read(0);
            } finally {
                reader.dispose();
            }
        }
    }

    private static BufferedImage resize(BufferedImage image, Integer maxWidth, Integer maxHeight, boolean isFixedRate) {
        int width = image.getWidth();
        int height = image.getHeight();
        int newWidth = width;
        int newHeight = height;
        if (isFixedRate) {
            double scale = 1.0;
            if (maxWidth != null && width > maxWidth) {
                scale = Math.min(scale, (double) maxWidth / width);
            }
            if (maxHeight != null && height > maxHeight) {
                scale = Math.min(scale, (double) maxHeight / height);
            }
            newWidth = Math.max(1, (int) Math.round(width * scale));
            newHeight = Math.max(1, (int) Math.round(height * scale));
        } else {
            if (maxWidth != null && width > maxWidth) {
                newWidth = Math.max(1, maxWidth);
            }
            if (maxHeight != null && height > maxHeight) {
                newHeight = Math.max(1, maxHeight);
            }
        }
        if (newWidth == width && newHeight == height) {
            return image;
        }
        // TYPE_CUSTOM (e.g. some PNGs) cannot be constructed, so use a standard type | TYPE_CUSTOM 은 생성할 수 없으므로 표준 타입 사용
        var type = image.getColorModel().hasAlpha() ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB;
        var output = new BufferedImage(newWidth, newHeight, type);
        var graphics = output.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.drawImage(image, 0, 0, newWidth, newHeight, null);
        } finally {
            graphics.dispose();
        }
        return output;
    }

    /**
     * Encodes to the format, flattening transparency onto white for formats without alpha (jpg, bmp) | 알파가 없는
     * 형식(jpg, bmp)은 투명 영역을 흰색으로 합성
     */
    private static byte[] encode(BufferedImage image, String format) throws IOException {
        var target = image;
        if (!format.equals("png") && !format.equals("gif") && (image.getColorModel().hasAlpha()
                || image.getType() == BufferedImage.TYPE_CUSTOM)) {
            target = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
            var graphics = target.createGraphics();
            try {
                graphics.drawImage(image, 0, 0, Color.WHITE, null);
            } finally {
                graphics.dispose();
            }
        }
        var out = new ByteArrayOutputStream();
        if (format.equals("jpg") || format.equals("jpeg")) {
            // An explicit quality: ImageIO's default (0.75) visibly blurs photos | 품질 지정: ImageIO 기본값(0.75)은 사진이 눈에 띄게 뭉개짐
            var writer = ImageIO.getImageWritersByFormatName("jpeg").next();
            try (var output = ImageIO.createImageOutputStream(out)) {
                var param = writer.getDefaultWriteParam();
                param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                param.setCompressionQuality(JPEG_QUALITY);
                writer.setOutput(output);
                writer.write(null, new IIOImage(target, null, null), param);
            } finally {
                writer.dispose();
            }
            return out.toByteArray();
        }
        if (!ImageIO.write(target, format, out)) {
            throw new IOException("이미지를 이 형식으로 쓸 수 없습니다: " + format);
        }
        return out.toByteArray();
    }
}
