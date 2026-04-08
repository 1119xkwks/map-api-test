const { Client } = require('pg');
const fs = require('fs');
const path = require('path');
const iconv = require('iconv-lite');
require('dotenv').config();

async function main() {
  const client = new Client({ connectionString: process.env.DATABASE_URL });
  await client.connect();
  console.log('DB 연결 성공');

  // 1. 법정동 코드 테이블 생성
  await client.query(`DROP TABLE IF EXISTS region_code`);
  await client.query(`DROP TABLE IF EXISTS dong_code`);
  await client.query(`
    CREATE TABLE dong_code (
      code VARCHAR(10) PRIMARY KEY,
      name VARCHAR(100) NOT NULL
    )
  `);
  console.log('dong_code 테이블 생성 완료');

  // 파일 읽기 (EUC-KR -> UTF-8)
  const buf = fs.readFileSync(path.join(__dirname, '..', '.claude', 'docs', 'dong_codes.txt'));
  const text = iconv.decode(buf, 'euc-kr');
  const lines = text.split('\n').slice(1); // 헤더 제거

  // "존재" 항목만 필터링
  const rows = [];
  for (const line of lines) {
    const parts = line.split('\t');
    if (parts.length >= 3 && parts[2].trim() === '존재') {
      rows.push({ code: parts[0].trim(), name: parts[1].trim() });
    }
  }
  console.log(`존재 항목: ${rows.length}건`);

  // 배치 INSERT (500건씩)
  const batchSize = 500;
  for (let i = 0; i < rows.length; i += batchSize) {
    const batch = rows.slice(i, i + batchSize);
    const values = [];
    const params = [];
    batch.forEach((row, idx) => {
      const offset = idx * 2;
      values.push(`($${offset + 1}, $${offset + 2})`);
      params.push(row.code, row.name);
    });
    await client.query(
      `INSERT INTO dong_code (code, name) VALUES ${values.join(', ')}`,
      params
    );
  }
  console.log('dong_code 데이터 입력 완료');

  // 2. 지역코드 테이블 생성 (CTAS - 앞5자리 그룹화, 나머지자리 오름차순 첫번째)
  await client.query(`
    CREATE TABLE region_code AS
    SELECT code, name FROM (
      SELECT
        SUBSTRING(code, 1, 5) AS code,
        name,
        ROW_NUMBER() OVER (
          PARTITION BY SUBSTRING(code, 1, 5)
          ORDER BY SUBSTRING(code, 6, 5) ASC
        ) AS rn
      FROM dong_code
    ) sub
    WHERE rn = 1
    ORDER BY code
  `);
  await client.query(`ALTER TABLE region_code ADD PRIMARY KEY (code)`);

  const countResult = await client.query(`SELECT COUNT(*) FROM region_code`);
  console.log(`region_code 테이블 생성 완료: ${countResult.rows[0].count}건`);

  await client.end();
  console.log('완료');
}

main().catch(err => {
  console.error(err);
  process.exit(1);
});
