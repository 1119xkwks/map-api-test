/**
 * API fetch wrapper
 * 백엔드 API와 통신하기 위한 유틸리티 함수
 */

const API_BASE_URL =
  process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080";

/**
 * 백엔드에서 Kakao JS Key 등 프론트엔드 설정값을 가져온다.
 * @returns {Promise<{ kakaoJsKey: string }>}
 */
export async function fetchConfig(signal) {
  const res = await fetch(`${API_BASE_URL}/api/config`, { signal });
  if (!res.ok) {
    throw new Error(`설정 조회 실패: ${res.status} ${res.statusText}`);
  }
  const json = await res.json();
  if (!json.success) {
    throw new Error("설정 조회 응답이 실패 상태입니다.");
  }
  return json.data; // { kakaoJsKey: "..." }
}

/**
 * 지도 bounds 영역 내 아파트 실거래가 데이터를 조회한다.
 * @param {{ swLat: number, swLng: number, neLat: number, neLng: number }} params - 남서/북동 좌표
 * @param {AbortSignal} [signal] - 요청 취소용 AbortSignal
 * @returns {Promise<{ success: boolean, count: number, dealYmd: string, data: Array }>}
 */
export async function fetchApartments(params, signal) {
  const { swLat, swLng, neLat, neLng } = params;
  const url = `${API_BASE_URL}/api/apartments?sw_lat=${swLat}&sw_lng=${swLng}&ne_lat=${neLat}&ne_lng=${neLng}`;

  const res = await fetch(url, { signal });
  if (!res.ok) {
    const errorBody = await res.json().catch(() => null);
    const message =
      errorBody?.message || `API 호출 실패: ${res.status} ${res.statusText}`;
    throw new Error(message);
  }
  return res.json();
}
