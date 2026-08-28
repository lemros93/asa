package sk.qa.mobile;

/**
 * Identifikátory aplikácie. Na Androide je to package name,
 * na iOS bundle ID — často sa líšia, preto to nemá byť hardcode
 * v step definíciách.
 *
 * Hodnoty prichádzajú zo system properties (napr. -DappId.android=...),
 * takže sa dajú prepínať medzi test/prod buildom appky priamo
 * z Bamboo plan variables bez rekompilácie.
 */
public final class AppIds {

    private static final String DEFAULT_ANDROID = "com.csobsk.smartbankingv2";
    private static final String DEFAULT_IOS = "com.csobsk.smartbanking";

    private AppIds() {
    }

    /** SmartBanking pre aktuálne bežiacu platformu. */
    public static String smartBanking() {
        return MobileDriver.isIos() ? iosSmartBanking() : androidSmartBanking();
    }

    public static String androidSmartBanking() {
        return resolve("appId.android", "APP_ID_ANDROID", DEFAULT_ANDROID);
    }

    public static String iosSmartBanking() {
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
