package sk.smartBanking.utils;

/**
 * Identifikátory testovanej aplikácie. Na Androide je to package name,
 * na iOS bundle ID — často sa líšia, preto to nemá byť hardcode
 * v step definíciách.
 *
 * Hodnoty prichádzajú zo system properties (napr. -DappId.android=...)
 * alebo z env premenných, takže sa dajú prepínať medzi test/prod buildom
 * appky priamo z Bamboo plan variables bez rekompilácie.
 */
public final class AppIds {

    private static final String DEFAULT_ANDROID = "com.csobsk.smartbankingv2";
    private static final String DEFAULT_IOS = "com.csobsk.smartbanking";

    private AppIds() {
    }

    /**
     * ID testovanej appky pre platformu, na ktorej práve beží session.
     * Toto volá MobileDriver vo svojich bezparametrových metódach.
     */
    public static String current() {
        return MobileDriver.isIos() ? ios() : android();
    }

    /** Android package name. */
    public static String android() {
        return resolve("appId.android", "APP_ID_ANDROID", DEFAULT_ANDROID);
    }

    /** iOS bundle ID. */
    public static String ios() {
        return resolve("appId.ios", "APP_ID_IOS", DEFAULT_IOS);
    }

    /**
     * System property má prednosť pred env premennou, env pred defaultom.
     * Rovnaké poradie ako v zvyšku frameworku.
     */
    private static String resolve(String property, String env, String fallback) {
        String value = System.getProperty(property);
        if (isBlank(value)) {
            value = System.getenv(env);
        }
        return isBlank(value) ? fallback : value.trim();
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
