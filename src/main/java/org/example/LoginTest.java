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

import java.util.concurrent.TimeUnit;
import java.time.Duration;

public class LoginTest {
    public static void main(String[] args) {

        String username = System.getenv("USERNAME");
        String password = System.getenv("PASSWORD");

        if (username == null || password == null) {
            System.out.println("❌ Las variables de entorno USERNAME o PASSWORD no están definidas.");
            return;
        }

        WebDriverManager.chromedriver().setup();

        ChromeOptions options = new ChromeOptions();
        options.addArguments("--no-sandbox", "--disable-dev-shm-usage", "--headless=new"); // ✅ GitHub Actions requiere headless

        WebDriver driver = new ChromeDriver(options);

        try {
            driver.get("https://clients.geovictoria.com/account/login");
            driver.manage().window().maximize();
            driver.manage().timeouts().implicitlyWait(10, TimeUnit.SECONDS);

            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
            WebElement usernameField = wait.until(ExpectedConditions.presenceOfElementLocated(By.xpath("/html/body/div[1]/div/main/section/div/div[2]/div/form/div[1]/input")));
            WebElement passwordField = wait.until(ExpectedConditions.presenceOfElementLocated(By.xpath("/html/body/div[1]/div/main/section/div/div[2]/div/form/div[2]/input[1]")));
            WebElement loginButton = wait.until(ExpectedConditions.presenceOfElementLocated(By.xpath("/html/body/div[1]/div/main/section/div/div[2]/div/form/div[3]/button")));

            usernameField.sendKeys(username);
            passwordField.sendKeys(password);
            loginButton.click();

            wait.until(ExpectedConditions.urlContains("clients.geovictoria.com"));
            Thread.sleep(20000); // esperar carga completa

            JavascriptExecutor js = (JavascriptExecutor) driver;

            // Paso 1: clic en botón "Más"
            String clickMasScript = """
                const widget = document.querySelector('web-punch-widget');
                if (!widget || !widget.shadowRoot) return '❌ No se encontró <web-punch-widget>';
                const botonMas = widget.shadowRoot.querySelector('.btn-details');
                if (!botonMas) return '❌ Botón "Más" no encontrado';
                botonMas.click();
                return '✅ Botón "Más" clickeado';
            """;
            Object resultadoMas = js.executeScript(clickMasScript);
            System.out.println(resultadoMas);

            // Paso 2: esperar modal y hacer clic en "Marcar Entrada"
            String scriptModal = """
                const callback = arguments[arguments.length - 1];
                let intentos = 0;
                const maxIntentos = 12;

                const intervalo = setInterval(() => {
                    try {
                        const detalles = document.querySelector('web-punch-details');
                        if (!detalles || !detalles.shadowRoot) return;

                        const modal = detalles.shadowRoot.querySelector('web-punch-modal');
                        if (!modal || !modal.shadowRoot) return;

                        const botonEntrada = modal.shadowRoot.querySelector('.button-entry');
                        if (!botonEntrada) {
                            clearInterval(intervalo);
                            return callback('❌ Botón "Marcar Entrada" no encontrado en el modal');
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
                        callback('❌ Error ejecutando JS: ' + err.message);
                    }

                    intentos++;
                    if (intentos >= maxIntentos) {
                        clearInterval(intervalo);
                        callback('❌ Timeout: modal no apareció a tiempo.');
                    }
                }, 1000);
            """;

            Object resultado = js.executeAsyncScript(scriptModal);
            System.out.println(resultado);

            // Enviar WhatsApp con resultado
            try {
                String message = resultado.toString();
                String encoded = java.net.URLEncoder.encode(message, java.nio.charset.StandardCharsets.UTF_8);
                String url = "https://api.callmebot.com/whatsapp.php?phone=56990703632&text=" + encoded + "&apikey=1774229";

                java.net.URL obj = new java.net.URL(url);
                java.net.HttpURLConnection con = (java.net.HttpURLConnection) obj.openConnection();
                con.setRequestMethod("GET");
                System.out.println("📩 WhatsApp enviado. Código: " + con.getResponseCode());
            } catch (Exception e) {
                System.out.println("❌ Error al enviar WhatsApp:");
                e.printStackTrace();
            }

            Thread.sleep(8000);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            driver.quit();
        }
    }
}
