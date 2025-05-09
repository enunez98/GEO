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

import java.util.List;
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
        options.addArguments("--no-sandbox", "--disable-dev-shm-usage");
        // OJO: sin headless para pruebas reales
        // options.addArguments("--headless=new");

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
            Thread.sleep(20000); // Esperar carga total

            JavascriptExecutor js = (JavascriptExecutor) driver;

            List<WebElement> iframes = driver.findElements(By.tagName("iframe"));
            System.out.println("🔎 Iframes encontrados: " + iframes.size());

            boolean switched = false;

            for (WebElement iframe : iframes) {
                driver.switchTo().frame(iframe);
                Boolean widgetExiste = (Boolean) js.executeScript("return document.querySelector('web-punch-widget') !== null;");
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

            String script = """
                const callback = arguments[arguments.length - 1];
                try {
                    const widget = document.querySelector('web-punch-widget');
                    if (!widget || !widget.shadowRoot) return callback('❌ No widget');

                    const content = widget.shadowRoot.querySelector('web-punch-content');
                    if (!content || !content.shadowRoot) return callback('❌ No content');

                    const botones = Array.from(content.shadowRoot.querySelectorAll('.btn-entry'));
                    const botonEntrada = botones.find(b => {
                        const texto = b.querySelector('.btn-text');
                        return texto && texto.innerText.trim() === 'Marcar Entrada';
                    });

                    if (!botonEntrada) {
                        const visibles = botones.map(b => b.innerText.trim());
                        return callback('❌ Botón "Marcar Entrada" no encontrado. Botones: [' + visibles.join(', ') + ']');
                    }

                    botonEntrada.scrollIntoView({behavior: 'smooth', block: 'center'});

                    // Simular secuencia de eventos como humano
                    ['pointerdown', 'mousedown', 'mouseup', 'click'].forEach(type => {
                        const event = new MouseEvent(type, {
                            bubbles: true,
                            cancelable: true,
                            view: window
                        });
                        botonEntrada.dispatchEvent(event);
                    });

                    // Esperar 2s para validar cambio de estado
                    setTimeout(() => {
                        const nuevos = Array.from(content.shadowRoot.querySelectorAll('.btn-entry')).map(b => {
                            const texto = b.querySelector('.btn-text');
                            return texto ? texto.innerText.trim() : '';
                        });

                        if (!nuevos.includes('Marcar Entrada') && nuevos.includes('Marcar Salida')) {
                            callback('✅ Entrada marcada correctamente (botón cambió a "Marcar Salida")');
                        } else {
                            callback('⚠️ Click ejecutado pero no hubo cambio. Botones ahora: [' + nuevos.join(', ') + ']');
                        }
                    }, 2000);

                } catch (err) {
                    callback('❌ Error ejecutando JS: ' + err.message);
                }
            """;

            Object resultado = js.executeAsyncScript(script);
            System.out.println(resultado);

            // 🔔 Notificación por WhatsApp con CallMeBot
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

            Thread.sleep(10000);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            driver.quit();
        }
    }
}
