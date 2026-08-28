package sk.smartBanking.utils;

import com.codeborne.selenide.Selenide;
import com.codeborne.selenide.WebDriverRunner;
import io.appium.java_client.HidesKeyboard;
import io.appium.java_client.InteractsWithApps;
import io.appium.java_client.PullsFiles;
import io.appium.java_client.PushesFiles;
import io.appium.java_client.android.AuthenticatesByFinger;
import io.appium.java_client.android.nativekey.AndroidKey;
import io.appium.java_client.android.nativekey.KeyEvent;
import io.appium.java_client.android.nativekey.PressesKey;
import io.appium.java_client.appmanagement.ApplicationState;
import io.appium.java_client.remote.SupportsRotation;
import org.openqa.selenium.Capabilities;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.HasCapabilities;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.ScreenOrientation;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.interactions.Interactive;
import org.openqa.selenium.interactions.Sequence;

import java.time.Duration;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;

/**
 * Jediné miesto, kde sa Selenide WebDriver pretypúva na Appium rozhrania.
 *
 * Nahrádza {@code AppiumDriverRunner.getAndroidDriver()},
 * {@code getIosDriver()} a {@code getMobileDriver()}, ktoré sú
 * od selenide-appium 7.18.0 označené @Deprecated(forRemoval = true).
 *
 * Metódy na správu appky majú dve podoby:
 * bezparametrovú, ktorá si ID testovanej appky vytiahne sama cez
 * {@link AppIds#current()} podľa bežiacej platformy, a s explicitným
 * appId pre prípady, keď pracuješ s inou appkou (napr. Nastavenia,
 * SMS, druhá banka pri prevode).
 *
 * Všetko je statické a bezpečné aj pri paralelnom behu — Selenide
 * drží driver v ThreadLocal, takže getWebDriver() vždy vráti driver
 * aktuálneho vlákna.
 */
public final class MobileDriver {

    private MobileDriver() {
    }

    // ------------------------------------------------------------------
    // Základ
    // ------------------------------------------------------------------

    /**
     * Surový Selenide WebDriver aktuálneho vlákna — teda to isté,
     * čo vráti {@code WebDriverRunner.getWebDriver()}. Pod ním je
     * reálne AndroidDriver alebo IOSDriver, ale cez typ WebDriver
     * sa k Appium metódam nedostaneš; na to slúžia gettery nižšie.
     *
     * Ak session ešte nebeží, Selenide ju na tomto mieste otvorí.
     */
    public static WebDriver currentWebDriver() {
        return WebDriverRunner.getWebDriver();
    }

    /** True ak je v tomto vlákne otvorená session. Nespustí novú. */
    public static boolean isStarted() {
        return WebDriverRunner.hasWebDriverStarted();
    }

    // ------------------------------------------------------------------
    // Rozhrania — použi keď potrebuješ metódu, ktorú tu nemám zabalenú
    // ------------------------------------------------------------------

    public static InteractsWithApps apps() {
        return (InteractsWithApps) currentWebDriver();
    }

    public static SupportsRotation rotation() {
        return (SupportsRotation) currentWebDriver();
    }

    public static HidesKeyboard keyboard() {
        return (HidesKeyboard) currentWebDriver();
    }

    public static PullsFiles pullsFiles() {
        return (PullsFiles) currentWebDriver();
    }

    public static PushesFiles pushesFiles() {
        return (PushesFiles) currentWebDriver();
    }

    /** Pozor: len Android. IOSDriver PressesKey neimplementuje. */
    public static PressesKey keys() {
        return (PressesKey) currentWebDriver();
    }

    public static JavascriptExecutor js() {
        return (JavascriptExecutor) currentWebDriver();
    }

    // ------------------------------------------------------------------
    // Platforma
    // ------------------------------------------------------------------

    public static Capabilities capabilities() {
        return ((HasCapabilities) currentWebDriver()).getCapabilities();
    }

    public static Object capability(String name) {
        return capabilities().getCapability(name);
    }

    /** Capability ako String, s fallbackom keď nie je nastavená. */
    public static String capability(String name, String fallback) {
        Object value = capability(name);
        return value != null ? value.toString() : fallback;
    }

    public static String platformName() {
        Capabilities caps = capabilities();
        Object name = caps.getCapability("platformName");
        if (name == null) {
            name = caps.getPlatformName();
        }
        return name == null ? "" : name.toString().toUpperCase(Locale.ROOT);
    }

    public static boolean isAndroid() {
        return platformName().contains("ANDROID");
    }

    public static boolean isIos() {
        String platform = platformName();
        return platform.contains("IOS") || platform.contains("IPHONE");
    }

    // ------------------------------------------------------------------
    // Testovaná appka — ID sa dotiahne samo podľa platformy
    // ------------------------------------------------------------------

    public static ApplicationState state() {
        return state(AppIds.current());
    }

    public static boolean isRunning() {
        return isRunning(AppIds.current());
    }

    public static boolean isInForeground() {
        return isInForeground(AppIds.current());
    }

    public static void activate() {
        activate(AppIds.current());
    }

    public static void terminate() {
        terminate(AppIds.current());
    }

    public static void relaunch() {
        relaunch(AppIds.current());
    }

    public static void openFresh() {
        openFresh(AppIds.current());
    }

    public static void background(Duration duration) {
        apps().runAppInBackground(duration);
    }

    public static boolean isInstalled() {
        return isInstalled(AppIds.current());
    }

    // ------------------------------------------------------------------
    // Ľubovoľná appka — keď potrebuješ inú než testovanú
    // ------------------------------------------------------------------

    public static ApplicationState state(String appId) {
        return apps().queryAppState(appId);
    }

    public static boolean isRunning(String appId) {
        return state(appId) != ApplicationState.NOT_RUNNING;
    }

    public static boolean isInForeground(String appId) {
        return state(appId) == ApplicationState.RUNNING_IN_FOREGROUND;
    }

    public static void activate(String appId) {
        apps().activateApp(appId);
    }

    /** Ukončí appku len ak beží. Výnimku ticho prehltne. */
    public static void terminate(String appId) {
        try {
            if (isRunning(appId)) {
                apps().terminateApp(appId);
            }
        } catch (RuntimeException e) {
            System.out.println("terminateApp(" + appId + ") zlyhal: " + e.getMessage());
        }
    }

    /** Ekvivalent SelenideAppium.relaunchApp() — terminate + activate. */
    public static void relaunch(String appId) {
        terminate(appId);
        activate(appId);
    }

    /** Ak appka beží, reštartuj ju; inak ju len spusti. */
    public static void openFresh(String appId) {
        if (isRunning(appId)) {
            relaunch(appId);
        } else {
            activate(appId);
        }
    }

    public static boolean isInstalled(String appId) {
        return apps().isAppInstalled(appId);
    }

    // ------------------------------------------------------------------
    // Zariadenie
    // ------------------------------------------------------------------

    public static void rotate(ScreenOrientation orientation) {
        rotation().rotate(orientation);
    }

    public static void rotateLandscape() {
        rotate(ScreenOrientation.LANDSCAPE);
    }

    public static void rotatePortrait() {
        rotate(ScreenOrientation.PORTRAIT);
    }

    public static ScreenOrientation orientation() {
        return rotation().getOrientation();
    }

    /** Nespadne, keď klávesnica nie je zobrazená. */
    public static void hideKeyboardIfShown() {
        try {
            keyboard().hideKeyboard();
        } catch (RuntimeException ignored) {
            // klávesnica nebola otvorená
        }
    }

    /** HOME tlačidlo. Na iOS cez mobile: pressButton. */
    public static void pressHome() {
        if (isAndroid()) {
            keys().pressKey(new KeyEvent(AndroidKey.HOME));
        } else {
            js().executeScript("mobile: pressButton", Map.of("name", "home"));
        }
    }

    /** BACK tlačidlo. iOS ho nemá — tam použi navigáciu v appke. */
    public static void pressBack() {
        if (isAndroid()) {
            keys().pressKey(new KeyEvent(AndroidKey.BACK));
        } else {
            throw new UnsupportedOperationException(
                    "iOS nemá hardvérové BACK tlačidlo.");
        }
    }

    public static byte[] pullFile(String remotePath) {
        return pullsFiles().pullFile(remotePath);
    }

    // ------------------------------------------------------------------
    // Rozmery obrazovky
    // ------------------------------------------------------------------

    /**
     * Rozmery viewportu. Pretypovanie netreba — getSize() je bežná
     * WebDriver metóda, funguje na mobile aj v browseri.
     *
     * Každé volanie je HTTP round trip na Appium server, takže pri
     * výpočte viacerých súradníc si výsledok ulož do premennej
     * namiesto opakovaného volania.
     */
    public static Dimension screenSize() {
        return currentWebDriver().manage().window().getSize();
    }

    public static int screenHeight() {
        return screenSize().getHeight();
    }

    public static int screenWidth() {
        return screenSize().getWidth();
    }

    // ------------------------------------------------------------------
    // Gestá
    // ------------------------------------------------------------------

    /** W3C Actions. Interactive je Selenium rozhranie, nie Appium. */
    public static void perform(Sequence... sequences) {
        ((Interactive) currentWebDriver()).perform(Arrays.asList(sequences));
    }

    // ------------------------------------------------------------------
    // Biometria
    // ------------------------------------------------------------------

    /**
     * Odtlačok prsta. Na Androide funguje len na emulátore,
     * fingerPrintId je 1 až 10 podľa Android Keystore.
     * Na iOS simulátore ide o Touch ID cez mobile: sendBiometricMatch.
     */
    public static void fingerprint(int fingerPrintId) {
        if (isAndroid()) {
            ((AuthenticatesByFinger) currentWebDriver()).fingerPrint(fingerPrintId);
        } else {
            js().executeScript("mobile: sendBiometricMatch",
                    Map.of("type", "touchId", "match", true));
        }
    }

    // ------------------------------------------------------------------
    // Session
    // ------------------------------------------------------------------

    /**
     * True ak je otvorená session mobilná (Appium), false ak ide
     * o browser. Volaj len keď isStarted() vráti true.
     */
    public static boolean isMobileSession() {
        return currentWebDriver() instanceof InteractsWithApps;
    }

    /**
     * Zatvorí session ak nejaká beží — mobilnú aj browserovú.
     *
     * Selenide drží na vlákno práve jeden driver, takže netreba
     * riešiť mobil a web zvlášť; keď si v Steps prepneš
     * Configuration.browser z IOSDriverProvider na Edge a naopak,
     * ide stále o to isté ThreadLocal miesto.
     */
    public static void closeSessionIfOpen() {
        if (!isStarted()) {
            System.out.println("No active session to close.");
            return;
        }
        String type = isMobileSession() ? "Mobile" : "Web";
        System.out.println("Closing " + type + " Driver...");
        Selenide.closeWebDriver();
    }
}
