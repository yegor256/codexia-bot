package dev.iakunin.codexiabot.codexia.cron;

import dev.iakunin.codexiabot.codexia.entity.CodexiaProject;
import dev.iakunin.codexiabot.codexia.repository.CodexiaProjectRepository;
import dev.iakunin.codexiabot.codexia.sdk.CodexiaClient;
import dev.iakunin.codexiabot.github.GithubModule;
import java.util.Objects;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@AllArgsConstructor(onConstructor_={@Autowired})
public final class ProjectsHealthCheck implements Runnable {

    private final CodexiaClient codexiaClient;

    private final CodexiaProjectRepository repository;

    private final GithubModule githubModule;

    public void run() {
         this.repository.findAllActive()
             .stream()
             .map(p -> this.codexiaClient.getProject(p.getExternalId()))
             .map(HttpEntity::getBody)
             .map(Objects::requireNonNull)
             .filter(p -> p.getDeleted() != null)
             .forEach(p -> {
                 this.deleteRepoSources(p);
                 this.updateEntity(p);
             });
    }

    private void deleteRepoSources(CodexiaClient.Project project) {
        this.githubModule.removeAllRepoSources(
            new GithubModule.DeleteArguments()
                .setSource(GithubModule.Source.CODEXIA)
                .setExternalId(String.valueOf(project.getId()))
        );
    }

    private void updateEntity(CodexiaClient.Project project) {
        final CodexiaProject foundCodexiaProject = this.repository
            .findByExternalId(project.getId())
            .orElseThrow(
                () -> new RuntimeException(
                    String.format(
                        "Unable to find CodexiaProject by externalId='%s'",
                        project.getId()
                    )
                )
            );
        this.repository.save(
            foundCodexiaProject.setDeleted(project.getDeleted())
        );
    }
}
