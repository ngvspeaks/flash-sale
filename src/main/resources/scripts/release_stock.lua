-- Redis Lua Script for Atomic Expiration Release of Cart Reservations
-- KEYS[1]: Reserved ZSET (e.g., {sku:1001}:reserved)
-- KEYS[2]: Stock Key (e.g., {sku:1001}:stock)
-- ARGV[1]: Max Score (Current Timestamp in Milliseconds)
-- ARGV[2]: Batch Limit Size

local reservedKey = KEYS[1]
local stockKey = KEYS[2]
local maxScore = ARGV[1]
local limit = tonumber(ARGV[2])

local expiredItems = redis.call('ZRANGEBYSCORE', reservedKey, 0, maxScore, 'LIMIT', 0, limit)

local releasedQty = 0
for _, item in ipairs(expiredItems) do
    local colonIdx = string.find(item, ":")
    if colonIdx then
        local qty = tonumber(string.sub(item, colonIdx + 1))
        releasedQty = releasedQty + qty
    end
    redis.call('ZREM', reservedKey, item)
end

if releasedQty > 0 then
    redis.call('INCRBY', stockKey, releasedQty)
end

return releasedQty
