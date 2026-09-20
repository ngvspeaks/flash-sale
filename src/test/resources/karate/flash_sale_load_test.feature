Feature: Enterprise Flash-Sale Concurrency & Zero-Overselling Verification

  Background:
    * url baseUrl
    * def testSku = 'SKU_1001'
    * def initialStock = 3

  Scenario: Reset Inventory Stock to Baseline
    Given path 'admin/inventory/reset'
    And request { skuId: '#(testSku)', stock: '#(initialStock)' }
    When method POST
    Then status 200
    And match response == { skuId: 'SKU_1001', updatedStock: 3 }

  Scenario: Valid Cart Reservation Within Inventory Capacity
    Given path 'orders/reserve'
    And request { skuId: '#(testSku)', userId: 'user_alpha', quantity: 1 }
    When method POST
    Then status 200
    And match response.status == 'RESERVED'
    And match response.skuId == 'SKU_1001'
    And match response.reservationId == '#present'

  Scenario: Prevent User Per-Item Purchase Limit Violation
    # First reservation by user_beta (Limit = 2)
    Given path 'orders/reserve'
    And request { skuId: '#(testSku)', userId: 'user_beta', quantity: 2 }
    When method POST
    Then status 200

    # Exceeding per-user limit on subsequent attempt
    Given path 'orders/reserve'
    And request { skuId: '#(testSku)', userId: 'user_beta', quantity: 1 }
    When method POST
    Then status 400
    And match response.errorCode == 'LIMIT_EXCEEDED'

  Scenario: Strict Zero Overselling (409 Conflict) Under Capacity Exhaustion
    # Reset stock to exactly 1 item
    Given path 'admin/inventory/reset'
    And request { skuId: '#(testSku)', stock: 1 }
    When method POST
    Then status 200

    # Exhaust final item
    Given path 'orders/reserve'
    And request { skuId: '#(testSku)', userId: 'user_gamma', quantity: 1 }
    When method POST
    Then status 200

    # Next attempt MUST fail with 409 Conflict (0% Overselling Protection)
    Given path 'orders/reserve'
    And request { skuId: '#(testSku)', userId: 'user_delta', quantity: 1 }
    When method POST
    Then status 409
    And match response == { errorCode: 'OUT_OF_STOCK', message: 'Stock depleted for SKU: SKU_1001' }

  Scenario: Bot Anomaly Detection and Automatic Rate-Limiting Block
    Given path 'orders/reserve'
    And header User-Agent = 'Mozilla/5.0 (compatible; Python-urllib/3.8-BotNet)'
    And request { skuId: '#(testSku)', userId: 'bot_scraps_99', quantity: 1 }
    When method POST
    Then status 429
    And match response == { errorCode: 'BOT_QUARANTINED', message: 'User account flagged for automated velocity violation' }
