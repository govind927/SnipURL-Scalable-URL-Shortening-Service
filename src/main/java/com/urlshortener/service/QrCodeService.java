package com.urlshortener.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Generates QR codes for short URLs using Google ZXing library.
 *
 * Output: PNG image as byte array
 * Size:   300x300 pixels (configurable)
 * Error correction: Level H (30% damage tolerance)
 *
 * Why ZXing?
 *  - Google's battle-tested QR library
 *  - Pure Java, no native dependencies
 *  - Used by millions of apps
 */
@Service
@Slf4j
public class QrCodeService {

    private static final int    DEFAULT_SIZE = 300;
    private static final String IMAGE_FORMAT = "PNG";

    /**
     * Generates a QR code PNG for the given URL.
     *
     * @param url   the full URL to encode (e.g. http://localhost:8080/aaaaab)
     * @param size  pixel width and height of the output image
     * @return PNG image as byte array
     */
    public byte[] generateQrCode(String url, int size) {
        try {
            QRCodeWriter writer = new QRCodeWriter();

            Map<EncodeHintType, Object> hints = new HashMap<>();
            hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.H);
            hints.put(EncodeHintType.MARGIN, 2);
            hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");

            BitMatrix bitMatrix = writer.encode(url, BarcodeFormat.QR_CODE, size, size, hints);

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(bitMatrix, IMAGE_FORMAT, outputStream);

            log.debug("Generated QR code for URL: {}", url);
            return outputStream.toByteArray();

        } catch (WriterException | IOException e) {
            log.error("Failed to generate QR code for URL {}: {}", url, e.getMessage());
            throw new RuntimeException("Failed to generate QR code", e);
        }
    }

    public byte[] generateQrCode(String url) {
        return generateQrCode(url, DEFAULT_SIZE);
    }
}
