package com.sep.psp.auth.mfa;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

/**
 * QR kod za uparivanje Authenticator aplikacije, kao SVG data URL.
 * Generise se na serveru, pa tajna ne napusta PSP preko servisa trece strane.
 */
@Service
public class QrCodeService {

    public String svgDataUrl(String content) {
        try {
            BitMatrix matrix = new QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, 0, 0,
                    Map.of(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M, EncodeHintType.MARGIN, 2));
            int width = matrix.getWidth();
            int height = matrix.getHeight();
            StringBuilder path = new StringBuilder();
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    if (matrix.get(x, y)) {
                        path.append('M').append(x).append(' ').append(y).append("h1v1h-1z");
                    }
                }
            }
            String svg = "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 " + width + " " + height
                    + "\" shape-rendering=\"crispEdges\"><rect width=\"100%\" height=\"100%\" fill=\"#FFFFFF\"/>"
                    + "<path fill=\"#13264A\" d=\"" + path + "\"/></svg>";
            return "data:image/svg+xml;base64,"
                    + Base64.getEncoder().encodeToString(svg.getBytes(StandardCharsets.UTF_8));
        } catch (WriterException e) {
            throw new IllegalStateException("QR kod nije moguce generisati", e);
        }
    }
}
