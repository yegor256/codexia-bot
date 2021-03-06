package dev.iakunin.codexiabot.bot.config;

import dev.iakunin.codexiabot.bot.Up;
import dev.iakunin.codexiabot.bot.repository.StarsUpResultRepository;
import dev.iakunin.codexiabot.bot.up.Stars;
import dev.iakunin.codexiabot.codexia.CodexiaModule;
import dev.iakunin.codexiabot.github.GithubModule;
import lombok.AllArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@AllArgsConstructor
public class StarsUpConfig {

    private final GithubModule github;

    private final CodexiaModule codexia;

    private final StarsUpResultRepository repository;

    private final Stars bot;

    @Bean
    public Up starsUp() {
        return new Up(
            this.github,
            this.repository,
            this.bot,
            this.codexia
        );
    }
}
