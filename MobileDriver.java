package sk.qa.mobile;

import com.codeborne.selenide.Selenide;
import com.codeborne.selenide.WebDriverRunner;
import io.appium.java_client.HidesKeyboard;
import io.appium.java_client.InteractsWithApps;
import io.appium.java_client.PullsFiles;
import io.appium.java_client.PushesFiles;
import io.appium.java_client.SupportsRotation;
import io.appium.java_client.android.nativekey.AndroidKey;
import io.appium.java_client.android.nativekey.KeyEvent;
import io.appium.java_client.android.nativekey.PressesKey;
import io.appium.java_client.appmanagement.ApplicationState;
import org.openqa.selenium.Capabilities;
import org.openqa.selenium.HasCapabilities;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.ScreenOrientation;
import org.openqa.selenium.WebDriver;

import java.time.Duration;
import java.util.Locale;
import java.util.Map;

/**
 * Jediné miesto, kde sa Selenide WebDriver pretypúva na Appium rozhrania.
 *
 * Nahrádza {@code AppiumDriverRunner.getAndroidDriver()},
 * {@code getIosDriver()} a {@code getMobileDriver()}, ktoré sú
 * od selenide-appium 7.18.0 označené @Deprecated(forRemoval = true).
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
    // Správa aplikácie — funguje na Androide aj iOS
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

    public static void background(Duration duration) {
        apps().runAppInBackground(duration);
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
    // Session
    // ------------------------------------------------------------------

    /** Zatvorí session ak nejaká beží. Bezpečné volať kedykoľvek. */
    public static void closeSessionIfOpen() {
        if (isStarted()) {
            System.out.println("Closing Mobile Driver...");
            Selenide.closeWebDriver();
        } else {
            System.out.println("No active Mobile session to close.");
        }
    }
}
