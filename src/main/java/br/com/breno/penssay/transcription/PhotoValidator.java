package br.com.breno.penssay.transcription;

import br.com.breno.penssay.web.InvalidRequestException;
import org.springframework.stereotype.Component;
import org.springframework.util.MimeType;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;

/**
 * Decides the photo type from its first bytes. The file name and the declared content type come from the client and
 * are never trusted: a PDF renamed to .jpg is refused here, before any paid call.
 */
@Component
public class PhotoValidator {

    static final MimeType WEBP = MimeType.valueOf("image/webp");
    private static final int HEADER_LENGTH = 12;

    public MimeType detect(MultipartFile photo) {
        if (photo == null || photo.isEmpty()) {
            throw new InvalidRequestException("Envie a foto da redação.");
        }
        byte[] header = readHeader(photo);
        if (startsWith(header, 0, 0xFF, 0xD8, 0xFF)) {
            return MimeTypeUtils.IMAGE_JPEG;
        }
        if (startsWith(header, 0, 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A)) {
            return MimeTypeUtils.IMAGE_PNG;
        }
        // RIFF container whose form type, at byte 8, is WEBP
        if (startsWith(header, 0, 'R', 'I', 'F', 'F') && startsWith(header, 8, 'W', 'E', 'B', 'P')) {
            return WEBP;
        }
        throw new InvalidRequestException("Envie uma foto em JPEG, PNG ou WebP.");
    }

    private static byte[] readHeader(MultipartFile photo) {
        try (InputStream in = photo.getInputStream()) {
            return in.readNBytes(HEADER_LENGTH);
        } catch (IOException e) {
            throw new InvalidRequestException("Envie a foto da redação.");
        }
    }

    private static boolean startsWith(byte[] data, int offset, int... expected) {
        if (data.length < offset + expected.length) {
            return false;
        }
        for (int i = 0; i < expected.length; i++) {
            if ((data[offset + i] & 0xFF) != expected[i]) {
                return false;
            }
        }
        return true;
    }
}
