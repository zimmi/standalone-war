package com.rmnsc.service;

import com.rmnsc.persistence.Daos;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 *
 * @author thomas
 */
@Configuration
@Import(Daos.class)
public class Services {

    @Autowired
    private Daos daos;

    @Bean
    public TodoService todoService() {
        return new TodoServiceImpl(daos.todoDao());
    }
}