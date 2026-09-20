-- Redis Lua Script for Atomic Inventory Reservation & Zero-Overselling Defense
-- KEYS[1]: Stock Key (e.g., {sku:1001}:stock)
-- KEYS[2]: Cart Reservation ZSET (e.g., {sku:1001}:reserved)
-- KEYS[3]: User Purchase Count Key (e.g., {sku:1001}:user:99:count)
-- ARGV[1]: Requested Quantity
-- ARGV[2]: Max Per-User Quantity Limit
-- ARGV[3]: Cart TTL in Seconds
-- ARGV[4]: Current Epoch Milliseconds
-- ARGV[5]: Unique Reservation ID

local stockKey = KEYS[1]
local reservedKey = KEYS[2]
local userLimitKey = KEYS[3]

local reqQty = tonumber(ARGV[1])
local maxLimit = tonumber(ARGV[2])
local ttlSec = tonumber(ARGV[3])
local now = tonumber(ARGV[4])
local reservationId = ARGV[5]

-- 1. Validate User Purchase Limit
local userCount = tonumber(redis.call('GET', userLimitKey) or '0')
if (userCount + reqQty) > maxLimit then
    return -2 -- EXCEEDED_USER_LIMIT
end

-- 2. Validate Inventory Availability
local currentStock = tonumber(redis.call('GET', stockKey) or '0')
if currentStock < reqQty then
    return -1 -- OUT_OF_STOCK
end

-- 3. Atomic State Mutation
redis.call('DECRBY', stockKey, reqQty)
redis.call('INCRBY', userLimitKey, reqQty)
redis.call('EXPIRE', userLimitKey, 86400) -- Keep user limit count active for 24h

local expireTime = now + (ttlSec * 1000)
local reservationPayload = reservationId .. ":" .. reqQty
redis.call('ZADD', reservedKey, expireTime, reservationPayload)

return 1 -- SUCCESS
