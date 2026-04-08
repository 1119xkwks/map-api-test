"use client";

import { useRef, useEffect, useState, useCallback } from "react";
import { useApartments } from "@/hooks/useApartments";
import { createMarkerManager } from "@/components/MarkerOverlay";
import ZoomMessage from "@/components/ZoomMessage";

/** 줌 레벨 임계값: 이 값 이하일 때만 API 호출 (level 4 = 100m 축척) */
const ZOOM_THRESHOLD = 4;

/**
 * 카카오맵 지도 컴포넌트
 * - 지도를 먼저 렌더링 (데이터 조회 없이)
 * - "실거래가 조회" 버튼 클릭 시 API 호출
 * - 줌 레벨에 따라 조회 가능 여부 결정
 */
export default function KakaoMap() {
  const mapContainerRef = useRef(null);
  const mapRef = useRef(null);
  const markerManagerRef = useRef(null);

  const [showZoomMessage, setShowZoomMessage] = useState(false);
  const [mapReady, setMapReady] = useState(false);

  const { apartments, isLoading, error, loadApartments, clearApartments } =
    useApartments();

  /**
   * 지도 초기화 (최초 1회) — 데이터 조회 없이 지도만 렌더링
   */
  useEffect(() => {
    if (!mapContainerRef.current || mapRef.current) return;

    const kakao = window.kakao;
    if (!kakao || !kakao.maps) return;

    // 서울 중심 좌표로 지도 생성
    const center = new kakao.maps.LatLng(37.5665, 126.978);
    const map = new kakao.maps.Map(mapContainerRef.current, {
      center,
      level: 3,
    });

    mapRef.current = map;
    markerManagerRef.current = createMarkerManager();
    setMapReady(true);
  }, []);

  /**
   * apartments 데이터가 변경되면 마커를 갱신한다.
   */
  useEffect(() => {
    const map = mapRef.current;
    const manager = markerManagerRef.current;
    if (!map || !manager) return;

    if (apartments && apartments.length > 0) {
      manager.displayMarkers(map, apartments);
    } else {
      manager.clearMarkers();
    }
  }, [apartments]);

  /**
   * "실거래가 조회" 버튼 클릭 핸들러
   */
  const handleSearchClick = useCallback(() => {
    const map = mapRef.current;
    if (!map) return;

    const level = map.getLevel();

    if (level > ZOOM_THRESHOLD) {
      // 줌 레벨이 부족하면 안내 메시지 표시 + 마커 제거
      setShowZoomMessage(true);
      clearApartments();
      if (markerManagerRef.current) {
        markerManagerRef.current.clearMarkers();
      }
      // 3초 후 메시지 자동 숨김
      setTimeout(() => setShowZoomMessage(false), 3000);
      return;
    }

    setShowZoomMessage(false);

    // bounds 추출 → API 호출
    const bounds = map.getBounds();
    const sw = bounds.getSouthWest();
    const ne = bounds.getNorthEast();

    loadApartments({
      swLat: sw.getLat(),
      swLng: sw.getLng(),
      neLat: ne.getLat(),
      neLng: ne.getLng(),
    });
  }, [loadApartments, clearApartments]);

  return (
    <div className="map-wrapper">
      {/* 지도 컨테이너 */}
      <div ref={mapContainerRef} className="map-container" />

      {/* 실거래가 조회 버튼 */}
      {mapReady && (
        <button
          className="search-btn"
          onClick={handleSearchClick}
          disabled={isLoading}
        >
          {isLoading ? "조회 중..." : "실거래가 조회"}
        </button>
      )}

      {/* 줌 안내 메시지 */}
      <ZoomMessage show={showZoomMessage} />

      {/* 로딩 스피너 */}
      {isLoading && (
        <div className="loading-overlay">
          <div className="loading-spinner" />
        </div>
      )}

      {/* 결과 카운트 */}
      {!isLoading && apartments && apartments.length > 0 && (
        <div className="result-count">
          {apartments.length}건 표시 중
        </div>
      )}

      {/* 에러 메시지 */}
      {error && (
        <div className="error-message" role="alert">
          {error}
        </div>
      )}
    </div>
  );
}
