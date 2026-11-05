package br.com.breno.penssay.transcription;

import com.openai.errors.OpenAIInvalidDataException;
import com.openai.errors.OpenAIIoException;
import br.com.breno.penssay.web.UsageKind;
import br.com.breno.penssay.web.UsageLimiter;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeType;

@Service
public class TranscriptionService {

    // an ENEM essay has at most 30 lines (~500 words); the cap stops a looping answer from burning credits
    private static final int MAX_TRANSCRIPTION_TOKENS = 2048;

    private final ChatClient chatClient;
    private final Resource prompt;
    private final String model;
    private final UsageLimiter usageLimiter;

    public TranscriptionService(
            ChatClient.Builder builder,
            @Value("classpath:/prompts/transcription.st") Resource prompt,
            @Value("${penssay.transcription.model}") String model,
            UsageLimiter usageLimiter) {

        this.chatClient = builder.build();
        this.prompt = prompt;
        this.model = model;
        this.usageLimiter = usageLimiter;
    }

    // mimeType is the one PhotoValidator detected from the bytes
    public String transcribe(byte[] photo, MimeType mimeType, String origin) {
        // reserved before the paid call; refuses with UsageLimitException when a limit is reached
        usageLimiter.acquire(UsageKind.TRANSCRIPTION, origin);
        Resource image = new ByteArrayResource(photo);

        try {
            return askModel(mimeType, image);
        } catch (OpenAIInvalidDataException | OpenAIIoException e) {
            // the provider sometimes resets the connection mid-answer; one more try usually goes through
            return askModel(mimeType, image);
        }
    }

    private String askModel(MimeType mimeType, Resource image) {
        return chatClient.prompt()
                .user(u -> u.text(prompt).media(mimeType, image)) // o que será enviado (imagem + texto)
                .options(ChatOptions.builder().model(model).maxTokens(MAX_TRANSCRIPTION_TOKENS)) // qual modelo será usado
                .call() // esperar a resposta do modelo
                .content(); //devolve ums String que será o texto da resposta

    }
}
