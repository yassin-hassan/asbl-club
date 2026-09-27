package club.asbl.asbl_club.email;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;

// A ticket's QR code as a PNG image, drawn with ZXing (the standard Java QR library). Error correction M: still
// readable from a printed ticket with a crease or a phone screen with some glare. Same content as the QR code on
// the "My bookings" page, so the door scans either.
final class QrCodes {

    private static final int SIZE = 480;

    private QrCodes() {
    }

    static byte[] png(String content) {
        try {
            BitMatrix matrix = new QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, SIZE, SIZE,
                    Map.of(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M, EncodeHintType.MARGIN, 2));
            ByteArrayOutputStream png = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(matrix, "PNG", png);
            return png.toByteArray();
        } catch (WriterException e) {
            throw new IllegalArgumentException("Can't draw a QR code for this content", e);
        } catch (IOException e) {
            throw new UncheckedIOException(e); // writing to memory doesn't fail
        }
    }
}
