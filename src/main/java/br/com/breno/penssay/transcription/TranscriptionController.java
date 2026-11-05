package br.com.breno.penssay.transcription;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.util.MimeType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/api/transcriptions")
public class TranscriptionController {

    private final TranscriptionService transcriptionService;
    private final PhotoValidator photoValidator;

    public TranscriptionController(TranscriptionService transcriptionService, PhotoValidator photoValidator) {
        this.transcriptionService = transcriptionService;
        this.photoValidator = photoValidator;
    }

    // not required here so a missing photo gets the same message as an empty one, from PhotoValidator
    @PostMapping
    public TranscriptionResponse transcribe(@RequestParam(value = "photo", required = false) MultipartFile photo,
                                            HttpServletRequest http) throws IOException {
        MimeType type = photoValidator.detect(photo); // from the bytes, never from the client's content type
        String transcribed = transcriptionService.transcribe(photo.getBytes(), type, http.getRemoteAddr());
        return new TranscriptionResponse(transcribed);
    }
}
