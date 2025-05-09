package org.example;

import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import io.github.bonigarcia.wdm.WebDriverManager;

import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class LoginTest {
    public static void main(String[] args) {
        String username = System.getenv("USERNAME");
        String password = System.getenv("PASSWORD");

        if (username == null || password == null) {
            System.out.println("❌ Las variables de entorno USERNAME o PASSWORD no están definidas.");
            return;
        }

        // Configuración de ChromeDriver
        WebDriverManager.chromedriver().setup();
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--no-sandbox", "--disable-dev-shm-usage", "--headless=new");

        WebDriver driver = new ChromeDriver(options);

        try {
            // 1. Iniciar sesión
            driver.get("https://clients.geovictoria.com/account/login");
            driver.manage().window().maximize();
            driver.manage().timeouts().implicitlyWait(10, TimeUnit.SECONDS);

            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
            WebElement usernameField = wait.until(ExpectedConditions.presenceOfElementLocated(
                By.xpath("/html/body/div[1]/div/main/section/div/div[2]/div/form/div[1]/input")));
            WebElement passwordField = wait.until(ExpectedConditions.presenceOfElementLocated(
                By.xpath("/html/body/div[1]/div/main/section/div/div[2]/div/form/div[2]/input[1]")));
            WebElement loginButton = wait.until(ExpectedConditions.presenceOfElementLocated(
                By.xpath("/html/body/div[1]/div/main/section/div/div[2]/div/form/div[3]/button")));

            usernameField.sendKeys(username);
            passwordField.sendKeys(password);
            loginButton.click();

            // Esperar a redirección
            wait.until(ExpectedConditions.urlContains("clients.geovictoria.com"));
            Thread.sleep(20000); // espera carga completa

            // 2. Buscar iframe con el widget
            JavascriptExecutor js = (JavascriptExecutor) driver;
            List<WebElement> iframes = driver.findElements(By.tagName("iframe"));
            System.out.println("🔎 Iframes encontrados: " + iframes.size());
            boolean switched = false;
            for (WebElement iframe : iframes) {
                driver.switchTo().frame(iframe);
                Boolean widgetExiste = (Boolean) js.executeScript(
                    "return document.querySelector('web-punch-widget') !== null;");
                if (widgetExiste) {
                    System.out.println("✅ <web-punch-widget> encontrado dentro de un iframe.");
                    switched = true;
                    break;
                }
                driver.switchTo().defaultContent();
            }
            if (!switched) {
                System.out.println("❌ No se encontró <web-punch-widget> en ningún iframe.");
                return;
            }

            // Paso 1: clic en "Más" solo si está colapsado (">")
            String clickMasScript = """
                const widget = document.querySelector('web-punch-widget');
                if (!widget || !widget.shadowRoot) return '❌ No widget';
                const toggle = widget.shadowRoot.querySelector('.expand-collapse-toggle');
                if (!toggle) return '❌ Toggle no encontrado';
                const texto = toggle.textContent.trim();
                if (texto === '>') {
                  toggle.click();
                  return '✅ Widget expandido (" > " → " < ")';
                } else if (texto === '<') {
                  return 'ℹ️ Ya estaba expandido ("<")';
                } else {
                  return '⚠️ Estado inesperado: ' + texto;
                }
            """;
            Object resMas = js.executeScript(clickMasScript);
            System.out.println(resMas);

            // Esperar a que aparezca el detalle del widget
            wait.withTimeout(Duration.ofSeconds(5))
                .until(d -> js.executeScript("return document.querySelector('web-punch-details') !== null;"));

            // Paso 2: clic en "Marcar Entrada"
            String scriptModal = """
                const callback = arguments[arguments.length - 1];
                let intentos = 0;
                const maxIntentos = 10;

                const intervalo = setInterval(() => {
                    try {
                        const detalles = document.querySelector('web-punch-details');
                        if (!detalles || !detalles.shadowRoot) return;

                        const modal = detalles.shadowRoot.querySelector('web-punch-modal');
                        if (!modal || !modal.shadowRoot) return;

                        const botonEntrada = Array.from(modal.shadowRoot.querySelectorAll('.button-entry')).find(b => {
                            const texto = b.textContent.trim();
                            return texto.includes("Marcar Entrada");
                        });

                        if (!botonEntrada) {
                            clearInterval(intervalo);
                            return callback('❌ Botón "Marcar Entrada" no encontrado en modal');
                        }

                        ['pointerdown', 'mousedown', 'mouseup', 'click'].forEach(evt => {
                            const e = new MouseEvent(evt, { bubbles: true, cancelable: true, view: window });
                            botonEntrada.dispatchEvent(e);
                        });

                        setTimeout(() => {
                            const botones = Array.from(modal.shadowRoot.querySelectorAll('.button'));
                            const textos = botones.map(b => b.textContent.trim());
                            if (textos.includes("Marcar Salida") && !textos.includes("Marcar Entrada")) {
                                callback('✅ Entrada marcada correctamente (cambió a "Marcar Salida")');
                            } else {
                                callback('⚠️ Click ejecutado pero no hubo cambio. Botones ahora: [' + textos.join(', ') + ']');
                            }
                        }, 2000);

                        clearInterval(intervalo);
                    } catch (err) {
                        clearInterval(intervalo);
                        callback('❌ Error en ejecución JS: ' + err.message);
                    }

                    intentos++;
                    if (intentos >= maxIntentos) {
                        clearInterval(intervalo);
                        callback('❌ Timeout: modal no apareció luego de varios intentos.');
                    }
                }, 1000);
            """;
            Object resultado = js.executeAsyncScript(scriptModal);
            System.out.println(resultado);

            // 3. Notificación por WhatsApp
            try {
                String message = resultado.toString();
                String encoded = URLEncoder.encode(message, StandardCharsets.UTF_8);
                String url = "https://api.callmebot.com/whatsapp.php?phone=56990703632&text=" + encoded + "&apikey=1774229";

                HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
                con.setRequestMethod("GET");
                System.out.println("📩 WhatsApp enviado. Código: " + con.getResponseCode());
            } catch (Exception e) {
                System.out.println("❌ Error al enviar WhatsApp:");
                e.printStackTrace();
            }

            Thread.sleep(15000);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            driver.quit();
        }
    }
}
