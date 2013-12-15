
package com.rmnsc.persistence;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 *
 * @author thomas
 */
@Configuration
@Import(PersistenceConfig.class)
public class Daos {

    @Autowired
    private PersistenceConfig config;

    @Bean
    public TodoDao todoDao(){
        return new TodoDaoJdbc(config.namedParameterJdbcOperations());
    }

}