package com.company.itam.config.i18n;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;
import org.springframework.web.servlet.i18n.LocaleChangeInterceptor;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

@Configuration
public class I18nConfig implements WebMvcConfigurer {

    public static final Locale DEFAULT_LOCALE = Locale.of("vi");
    public static final List<Locale> SUPPORTED_LOCALES = Arrays.asList(
            DEFAULT_LOCALE,
            Locale.ENGLISH,
            Locale.of("en")
    );

    @Bean
    public LocaleResolver localeResolver() {
        CustomLocaleResolver resolver = new CustomLocaleResolver();
        resolver.setDefaultLocale(DEFAULT_LOCALE);
        resolver.setSupportedLocales(SUPPORTED_LOCALES);
        return resolver;
    }

    @Bean
    public LocaleChangeInterceptor localeChangeInterceptor() {
        LocaleChangeInterceptor interceptor = new LocaleChangeInterceptor();
        interceptor.setParamName("lang");
        return interceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(localeChangeInterceptor());
    }

    @Bean
    public MessageSource messageSource() {
        ReloadableResourceBundleMessageSource messageSource = new ReloadableResourceBundleMessageSource();
        messageSource.setBasename("classpath:messages");
        messageSource.setDefaultEncoding(StandardCharsets.UTF_8.name());
        messageSource.setDefaultLocale(DEFAULT_LOCALE);
        messageSource.setFallbackToSystemLocale(false);
        return messageSource;
    }

    public static class CustomLocaleResolver extends AcceptHeaderLocaleResolver {
        private static final String LOCALE_REQUEST_ATTRIBUTE_NAME = CustomLocaleResolver.class.getName() + ".LOCALE";

        @Override
        public Locale resolveLocale(HttpServletRequest request) {
            Locale customLocale = (Locale) request.getAttribute(LOCALE_REQUEST_ATTRIBUTE_NAME);
            if (customLocale != null) {
                return customLocale;
            }

            String langParam = request.getParameter("lang");
            if (langParam != null && !langParam.isBlank()) {
                Locale resolved = Locale.forLanguageTag(langParam.trim());
                if (resolved.getLanguage().equalsIgnoreCase("en")) {
                    return Locale.ENGLISH;
                }
                return DEFAULT_LOCALE;
            }

            String acceptHeader = request.getHeader("Accept-Language");
            if (acceptHeader != null && !acceptHeader.isBlank()) {
                Locale resolved = super.resolveLocale(request);
                if (resolved != null && resolved.getLanguage().equalsIgnoreCase("en")) {
                    return Locale.ENGLISH;
                }
            }

            return DEFAULT_LOCALE;
        }

        @Override
        public void setLocale(HttpServletRequest request, HttpServletResponse response, Locale locale) {
            if (request != null) {
                request.setAttribute(LOCALE_REQUEST_ATTRIBUTE_NAME, locale);
            }
        }
    }
}
