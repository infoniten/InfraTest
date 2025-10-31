-- Create trades table with simple schema
CREATE TABLE IF NOT EXISTS trades (
    id VARCHAR(50) PRIMARY KEY,
    trade_data JSONB NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Create indexes for performance
CREATE INDEX idx_trades_created_at ON trades(created_at DESC);
CREATE INDEX idx_trades_data_gin ON trades USING GIN (trade_data);

-- Create index for trade timestamp from JSON
CREATE INDEX idx_trades_timestamp ON trades((trade_data->>'timestamp'));

-- Create partitioned table for better performance (optional, for production)
-- Uncomment if you want to use partitioning
/*
CREATE TABLE trades_partitioned (
    id VARCHAR(50),
    trade_data JSONB NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id, created_at)
) PARTITION BY RANGE (created_at);

-- Create partitions for the next 30 days
DO $$
DECLARE
    start_date date := CURRENT_DATE;
    end_date date;
    partition_name text;
BEGIN
    FOR i IN 0..30 LOOP
        end_date := start_date + INTERVAL '1 day';
        partition_name := 'trades_' || to_char(start_date, 'YYYY_MM_DD');

        EXECUTE format('CREATE TABLE IF NOT EXISTS %I PARTITION OF trades_partitioned
                       FOR VALUES FROM (%L) TO (%L)',
                       partition_name, start_date, end_date);

        start_date := end_date;
    END LOOP;
END $$;
*/

-- Create statistics table for monitoring
CREATE TABLE IF NOT EXISTS trade_statistics (
    id SERIAL PRIMARY KEY,
    hour_timestamp TIMESTAMP WITH TIME ZONE NOT NULL,
    trade_count INTEGER DEFAULT 0,
    total_volume DECIMAL(20, 2) DEFAULT 0,
    avg_latency_ms DECIMAL(10, 3),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_trade_statistics_hour ON trade_statistics(hour_timestamp);

-- Create reconciliation table for consistency checks
CREATE TABLE IF NOT EXISTS reconciliation_log (
    id SERIAL PRIMARY KEY,
    check_timestamp TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    db_count INTEGER,
    cache_count INTEGER,
    discrepancy_count INTEGER,
    status VARCHAR(20),
    details JSONB
);

-- Function to get trade statistics
CREATE OR REPLACE FUNCTION get_trade_stats()
RETURNS TABLE (
    total_trades BIGINT,
    trades_last_hour BIGINT,
    trades_today BIGINT,
    avg_trade_size_kb DECIMAL
) AS $$
BEGIN
    RETURN QUERY
    SELECT
        COUNT(*) as total_trades,
        COUNT(*) FILTER (WHERE created_at > NOW() - INTERVAL '1 hour') as trades_last_hour,
        COUNT(*) FILTER (WHERE created_at > CURRENT_DATE) as trades_today,
        AVG(pg_column_size(trade_data))::DECIMAL / 1024 as avg_trade_size_kb
    FROM trades;
END;
$$ LANGUAGE plpgsql;

-- Function for batch insert with conflict handling
CREATE OR REPLACE FUNCTION batch_insert_trades(trades_data JSONB[])
RETURNS INTEGER AS $$
DECLARE
    inserted_count INTEGER := 0;
    trade_record JSONB;
BEGIN
    FOREACH trade_record IN ARRAY trades_data
    LOOP
        INSERT INTO trades (id, trade_data)
        VALUES (
            trade_record->>'tradeId',
            trade_record
        )
        ON CONFLICT (id) DO NOTHING;

        IF FOUND THEN
            inserted_count := inserted_count + 1;
        END IF;
    END LOOP;

    RETURN inserted_count;
END;
$$ LANGUAGE plpgsql;

-- Monitoring view for real-time statistics
CREATE OR REPLACE VIEW trade_monitor AS
SELECT
    COUNT(*) as total_trades,
    COUNT(*) FILTER (WHERE created_at > NOW() - INTERVAL '1 minute') as trades_per_minute,
    COUNT(*) FILTER (WHERE created_at > NOW() - INTERVAL '1 hour') as trades_last_hour,
    MAX(created_at) as last_trade_time,
    (EXTRACT(EPOCH FROM (NOW() - MAX(created_at))))::INTEGER as seconds_since_last_trade,
    AVG(pg_column_size(trade_data))::DECIMAL / 1024 as avg_trade_size_kb,
    pg_size_pretty(pg_total_relation_size('trades')) as table_size
FROM trades;

-- Grant permissions (adjust as needed)
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO postgres;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO postgres;
GRANT ALL PRIVILEGES ON ALL FUNCTIONS IN SCHEMA public TO postgres;