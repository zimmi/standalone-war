package com.rmnsc.web;

import com.rmnsc.config.*;
import com.rmnsc.session.SmartLocaleResolver;
import java.nio.charset.StandardCharsets;
import java.util.List;
import javax.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;
import org.springframework.core.Ordered;
import org.springframework.core.env.Environment;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.ViewResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurationSupport;
import org.springframework.web.servlet.handler.BeanNameUrlHandlerMapping;
import org.springframework.web.servlet.i18n.LocaleChangeInterceptor;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.thymeleaf.spring4.SpringTemplateEngine;
import org.thymeleaf.spring4.messageresolver.SpringNonCacheableMessageResolver;
import org.thymeleaf.spring4.view.ThymeleafViewResolver;
import org.thymeleaf.templateresolver.ServletContextTemplateResolver;

/**
 *
 * @author Thomas
 */
@Configuration
public class WebConfig extends WebMvcConfigurationSupport {

    @Autowired
    private Environment environment;

    @Override
    public BeanNameUrlHandlerMapping beanNameHandlerMapping() {
        // TODO: Disable registering BeanNameUrlHandlerMapping somehow instead of this mess.
        BeanNameUrlHandlerMapping mapping = new BeanNameUrlHandlerMapping() {
            @Override
            protected Object getHandlerInternal(HttpServletRequest request) throws Exception {
                return null;
            }
        };
        mapping.setOrder(Ordered.LOWEST_PRECEDENCE);
        return mapping;
    }

    @Override
    public RequestMappingHandlerMapping requestMappingHandlerMapping() {
        RequestMappingHandlerMapping mapping = super.requestMappingHandlerMapping();
        mapping.setUseSuffixPatternMatch(false);
        mapping.setUseTrailingSlashMatch(true);
        return mapping;
    }

    @Override
    public RequestMappingHandlerAdapter requestMappingHandlerAdapter() {
        RequestMappingHandlerAdapter requestMappingHandlerAdapter = super.requestMappingHandlerAdapter();
        requestMappingHandlerAdapter.setSynchronizeOnSession(true);
        requestMappingHandlerAdapter.setIgnoreDefaultModelOnRedirect(true);
        return requestMappingHandlerAdapter;
    }

    private String getResourceRootPattern() {
        // TODO: Remove this property and this pattern when this ticket is done:
        // https://jira.springsource.org/browse/SPR-10310
        return environment.getRequiredProperty("resourceroot") + "**";
    }

    @Bean
    public MessageSource messageSource() {
        ReloadableResourceBundleMessageSource messageSource = new ReloadableResourceBundleMessageSource();
        messageSource.setBasename("/WEB-INF/messages/messages");
        // All the message-files MUST be UTF-8 encoded.
        messageSource.setDefaultEncoding(StandardCharsets.UTF_8.name());
        messageSource.setFallbackToSystemLocale(false);

        if (environment.acceptsProfiles(Profile.DEV.getPropertyName())) {
            messageSource.setCacheSeconds(0);
            messageSource.setUseCodeAsDefaultMessage(true);
        }

        return messageSource;
    }

    @Override
    public void configureMessageConverters(List<HttpMessageConverter<?>> converters) {
        // No need for the default ones, just Jackson is fine.
        converters.add(new MappingJackson2HttpMessageConverter());
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        ResourceHandlerRegistration resourceHandlerRegistration = registry.addResourceHandler(this.getResourceRootPattern());
        resourceHandlerRegistration.addResourceLocations(environment.getRequiredProperty("resourceroot.internal"));
        // cache for about one year
        resourceHandlerRegistration.setCachePeriod(31556926);
    }

    @Bean
    public LocaleResolver localeResolver() {
        return new SmartLocaleResolver();
    }

    @Override
    protected void addInterceptors(InterceptorRegistry registry) {
        // Will start a new session for the user if a special query parameter is present.
        LocaleChangeInterceptor lci = new LocaleChangeInterceptor();
        lci.setParamName(StandardRequestParameter.CHANGE_LOCALE_TAG.getParameterName());
        InterceptorRegistration localeRegistration = registry.addInterceptor(lci);
        // Resources don't need locales.
        localeRegistration.excludePathPatterns(this.getResourceRootPattern());
        //TODO: this will check EVERY request for this parameter. url could be forged by other users. mhm.
    }

    @Bean
    public ViewResolver viewResolver() {
        ServletContextTemplateResolver templateResolver = new ServletContextTemplateResolver();
        templateResolver.setPrefix("/WEB-INF/templates/");
        templateResolver.setSuffix(".html");
        templateResolver.setTemplateMode("HTML5");

        SpringNonCacheableMessageResolver messageResolver = new SpringNonCacheableMessageResolver();
        messageResolver.setMessageSource(this.messageSource());

        SpringTemplateEngine templateEngine = new SpringTemplateEngine();
        templateEngine.setTemplateResolver(templateResolver);
        templateEngine.setMessageResolver(messageResolver);

        ThymeleafViewResolver viewResolver = new ThymeleafViewResolver();
        viewResolver.setContentType("text/html");
        viewResolver.setCharacterEncoding(StandardCharsets.UTF_8.name());
        viewResolver.setRedirectContextRelative(true);
        // 303 on redirect, as of the HTTP 1.1 spec
        viewResolver.setRedirectHttp10Compatible(false);

        if (environment.acceptsProfiles(Profile.DEV.getPropertyName())) {
            // Disable all caching of templates. Pretty slow.
            // Allows for editing templates inside IDE WITHOUT reload of application.
            templateEngine.setCacheManager(null);
            viewResolver.setCache(false);
        }

        viewResolver.setTemplateEngine(templateEngine);

        // TODO start daemon thread/use some threadpool to initialize thymeleaf-engine. find out how. fake-render? init-method?
        return viewResolver;
    }
}
