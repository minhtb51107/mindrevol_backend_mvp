package com.example.demo.test;

import org.openqa.selenium.By;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.time.Duration;

public class Bai1_DuckDuckGo {

    WebDriver driver;

    @BeforeMethod
    public void setup() {
        // Khởi tạo Chrome Driver. Selenium Manager sẽ tự động tải driver.
        driver = new ChromeDriver();
        driver.manage().window().maximize();
    }

    @Test
    public void testDuckDuckGoSearch() throws InterruptedException {
        // 1. Truy cập https://duckduckgo.com
        driver.get("https://duckduckgo.com");

        // Sử dụng WebDriverWait để chờ cho ô tìm kiếm sẵn sàng
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
        WebElement searchBox = wait.until(ExpectedConditions.visibilityOfElementLocated(By.name("q")));

        // 2. Truyền nội dung "alibaba" vào ô tìm kiếm
        searchBox.sendKeys("alibaba");

        // 3. Nhấn Enter để tìm kiếm
        searchBox.sendKeys(Keys.ENTER);

        // (Tùy chọn) Dừng 2 giây để xem kết quả
        Thread.sleep(2000);
    }

    @AfterMethod
    public void teardown() {
        // Đóng trình duyệt sau khi test xong
        if (driver != null) {
            driver.quit();
        }
    }
}