package com.minidc.server.config;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.file.Path;

@Configuration
@Profile("embedded")
public class EmbeddedPgConfig {

    @Bean(destroyMethod = "close")
    EmbeddedPostgres embeddedPostgres() throws IOException {
        return EmbeddedPostgres.builder()
                .setDataDirectory(Path.of("./data/pg"))
                .setCleanDataDirectory(false)      // keep data between runs
                .setPort(5432)
                .start();
    }

    @Bean
    DataSource dataSource(EmbeddedPostgres pg) {
        return pg.getPostgresDatabase();       // db "postgres", user "postgres"
    }
}
