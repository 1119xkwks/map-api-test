"use client";

import { formatPrice, escapeHtml } from "@/lib/utils";

/**
 * 마커 오버레이 관리 모듈
 * Kakao Maps CustomOverlay를 생성/삭제하고 클릭 시 상세 팝업을 표시한다.
 *
 * 같은 아파트(aptNm + lat + lng)에 여러 거래가 있으면 최신 1건만 마커로 표시한다.
 * 클릭 시 해당 아파트의 모든 거래 내역을 상세 팝업에 표시한다.
 */

/**
 * 거래 데이터를 아파트 기준으로 그룹핑한다.
 * 동일 아파트(aptNm + lat + lng) 건을 묶고, 최신 거래를 대표 데이터로 선택한다.
 * @param {Array} data - API에서 받은 거래 데이터 배열
 * @returns {Array<{ representative: object, trades: Array }>}
 */
function groupByApartment(data) {
  const groups = new Map();

  data.forEach((item) => {
    // 좌표가 없는 건은 스킵
    if (!item.lat || !item.lng) return;

    const key = `${item.aptNm}_${item.lat}_${item.lng}`;
    if (!groups.has(key)) {
      groups.set(key, []);
    }
    groups.get(key).push(item);
  });

  const result = [];
  groups.forEach((trades) => {
    // 거래일 기준 내림차순 정렬하여 최신 거래를 대표로 선택
    trades.sort((a, b) => (b.dealDate || "").localeCompare(a.dealDate || ""));
    result.push({
      representative: trades[0],
      trades,
    });
  });

  return result;
}

/**
 * 상세 팝업 HTML을 생성한다.
 * @param {Array} trades - 해당 아파트의 모든 거래 내역
 * @returns {string} HTML 문자열
 */
function createDetailHTML(trades) {
  const apt = trades[0];
  const tradesHTML = trades
    .map((t) => {
      return `
        <div class="detail-trade-item">
          <div class="detail-trade-row">
            <span class="detail-label">거래가</span>
            <span class="detail-value detail-price">${escapeHtml(formatPrice(t.dealAmount))}</span>
          </div>
          <div class="detail-trade-row">
            <span class="detail-label">전용면적</span>
            <span class="detail-value">${escapeHtml(String(t.excluUseAr))}m&sup2;</span>
          </div>
          <div class="detail-trade-row">
            <span class="detail-label">층</span>
            <span class="detail-value">${escapeHtml(String(t.floor))}층</span>
          </div>
          <div class="detail-trade-row">
            <span class="detail-label">거래일</span>
            <span class="detail-value">${escapeHtml(t.dealDate || "-")}</span>
          </div>
          <div class="detail-trade-row">
            <span class="detail-label">거래유형</span>
            <span class="detail-value">${escapeHtml(t.dealingGbn || "-")}</span>
          </div>
        </div>`;
    })
    .join("");

  return `
    <div class="detail-popup">
      <div class="detail-header">
        <span class="detail-apt-name">${escapeHtml(apt.aptNm)}</span>
        <button class="detail-close-btn" onclick="this.closest('.detail-popup').parentElement.remove()">&#10005;</button>
      </div>
      <div class="detail-info">
        <span class="detail-meta">${escapeHtml(apt.umdNm)} ${escapeHtml(apt.jibun || "")}</span>
        ${apt.roadNm ? `<span class="detail-meta">${escapeHtml(apt.roadNm)}</span>` : ""}
        <span class="detail-meta">건축 ${escapeHtml(String(apt.buildYear))}년</span>
      </div>
      <div class="detail-trades-list">
        ${tradesHTML}
      </div>
    </div>`;
}

/**
 * 마커 오버레이 매니저를 생성한다.
 * KakaoMap 컴포넌트에서 사용한다.
 * @returns {{ displayMarkers: Function, clearMarkers: Function }}
 */
export function createMarkerManager() {
  // 현재 표시 중인 오버레이 배열
  let overlays = [];
  // 현재 열려 있는 상세 팝업 오버레이
  let detailOverlay = null;

  /**
   * 모든 마커와 상세 팝업을 지도에서 제거한다.
   */
  function clearMarkers() {
    overlays.forEach((overlay) => overlay.setMap(null));
    overlays = [];
    if (detailOverlay) {
      detailOverlay.setMap(null);
      detailOverlay = null;
    }
  }

  /**
   * 상세 팝업을 닫는다.
   */
  function closeDetailPopup() {
    if (detailOverlay) {
      detailOverlay.setMap(null);
      detailOverlay = null;
    }
  }

  /**
   * 거래 데이터를 기반으로 마커 오버레이를 생성하여 지도에 표시한다.
   * @param {kakao.maps.Map} map - 카카오 지도 인스턴스
   * @param {Array} data - 아파트 거래 데이터 배열
   */
  function displayMarkers(map, data) {
    // 기존 마커 제거
    clearMarkers();

    if (!data || data.length === 0) return;

    // 아파트별 그룹핑
    const grouped = groupByApartment(data);

    grouped.forEach(({ representative, trades }) => {
      const position = new window.kakao.maps.LatLng(
        representative.lat,
        representative.lng
      );

      // 마커 말풍선 HTML
      const content = document.createElement("div");
      content.className = "marker-bubble";
      content.innerHTML = `
        <span class="marker-apt-name">${escapeHtml(representative.aptNm)}</span>
        <span class="marker-price">${escapeHtml(formatPrice(representative.dealAmount))}</span>
      `;

      // 클릭 이벤트: 상세 팝업 표시
      content.addEventListener("click", (e) => {
        e.stopPropagation();
        closeDetailPopup();

        const detailContent = document.createElement("div");
        detailContent.innerHTML = createDetailHTML(trades);

        detailOverlay = new window.kakao.maps.CustomOverlay({
          position,
          content: detailContent,
          yAnchor: 0,
          zIndex: 10,
        });
        detailOverlay.setMap(map);
      });

      const overlay = new window.kakao.maps.CustomOverlay({
        position,
        content,
        yAnchor: 1.3,
        zIndex: 5,
      });

      overlay.setMap(map);
      overlays.push(overlay);
    });
  }

  return { displayMarkers, clearMarkers };
}
