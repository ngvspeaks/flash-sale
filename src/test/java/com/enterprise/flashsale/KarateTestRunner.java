package com.enterprise.flashsale;

import com.intuit.karate.junit5.Karate;

public class KarateTestRunner {

    @Karate.Test
    Karate testFlashSale() {
        return Karate.run("classpath:karate/flash_sale_load_test.feature");
    }
}
