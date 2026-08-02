package com.earthscan.forum.config;

import com.earthscan.common.config.OpenApiFactory;
import io.swagger.v3.oas.models.OpenAPI;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI forumServiceOpenApi() {
        return OpenApiFactory.create(
                "Forum Service",
                """
                Community Q&A between farmers and agriculture experts. Owns the MongoDB
                `earthscan_forum` database, where each thread is a single document with its replies
                embedded.

                Publishes `forum.post.created` and `forum.comment.added`; consumes `user.deleted`
                to anonymise a removed user's contributions while preserving thread readability.
                """,
                "1.0.0");
    }
}
