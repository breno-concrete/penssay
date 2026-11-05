package br.com.breno.penssay.transcription;

import br.com.breno.penssay.web.InvalidRequestException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.MimeTypeUtils;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PhotoValidatorTest {

    private final PhotoValidator validator = new PhotoValidator();

    static byte[] bytes(int... values) {
        byte[] data = new byte[values.length];
        for (int i = 0; i < values.length; i++) {
            data[i] = (byte) values[i];
        }
        return data;
    }

    static byte[] padded(byte[] header) {
        return Arrays.copyOf(header, 64); // a real file goes on after the header
    }

    static final byte[] JPEG = padded(bytes(0xFF, 0xD8, 0xFF, 0xE0));
    static final byte[] PNG = padded(bytes(0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A));
    static final byte[] WEBP = padded(bytes('R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P'));
    static final byte[] PDF = padded(bytes('%', 'P', 'D', 'F', '-', '1', '.', '7'));

    private static MockMultipartFile photo(String declaredType, byte[] content) {
        return new MockMultipartFile("photo", "redacao.jpg", declaredType, content);
    }

    @Test
    void recognisesJpegPngAndWebpByTheirBytes() {
        assertThat(validator.detect(photo("application/octet-stream", JPEG))).isEqualTo(MimeTypeUtils.IMAGE_JPEG);
        assertThat(validator.detect(photo(null, PNG))).isEqualTo(MimeTypeUtils.IMAGE_PNG);
        assertThat(validator.detect(photo("image/jpeg", WEBP))).isEqualTo(PhotoValidator.WEBP);
    }

    @Test
    void refusesAPdfThatClaimsToBeAJpeg() {
        assertThatThrownBy(() -> validator.detect(photo("image/jpeg", PDF)))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Envie uma foto em JPEG, PNG ou WebP.");
    }

    @Test
    void refusesARiffFileThatIsNotWebp() {
        byte[] wav = padded(bytes('R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'A', 'V', 'E'));

        assertThatThrownBy(() -> validator.detect(photo("image/webp", wav)))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void refusesAMissingOrEmptyPhoto() {
        assertThatThrownBy(() -> validator.detect(null))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Envie a foto da redação.");
        assertThatThrownBy(() -> validator.detect(photo("image/jpeg", new byte[0])))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Envie a foto da redação.");
    }

    @Test
    void refusesAFileShorterThanAnyImageHeader() {
        assertThatThrownBy(() -> validator.detect(photo("image/jpeg", bytes(0xFF, 0xD8))))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Envie uma foto em JPEG, PNG ou WebP.");
    }
}
