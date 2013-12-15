package com.rmnsc.session;

import com.rmnsc.web.StandardRequestParameter;
import com.rmnsc.web.StandardSessionAttribute;
import java.util.Locale;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.util.WebUtils;

/**
 *
 * @author thomas
 */
public class SmartLocaleResolver implements LocaleResolver {

    @Override
    public Locale resolveLocale(HttpServletRequest request) {
        // First check if the locale is overridden for this request.
        String forcedLocale = request.getParameter(StandardRequestParameter.FORCED_LOCALE_TAG.getParameterName());
        if(forcedLocale != null){
            return Locale.forLanguageTag(forcedLocale);
        }
        // Then check if the session contains a locale.
        Locale sessionLocale = (Locale) WebUtils.getSessionAttribute(request, StandardSessionAttribute.LOCALE.getAttributeName());
        if (sessionLocale != null) {
            return sessionLocale;
        }
        // Then look at the Accept-Language header.
        if(request.getHeader("Accept-Language") != null){
            return request.getLocale();
        }
        return Locale.ROOT;
    }

    @Override
    public void setLocale(HttpServletRequest request, HttpServletResponse response, Locale locale) {
        // Create a new session for the user.
        WebUtils.setSessionAttribute(request, StandardSessionAttribute.LOCALE.getAttributeName(), locale);
    }
}