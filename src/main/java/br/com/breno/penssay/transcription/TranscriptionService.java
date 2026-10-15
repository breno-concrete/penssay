package br.com.breno.penssay.transcription;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeType;
import org.springframework.util.MimeTypeUtils;

@Service
public class TranscriptionService {

    private final ChatClient chatClient;
    private final Resource prompt;
    private final String model;

    public TranscriptionService(
            ChatClient.Builder builder,
            @Value("classpath:/prompts/transcription.st") Resource prompt,
            @Value("${penssay.transcription.model}") String model) {

        this.chatClient = builder.build();
        this.prompt = prompt;
        this.model = model;
    }

    public String transcribe(byte[] photo, String contentType){
        Resource image = new ByteArrayResource(photo);

        MimeType mimeType = MimeTypeUtils.parseMimeType(contentType);

        return chatClient.prompt()
                .user(u -> u.text(prompt).media(mimeType, image)) // o que será enviado (imagem + texto)
                .options(OllamaChatOptions.builder().model(model)) // qual modelo será usado
                .call() // esperar a resposta do modelo
                .content(); //devolve ums String que será o texto da resposta

    }
}
