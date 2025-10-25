package com.example.demo.test;

import org.openqa.selenium.By;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.edge.EdgeDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.time.Duration;

public class Bai2_MNS {

    WebDriver driver;

    @BeforeMethod
    public void setup() {
        // Khởi tạo Edge Driver. Selenium Manager sẽ tự động tải driver.
        driver = new EdgeDriver();
        driver.manage().window().maximize();
    }

    @Test
    public void testMNSSearch() throws InterruptedException {
        // 1. Truy cập https://www.mns.com
        driver.get("https://www.mns.com");

        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));

        // Xử lý Cookie Banner (vì nó che mất ô search)
        try {
            // Chờ nút "Accept all" xuất hiện và click
            WebElement acceptCookiesBtn = wait.until(ExpectedConditions.elementToBeClickable(By.id("accept-all")));
            acceptCookiesBtn.click();
        } catch (Exception e) {
            // Bỏ qua nếu không tìm thấy banner (có thể đã được chấp nhận trước đó)
            System.out.println("Không tìm thấy hoặc không thể click cookie banner.");
        }

        // Chờ cho ô tìm kiếm (ID: global-search) sẵn sàng
        WebElement searchBox = wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("global-search")));

        // 2. Truyền nội dung "Việt Nam" vào ô tìm kiếm
        searchBox.sendKeys("Việt Nam");

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