package test.acceptance;



import static org.junit.Assert.assertEquals;

import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;

public class ExampleSeleniumTests {

    private static FirefoxDriver driver;
	
    @BeforeEach
    void setUp() throws Exception 
    {        
        FirefoxOptions capabilities = new FirefoxOptions();
    
        driver = new FirefoxDriver(capabilities);
        driver.get("http://localhost:8080/test");

        driver.findElement(By.id("username")).sendKeys("admin");
        driver.findElement(By.id("password")).sendKeys("admin");
        driver.findElement(By.xpath("//input[@value='Login']")).click();
    }

    @AfterEach
    void close() throws Exception {
        driver.quit();
    } 


    @Test
    @DisplayName("Displayed username is 'admin'")	
    public void usernameDisplay(){

        assertEquals(driver.findElement(By.xpath("//span[@class='copyTitle']/b")).getText(), "admin");	
        driver.quit();
    }

}		