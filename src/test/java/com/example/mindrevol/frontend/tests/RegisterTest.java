// src/test/java/com/example/mindrevol/frontend/tests/RegisterTest.java
package com.example.mindrevol.frontend.tests;

import com.example.mindrevol.frontend.base.BaseTest;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.UUID;

public class RegisterTest extends BaseTest {

    @Test(description = "Kiểm tra đăng ký thành công")
    public void testSuccessfulRegistration() {
        driver.findElement(By.xpath("//a[contains(text(),'Đăng ký')]")).click();
        wait.until(ExpectedConditions.urlContains("/register"));

        // Tạo email và sđt ngẫu nhiên để tránh trùng lặp
        String randomEmail = "testuser_" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        String randomPhone = "09" + String.valueOf(System.currentTimeMillis()).substring(5); // Sđt giả

        // Tìm elements (điều chỉnh selectors)
        WebElement fullnameInput = driver.findElement(By.xpath("//label[contains(text(),'Họ và tên')]/following-sibling::input")); // Ví dụ selector phức tạp hơn
        WebElement emailInput = driver.findElement(By.xpath("//input[@type='email']"));
        WebElement phoneInput = driver.findElement(By.xpath("//input[@type='tel']"));
        WebElement passwordInput = driver.findElement(By.xpath("//label[contains(text(),'Mật khẩu')]/following-sibling::input"));
        WebElement registerButton = driver.findElement(By.xpath("//button[contains(.,'Đăng ký')]"));


        fullnameInput.sendKeys("Test User Register");
        emailInput.sendKeys(randomEmail);
        phoneInput.sendKeys(randomPhone);
        passwordInput.sendKeys("password123");
        registerButton.click();

        // Chờ thông báo thành công xuất hiện
        WebElement successMessage = wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath("//h3[contains(text(),'Đăng ký thành công!')]")));

        // Kiểm tra
        Assert.assertTrue(successMessage.isDisplayed(), "Thông báo đăng ký thành công không xuất hiện.");
        WebElement emailConfirmation = driver.findElement(By.xpath("//p[contains(.,'" + randomEmail + "')]"));
        Assert.assertTrue(emailConfirmation.isDisplayed(), "Email đăng ký không hiển thị trong thông báo thành công.");
        WebElement backToLoginButton = driver.findElement(By.xpath("//a[contains(text(),'Về trang đăng nhập')]"));
        Assert.assertTrue(backToLoginButton.isDisplayed(), "Nút quay lại đăng nhập không hiển thị.");

        // Lưu ý: Test này không kiểm tra việc kích hoạt email.
    }

     @Test(description = "Kiểm tra đăng ký thất bại khi email đã tồn tại")
    public void testRegistrationEmailExists() {
        driver.findElement(By.xpath("//a[contains(text(),'Đăng ký')]")).click();
        wait.until(ExpectedConditions.urlContains("/register"));

        // Sử dụng một email đã tồn tại (cần tạo trước hoặc dùng email của test login)
        String existingEmail = "testuser@example.com";
        String randomPhone = "09" + String.valueOf(System.currentTimeMillis()).substring(5);

        WebElement fullnameInput = driver.findElement(By.xpath("//label[contains(text(),'Họ và tên')]/following-sibling::input"));
        WebElement emailInput = driver.findElement(By.xpath("//input[@type='email']"));
        WebElement phoneInput = driver.findElement(By.xpath("//input[@type='tel']"));
        WebElement passwordInput = driver.findElement(By.xpath("//label[contains(text(),'Mật khẩu')]/following-sibling::input"));
        WebElement registerButton = driver.findElement(By.xpath("//button[contains(.,'Đăng ký')]"));

        fullnameInput.sendKeys("Another User");
        emailInput.sendKeys(existingEmail);
        phoneInput.sendKeys(randomPhone);
        passwordInput.sendKeys("password123");
        registerButton.click();

         // Chờ thông báo lỗi xuất hiện
        WebElement errorMessage = wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath("//div[contains(@class, 'v-alert') and @type='error']")));

        // Kiểm tra
        Assert.assertTrue(driver.getCurrentUrl().endsWith("/register"), "Không ở lại trang đăng ký sau lỗi.");
        Assert.assertTrue(errorMessage.isDisplayed(), "Thông báo lỗi không xuất hiện.");
        // **Quan trọng:** Kiểm tra nội dung lỗi chính xác từ backend trả về
        Assert.assertTrue(errorMessage.getText().contains("Email đã được sử dụng"), "Nội dung thông báo lỗi không đúng.");
    }
    // Thêm các test case khác: thiếu trường, sai định dạng email, mật khẩu ngắn...
}