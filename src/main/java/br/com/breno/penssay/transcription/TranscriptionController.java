package br.com.breno.penssay.transcription;

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

    public TranscriptionController(TranscriptionService transcriptionService) {
        this.transcriptionService = transcriptionService;
    }

    @PostMapping
    public TranscriptionResponse transcribe(@RequestParam("photo") MultipartFile photo) throws IOException {
        byte[] photoBytes = photo.getBytes();

        String type = photo.getContentType();

        String transcribed = transcriptionService.transcribe(photoBytes, type);

        return new TranscriptionResponse(transcribed);


    }
}
