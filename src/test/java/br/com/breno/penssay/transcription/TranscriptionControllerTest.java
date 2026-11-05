package br.com.breno.penssay.transcription;

import br.com.breno.penssay.web.UsageKind;
import br.com.breno.penssay.web.UsageLimitException;
import com.openai.errors.OpenAIInvalidDataException;
import com.openai.errors.OpenAIIoException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TranscriptionController.class)
@Import(PhotoValidator.class)
class TranscriptionControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private TranscriptionService service;

    private ResultActions upload(String declaredType, byte[] content) throws Exception {
        return mvc.perform(multipart("/api/transcriptions")
                .file(new MockMultipartFile("photo", "redacao.jpg", declaredType, content)));
    }

    private ResultActions failWith(RuntimeException e) throws Exception {
        doThrow(e).when(service).transcribe(any(), any(), anyString());
        return upload("image/jpeg", PhotoValidatorTest.JPEG);
    }

    @Test
    void aMissingPhotoIs400() throws Exception {
        mvc.perform(multipart("/api/transcriptions"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Envie a foto da redação."));
        verifyNoInteractions(service);
    }

    @Test
    void aPdfIsRefusedWithoutCallingTheService() throws Exception {
        upload("image/jpeg", PhotoValidatorTest.PDF)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Envie uma foto em JPEG, PNG ou WebP."));
        verifyNoInteractions(service);
    }

    @Test
    void theServiceGetsTheTypeFromTheBytesNotFromTheClient() throws Exception {
        when(service.transcribe(any(), any(), anyString())).thenReturn("texto lido");

        upload("application/pdf", PhotoValidatorTest.JPEG)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.text").value("texto lido"));

        verify(service).transcribe(any(), eq(MimeTypeUtils.IMAGE_JPEG), anyString());
    }

    @Test
    void aPhotoAboveTheSizeLimitIs413() throws Exception {
        failWith(new MaxUploadSizeExceededException(10 * 1024 * 1024))
                .andExpect(status().isContentTooLarge())
                .andExpect(jsonPath("$.message").value("A foto é muito grande. Envie uma foto de até 10 MB."));
    }

    @Test
    void aReachedPhotoLimitHasThePhotoMessage() throws Exception {
        failWith(new UsageLimitException(UsageKind.TRANSCRIPTION, UsageLimitException.Scope.ORIGIN, 600))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "600"))
                .andExpect(jsonPath("$.message").value("Você atingiu o limite de fotos por agora. Tente de novo mais tarde."));
    }

    @Test
    void providerFailuresUseThePhotoMessages() throws Exception {
        failWith(new OpenAIInvalidDataException("bad answer"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.message").value("Não conseguimos ler a foto. Tente de novo."));
        failWith(new OpenAIIoException("down"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message")
                        .value("O serviço de leitura está indisponível no momento. Tente de novo em alguns minutos."));
    }
}
