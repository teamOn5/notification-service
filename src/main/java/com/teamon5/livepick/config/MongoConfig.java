package com.teamon5.livepick.config;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;

@Configuration
public class MongoConfig {

    @Value("${spring.data.mongodb.uri:mongodb+srv://tjsghkwlsgh_db_user:1234@cluster0.61vehwi.mongodb.net/live_stream_db?retryWrites=true&w=majority}")
    private String connectionString;

    @Bean
    public MongoClient mongoClient() {
        return MongoClients.create(connectionString);
    }

    @Bean
    public MongoTemplate mongoTemplate() {
        return new MongoTemplate(mongoClient(), "live_stream_db");
    }
}
