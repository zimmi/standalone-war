
package com.rmnsc.web;

import com.rmnsc.service.Services;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 *
 * @author thomas
 */
@Configuration
@Import({WebConfig.class, Services.class})
public class Controllers {

    @Autowired
    private Services services;

    @Bean
    public TodoController todoController(){
        return new TodoController(services.todoService());
    }

}