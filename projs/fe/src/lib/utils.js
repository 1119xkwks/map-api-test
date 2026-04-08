/**
 * 유틸리티 함수 모음
 */

/**
 * HTML 특수문자를 이스케이프하여 XSS를 방지한다.
 * @param {string} str - 이스케이프할 문자열
 * @returns {string} 이스케이프된 문자열
 */
export function escapeHtml(str) {
  if (!str) return "";
  return String(str)
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;")
    .replace(/'/g, "&#039;");
}

/**
 * 디바운스 함수
 * 연속 호출 시 마지막 호출 후 delay(ms)가 지나야 실행된다.
 * @param {Function} fn - 실행할 함수
 * @param {number} delay - 대기 시간 (ms)
 * @returns {Function} 디바운스가 적용된 함수
 */
export function debounce(fn, delay) {
  let timer = null;
  return function (...args) {
    if (timer) {
      clearTimeout(timer);
    }
    timer = setTimeout(() => {
      fn.apply(this, args);
      timer = null;
    }, delay);
  };
}

/**
 * 거래금액(만원 단위, 콤마 포함 문자열)을 한국식 억 단위로 변환한다.
 * 예: "180,000" -> "18억"
 *     "95,000"  -> "9.5억"
 *     "12,500"  -> "1.25억"
 *     "8,000"   -> "8,000만"
 * @param {string} amount - 거래금액 문자열 (만원 단위, 콤마 포함)
 * @returns {string} 변환된 가격 문자열
 */
export function formatPrice(amount) {
  if (!amount) return "";

  // 콤마 제거 후 숫자로 변환
  const num = parseInt(amount.replace(/,/g, ""), 10);
  if (isNaN(num)) return amount;

  // 1억 미만이면 만원 단위로 표시
  if (num < 10000) {
    return `${num.toLocaleString()}만`;
  }

  // 1억 이상이면 억 단위로 변환
  const eok = num / 10000;

  // 정수인 경우 소수점 없이 표시
  if (Number.isInteger(eok)) {
    return `${eok}억`;
  }

  // 소수점 이하 불필요한 0 제거
  // 예: 9.5000 -> "9.5", 1.2500 -> "1.25"
  const formatted = parseFloat(eok.toFixed(4));
  return `${formatted}억`;
}
