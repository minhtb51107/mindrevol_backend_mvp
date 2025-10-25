// src/test/java/com/example/mindrevol/frontend/base/BaseTest.java
package com.example.mindrevol.frontend.base;

import io.github.bonigarcia.wdm.WebDriverManager;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;

import java.time.Duration;

public class BaseTest {

    protected WebDriver driver;
    protected WebDriverWait wait;
    protected String baseUrl = "http://localhost:5173"; // Địa chỉ chạy frontend dev

    @BeforeMethod
    public void setUp() {
        WebDriverManager.chromedriver().setup(); // Tự động tải và cấu hình ChromeDriver
        ChromeOptions options = new ChromeOptions();
        // options.addArguments("--headless"); // Chạy ẩn danh (không mở cửa sổ trình duyệt) - bỏ comment nếu cần
        options.addArguments("--disable-gpu");
        options.addArguments("--window-size=1920,1080");
        driver = new ChromeDriver(options);
        driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(5)); // Chờ ngầm định
        wait = new WebDriverWait(driver, Duration.ofSeconds(10)); // Chờ tường minh
        driver.get(baseUrl);
    }

    @AfterMethod
    public void tearDown() {
        if (driver != null) {
            driver.quit(); // Đóng trình duyệt và driver process
        }
    }
}