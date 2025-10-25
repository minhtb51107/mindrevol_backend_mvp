// src/test/java/com/example/mindrevol/frontend/tests/PlanTest.java
package com.example.mindrevol.frontend.tests;

import com.example.mindrevol.frontend.base.BaseTest;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public class PlanTest extends BaseTest {

    // Đăng nhập trước mỗi test trong class này
    @BeforeMethod
    public void loginBeforeTest() {
        driver.get(baseUrl + "/login");
        driver.findElement(By.xpath("//input[@type='email']")).sendKeys("minhbinh51107@gmail.com"); // User hợp lệ
        driver.findElement(By.xpath("//input[@type='password']")).sendKeys("0matkhau"); // Password hợp lệ
        driver.findElement(By.xpath("//button[contains(.,'Đăng nhập')]")).click();
        wait.until(ExpectedConditions.urlToBe(baseUrl + "/"));
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath("//h3[contains(text(),'Các kế hoạch của bạn')]")));
    }

    @Test(description = "Kiểm tra tạo kế hoạch mới thành công")
    public void testCreatePlanSuccessfully() {
        // Nhấn nút tạo kế hoạch
        driver.findElement(By.xpath("//a[contains(.,'Tạo kế hoạch học ngay')]")).click();
        wait.until(ExpectedConditions.urlContains("/plans/create"));

        // Tìm elements trên form tạo plan (điều chỉnh selectors)
        WebElement titleInput = driver.findElement(By.xpath("//label[contains(text(),'Tên kế hoạch')]/following-sibling::input"));
        WebElement durationInput = driver.findElement(By.xpath("//label[contains(text(),'Thời lượng')]/following-sibling::input"));
        WebElement startDateInput = driver.findElement(By.xpath("//label[contains(text(),'Ngày bắt đầu')]/following-sibling::input"));
        WebElement createButton = driver.findElement(By.xpath("//button[contains(.,'Tạo kế hoạch')]"));

        // Điền thông tin
        String planTitle = "Selenium Test Plan " + System.currentTimeMillis();
        titleInput.sendKeys(planTitle);
        durationInput.sendKeys("5"); // Số ngày

        // Lấy ngày mai để đảm bảo hợp lệ
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        String tomorrowStr = tomorrow.format(DateTimeFormatter.ISO_LOCAL_DATE); // YYYY-MM-DD
        startDateInput.sendKeys(tomorrowStr);

        // (Tùy chọn) Thêm công việc nếu cần
        // driver.findElement(By.xpath("//button[contains(.,'Thêm công việc')]")).click();
        // driver.findElement(By.xpath("//label[contains(text(),'Công việc 1')]/following-sibling::input")).sendKeys("Task 1 from Selenium");

        createButton.click();

        // Chờ chuyển hướng đến trang chi tiết kế hoạch
        // URL sẽ chứa link ngẫu nhiên, nên chỉ kiểm tra phần đầu
        wait.until(ExpectedConditions.urlContains("/plan/"));
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath("//*[contains(text(),'" + planTitle + "')]"))); // Chờ title xuất hiện trên trang chi tiết

        // Kiểm tra title trên trang chi tiết
        WebElement detailTitle = driver.findElement(By.xpath("//div[contains(@class, 'v-card-title') and contains(.,'" + planTitle + "')]"));
        Assert.assertTrue(detailTitle.isDisplayed(), "Tên kế hoạch không hiển thị đúng trên trang chi tiết.");

        // (Tùy chọn) Kiểm tra các thông tin khác trên trang chi tiết
    }

    // Thêm test case cho việc xem chi tiết kế hoạch, tham gia kế hoạch, v.v.
}