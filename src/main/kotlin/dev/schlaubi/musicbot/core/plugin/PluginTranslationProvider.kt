package dev.schlaubi.musicbot.core.plugin

import com.kotlindiscord.kord.extensions.i18n.DEFAULT_BUNDLE_SUFFIX
import com.kotlindiscord.kord.extensions.i18n.KORDEX_KEY
import com.kotlindiscord.kord.extensions.i18n.TranslationsProvider
import mu.KLogger
import mu.KotlinLogging
import java.text.MessageFormat
import java.util.*

private val LOG = KotlinLogging.logger { }

/**
 * Implementation of [TranslationsProvider] handling different plugin class loaders.
 */
class PluginTranslationProvider(defaultLocaleBuilder: () -> Locale) :
    OpenResourceBundleTranslations(defaultLocaleBuilder) {
    override fun getResourceBundle(bundle: String, locale: Locale, control: ResourceBundle.Control): ResourceBundle {
        val plugin = PluginLoader.getPluginForBundle(bundle)
        val classLoader =
            plugin?.pluginClassLoader ?: ClassLoader.getSystemClassLoader()
        LOG.debug { "Found classloader for $bundle to be $classLoader (${plugin?.pluginId ?: "<root>"})" }

        return ResourceBundle.getBundle(bundle, locale, classLoader, control)
    }
}

/**
 * Translation provider backed by Java's [ResourceBundle]s. This makes use of `.properties` files that are standard
 * across the Java ecosystem.
 *
 * Bundles are resolved as follows:
 *
 * * If `bundleName` is `null`, default to `kordex`
 * * Prefix the bundle name with `translations.`
 * * If `bundleName` doesn't contain a `.` character, suffix it with `.strings`
 *
 * With a `bundleName` of `null`, this means the bundle will be named `translations.kordex.strings`, which will resolve
 * to `translations/kordex/strings${_locale ?: ""}.properties` in the resources.
 */
// See: https://github.com/Kord-Extensions/kord-extensions/pull/106
open class OpenResourceBundleTranslations(
    defaultLocaleBuilder: () -> Locale
) : TranslationsProvider(defaultLocaleBuilder) {
    private val logger: KLogger = KotlinLogging.logger(
        "com.kotlindiscord.kord.extensions.i18n.ResourceBundleTranslations"
    )

    private val bundles: MutableMap<Pair<String, Locale>, ResourceBundle> = mutableMapOf()
    private val overrideBundles: MutableMap<Pair<String, Locale>, ResourceBundle> = mutableMapOf()

    override fun hasKey(key: String, locale: Locale, bundleName: String?): Boolean {
        return try {
            val (bundle, _) = getBundles(locale, bundleName)

            // Overrides aren't for adding keys, so we don't check them
            bundle.keys.toList().contains(key)
        } catch (e: MissingResourceException) {
            logger.trace { "Failed to get bundle $bundleName for locale $locale" }

            false
        }
    }

    /**
     * Loads the [ResourceBundle] called [bundle] for [locale].
     *
     * @see ResourceBundle.getBundle
     */
    protected open fun getResourceBundle(
        bundle: String,
        locale: Locale,
        control: ResourceBundle.Control
    ): ResourceBundle =
        ResourceBundle.getBundle(bundle, locale, Control)

    /**
     * Retrieves a pair of the [ResourceBundle] and the overide resource bundle for [bundleName] in locale.
     */
    @Throws(MissingResourceException::class)
    protected open fun getBundles(locale: Locale, bundleName: String?): Pair<ResourceBundle, ResourceBundle?> {
        var bundle = "translations." + (bundleName ?: KORDEX_KEY)

        if (bundle.count { it == '.' } < 2) {
            bundle += ".$DEFAULT_BUNDLE_SUFFIX"
        }

        val bundleKey = bundle to locale

        if (bundles[bundleKey] == null) {
            logger.trace { "Getting bundle $bundle for locale $locale" }
            bundles[bundleKey] = getResourceBundle(bundle, locale, Control)

            try {
                val overrideBundle = bundle + "_override"

                logger.trace { "Getting override bundle $overrideBundle for locale $locale" }

                overrideBundles[bundleKey] = getResourceBundle(overrideBundle, locale, Control)
            } catch (e: MissingResourceException) {
                logger.trace { "No override bundle found." }
            }
        }

        return bundles[bundleKey]!! to overrideBundles[bundleKey]
    }

    @Throws(MissingResourceException::class)
    override fun get(key: String, locale: Locale, bundleName: String?): String {
        val (bundle, overrideBundle) = getBundles(locale, bundleName)
        val result = overrideBundle?.getStringOrNull(key) ?: bundle.getString(key)

        logger.trace { "Result: $key -> $result" }

        return result
    }

    override fun translate(key: String, locale: Locale, bundleName: String?, replacements: Array<Any?>): String {
        var string = try {
            get(key, locale, bundleName)
        } catch (e: MissingResourceException) {
            key
        }

        return try {
            if (string == key && bundleName != null) {
                // Fall through to the default bundle if the key isn't found
                logger.trace { "'$key' not found in bundle '$bundleName' - falling through to '$KORDEX_KEY'" }

                string = get(key, locale, KORDEX_KEY)
            }

            val formatter = MessageFormat(string, locale)

            formatter.format(replacements)
        } catch (e: MissingResourceException) {
            logger.trace {
                if (bundleName == null) {
                    "Unable to find translation for key '$key' in bundle '$KORDEX_KEY'"
                } else {
                    "Unable to find translation for key '$key' in bundles: '$bundleName', '$KORDEX_KEY'"
                }
            }

            key
        }
    }

    private fun ResourceBundle.getStringOrNull(key: String): String? {
        return try {
            getString(key)
        } catch (e: MissingResourceException) {
            null
        }
    }

    private object Control : ResourceBundle.Control() {
        override fun getFormats(baseName: String?): MutableList<String> {
            if (baseName == null) {
                throw NullPointerException()
            }

            return FORMAT_PROPERTIES
        }
    }
}
