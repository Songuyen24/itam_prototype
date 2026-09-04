package com.company.itam.config.i18n;

import com.company.itam.common.util.MessageHelper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.MessageSource;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;
import org.springframework.mock.web.MockHttpServletRequest;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;

class I18nConfigTest {

    private final I18nConfig i18nConfig = new I18nConfig();

    @Test
    @DisplayName("Should resolve messages correctly in Vietnamese and English")
    void testMessageResolutionBilingual() {
        MessageSource messageSource = i18nConfig.messageSource();
        MessageHelper messageHelper = new MessageHelper(messageSource);

        // Vietnamese tests (default)
        String viCatalogInUse = messageHelper.getMessage("CATALOG_IN_USE", Locale.of("vi"));
        assertEquals("Danh mục đang được sử dụng trong hệ thống, không thể xóa", viCatalogInUse);

        String viAssetTag = messageHelper.getMessage("DUPLICATE_ASSET_TAG", Locale.of("vi"));
        assertEquals("Mã tài sản đã tồn tại trong hệ thống", viAssetTag);

        // English tests
        String enCatalogInUse = messageHelper.getMessage("CATALOG_IN_USE", Locale.ENGLISH);
        assertEquals("Catalog item is currently in use in the system and cannot be deleted", enCatalogInUse);

        String enAssetTag = messageHelper.getMessage("DUPLICATE_ASSET_TAG", Locale.ENGLISH);
        assertEquals("Asset tag already exists in the system", enAssetTag);

        // Fallback test for unknown key
        String fallback = messageHelper.getMessageWithDefault("UNKNOWN_CODE_XYZ", "Default fallback message");
        assertEquals("Default fallback message", fallback);
    }

    @Test
    @DisplayName("CustomLocaleResolver should handle Accept-Language header and lang parameter")
    void testLocaleResolver() {
        I18nConfig.CustomLocaleResolver resolver = (I18nConfig.CustomLocaleResolver) i18nConfig.localeResolver();

        // Default when no header or param
        MockHttpServletRequest request1 = new MockHttpServletRequest();
        assertEquals(Locale.of("vi"), resolver.resolveLocale(request1));

        // Accept-Language header en
        MockHttpServletRequest request2 = new MockHttpServletRequest();
        request2.addHeader("Accept-Language", "en-US,en;q=0.9");
        assertEquals(Locale.ENGLISH, resolver.resolveLocale(request2));

        // Accept-Language header vi
        MockHttpServletRequest request3 = new MockHttpServletRequest();
        request3.addHeader("Accept-Language", "vi-VN,vi;q=0.9");
        assertEquals(Locale.of("vi"), resolver.resolveLocale(request3));

        // Query param lang=en overrides header
        MockHttpServletRequest request4 = new MockHttpServletRequest();
        request4.addHeader("Accept-Language", "vi-VN,vi;q=0.9");
        request4.setParameter("lang", "en");
        assertEquals(Locale.ENGLISH, resolver.resolveLocale(request4));
    }
}
