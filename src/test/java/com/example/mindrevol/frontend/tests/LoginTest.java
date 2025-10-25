// src/test/java/com/example/mindrevol/frontend/tests/LoginTest.java
package com.example.mindrevol.frontend.tests;

import com.example.mindrevol.frontend.base.BaseTest;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.testng.Assert;
import org.testng.annotations.Test;

public class LoginTest extends BaseTest {

    @Test(description = "Kiểm tra đăng nhập thành công với tài khoản hợp lệ")
    public void testSuccessfulLogin() {
        // Điều hướng đến trang login nếu chưa ở đó
        if (!driver.getCurrentUrl().endsWith("/login")) {
             driver.findElement(By.xpath("//a[contains(text(),'Đăng nhập')]")).click(); // Giả sử có nút/link này
             wait.until(ExpectedConditions.urlContains("/login"));
        }

        // Tìm các element (Sử dụng ID, name, CSS Selector hoặc XPath ổn định)
        // **Lưu ý:** Các selector này cần được kiểm tra và điều chỉnh cho phù hợp với HTML thực tế của bạn
        WebElement emailInput = driver.findElement(By.xpath("//input[@type='email']")); // Có thể cần selector cụ thể hơn
        WebElement passwordInput = driver.findElement(By.xpath("//input[@type='password']"));
        WebElement loginButton = driver.findElement(By.xpath("//button[contains(.,'Đăng nhập')]")); // Tìm button có chữ "Đăng nhập"

        // Điền thông tin và submit
        emailInput.sendKeys("minhbinh51107@gmail.com"); // Thay bằng email hợp lệ
        passwordInput.sendKeys("0matkhau"); // Thay bằng mật khẩu hợp lệ
        loginButton.click();

        // Chờ trang dashboard (hoặc trang home) load
        wait.until(ExpectedConditions.urlToBe(baseUrl + "/")); // Chờ chuyển hướng về trang chủ
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath("//h3[contains(text(),'Các kế hoạch của bạn')]"))); // Chờ element đặc trưng của trang home

        // Kiểm tra kết quả
        Assert.assertEquals(driver.getCurrentUrl(), baseUrl + "/", "URL sau khi đăng nhập không đúng.");
        WebElement welcomeMessage = driver.findElement(By.xpath("//h1[contains(text(),'Chào mừng')]"));
        Assert.assertTrue(welcomeMessage.isDisplayed(), "Không thấy thông báo chào mừng.");

        // (Tùy chọn) Kiểm tra xem tên user có hiển thị đúng không
        // WebElement userNameDisplay = driver.findElement(By.xpath("... selector cho tên user ..."));
        // Assert.assertTrue(userNameDisplay.getText().contains("Tên User"), "Tên người dùng không hiển thị đúng.");
    }

    @Test(description = "Kiểm tra đăng nhập thất bại với mật khẩu sai")
    public void testFailedLoginInvalidPassword() {
         if (!driver.getCurrentUrl().endsWith("/login")) {
             driver.findElement(By.xpath("//a[contains(text(),'Đăng nhập')]")).click();
             wait.until(ExpectedConditions.urlContains("/login"));
         }

        WebElement emailInput = driver.findElement(By.xpath("//input[@type='email']"));
        WebElement passwordInput = driver.findElement(By.xpath("//input[@type='password']"));
        WebElement loginButton = driver.findElement(By.xpath("//button[contains(.,'Đăng nhập')]"));

        emailInput.sendKeys("testuser@example.com"); // Email đúng
        passwordInput.sendKeys("wrongpassword"); // Mật khẩu sai
        loginButton.click();

        // Chờ thông báo lỗi xuất hiện (điều chỉnh selector nếu cần)
        WebElement errorMessage = wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath("//div[contains(@class, 'v-alert') and @type='error']")));

        // Kiểm tra kết quả
        Assert.assertTrue(driver.getCurrentUrl().endsWith("/login"), "URL không ở lại trang login sau khi lỗi.");
        Assert.assertTrue(errorMessage.isDisplayed(), "Thông báo lỗi không xuất hiện.");
        Assert.assertTrue(errorMessage.getText().contains("Email hoặc mật khẩu không chính xác"), "Nội dung thông báo lỗi không đúng.");
    }

     // Thêm các test case khác: email sai, bỏ trống trường...
}