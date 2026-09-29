package com.sih.nivara.assistant.speech;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

/**
 * Chooses the assistant's speech provider from {@code nivara.assistant.speech.provider}, as
 * validated by {@link SpeechProperties}: {@link DisabledSpeechClient} when unset, blank or
 * {@code none}, which is the default, or {@link SarvamSpeechClient} for {@code sarvam}. One bean
 * serves both directions, as both {@link SpeechToText} and {@link TextToSpeech}. An unknown
 * provider fails startup.
 */
@Configuration
@EnableConfigurationProperties(SpeechProperties.class)
public class SpeechConfiguration {

    @Bean
    SpeechClient speechClient(SpeechProperties properties, JsonMapper jsonMapper) {
        return switch (properties.provider()) {
            case NONE -> new DisabledSpeechClient();
            case SARVAM -> new SarvamSpeechClient(properties, jsonMapper);
        };
    }
}
