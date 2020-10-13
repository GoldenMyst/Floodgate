/*
 * Copyright (c) 2019-2020 GeyserMC. http://geysermc.org
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 *
 * @author GeyserMC
 * @link https://github.com/GeyserMC/Floodgate
 */

package org.geysermc.floodgate.util;

import com.google.inject.Inject;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.geysermc.floodgate.api.logger.FloodgateLogger;
import org.geysermc.floodgate.config.FloodgateConfig;

/**
 * Manages translations for strings in Floodgate
 */
@RequiredArgsConstructor
public final class LanguageManager {
    private final Map<String, Properties> LOCALE_MAPPINGS = new HashMap<>();

    private final FloodgateLogger logger;

    /**
     * The locale used in console and as a fallback
     */
    @Getter private String defaultLocale;

    /**
     * Cleans up and formats a locale string
     *
     * @param locale the locale to format
     * @return the formatted locale
     */
    private static String formatLocale(String locale) {
        try {
            String[] parts = locale.toLowerCase().split("_");
            return parts[0] + "_" + parts[1].toUpperCase();
        } catch (Exception e) {
            return locale;
        }
    }

    /**
     * Loads the log's locale file once Floodgate loads the config
     *
     * @param config the Floodgate config
     */
    @Inject
    public void init(FloodgateConfig config) {
        loadLocale("en_US"); // Fallback

        defaultLocale = formatLocale(config.getDefaultLocale());

        if (isValidLanguage(defaultLocale)) {
            loadLocale(defaultLocale);
            return;
        }

        String systemLocale = formatLocale(
                Locale.getDefault().getLanguage() + "_" + Locale.getDefault().getCountry()
        );

        if (isValidLanguage(systemLocale)) {
            loadLocale(systemLocale);
            defaultLocale = systemLocale;
            return;
        }

        defaultLocale = "en_US";
    }

    /**
     * Loads a Floodgate locale from resources; if the file doesn't exist it just logs a warning
     *
     * @param locale locale to load
     */
    public void loadLocale(String locale) {
        locale = formatLocale(locale);

        // just return if the locale has been loaded already
        if (LOCALE_MAPPINGS.containsKey(locale)) {
            return;
        }

        InputStream localeStream = LanguageManager.class.getClassLoader().getResourceAsStream(
                "languages/texts/" + locale + ".properties");

        // Load the locale
        if (localeStream != null) {
            Properties localeProp = new Properties();
            try {
                localeProp.load(new InputStreamReader(localeStream, StandardCharsets.UTF_8));
            } catch (Exception e) {
                throw new AssertionError("Failed to load Floodgate locale", e);
            }

            // Insert the locale into the mappings
            LOCALE_MAPPINGS.put(locale, localeProp);
        } else {
            logger.warn("Missing locale file: " + locale);
        }
    }

    /**
     * Get a formatted language string with the default locale for Floodgate
     *
     * @param key    language string to translate
     * @param values values to put into the string
     * @return translated string or the original message if it was not found in the given locale
     */
    public String getLogString(String key, Object... values) {
        return getString(key, defaultLocale, values);
    }

    /**
     * Get a formatted language string with the given locale for Floodgate
     *
     * @param key    language string to translate
     * @param locale locale to translate to
     * @param values values to put into the string
     * @return translated string or the original message if it was not found in the given locale
     */
    public String getString(String key, String locale, Object... values) {
        locale = formatLocale(locale);

        Properties properties = LOCALE_MAPPINGS.get(locale);
        String formatString = properties.getProperty(key);

        // Try and get the key from the default locale
        if (formatString == null) {
            properties = LOCALE_MAPPINGS.get(defaultLocale);
            formatString = properties.getProperty(key);
        }

        // Try and get the key from en_US (this should only ever happen in development)
        if (formatString == null) {
            properties = LOCALE_MAPPINGS.get("en_US");
            formatString = properties.getProperty(key);
        }

        // Final fallback
        if (formatString == null) {
            formatString = key;
        }

        return MessageFormat.format(formatString.replace("'", "''").replace("&", "\u00a7"), values);
    }

    /**
     * Ensures that the given locale is supported by Floodgate
     *
     * @param locale the locale to validate
     * @return true if the given locale is supported by Floodgate
     */
    private boolean isValidLanguage(String locale) {
        if (locale == null) {
            return false;
        }

        URL languageFile = LanguageManager.class
                .getResource("/languages/texts/" + locale + ".properties");

        if (languageFile == null) {
            logger.warn(locale + " is not a supported Floodgate language.");
            return false;
        }
        return true;
    }
}