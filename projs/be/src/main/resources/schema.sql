-- apt_trade_cache: 공공API 실거래가 캐시
CREATE TABLE IF NOT EXISTS apt_trade_cache (
    id SERIAL PRIMARY KEY,
    sgg_cd VARCHAR(5) NOT NULL,
    deal_ymd VARCHAR(6) NOT NULL,
    apt_nm VARCHAR(100) NOT NULL,
    deal_amount VARCHAR(20) NOT NULL,
    exclu_use_ar NUMERIC(10,2),
    floor INTEGER,
    build_year INTEGER,
    deal_year VARCHAR(4),
    deal_month VARCHAR(2),
    deal_day VARCHAR(2),
    umd_nm VARCHAR(40),
    jibun VARCHAR(20),
    road_nm VARCHAR(60),
    umd_cd VARCHAR(10),
    cdeal_type VARCHAR(1) DEFAULT '',
    dealing_gbn VARCHAR(10),
    lat NUMERIC(10,7),
    lng NUMERIC(10,7),
    fetched_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- geocode_cache: Kakao geocoding 결과 캐시
CREATE TABLE IF NOT EXISTS geocode_cache (
    id SERIAL PRIMARY KEY,
    address VARCHAR(200) NOT NULL UNIQUE,
    lat NUMERIC(10,7) NOT NULL,
    lng NUMERIC(10,7) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- 인덱스
CREATE INDEX IF NOT EXISTS idx_trade_sgg_ymd ON apt_trade_cache (sgg_cd, deal_ymd);
CREATE INDEX IF NOT EXISTS idx_trade_coords ON apt_trade_cache (lat, lng);
CREATE INDEX IF NOT EXISTS idx_trade_fetched ON apt_trade_cache (fetched_at);
